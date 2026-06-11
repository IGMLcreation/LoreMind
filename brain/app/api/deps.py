"""Factories d'injection de dépendance — le point d'inversion de l'hexagone.

C'est ICI (et seulement ici) qu'on choisit QUEL adapter concret incarne chaque
port (LLM, embeddings, extracteur PDF), en fonction des Settings — modifiables
à chaud depuis l'écran Paramètres de l'UI. Les routers ne connaissent que les
ports et les use cases, jamais Ollama/Mistral/etc.
"""
import logging
from typing import Annotated

from fastapi import Depends, HTTPException

from app.application.adapt_campaign import AdaptCampaignUseCase
from app.application.chat import ChatUseCase
from app.application.embeddings import EmbeddingError
from app.application.generate_page import GeneratePageUseCase
from app.application.import_campaign import ImportCampaignUseCase
from app.application.import_rules import ImportRulesUseCase
from app.application.notebook_chat import NotebookChatUseCase
from app.application.notebook_deep import NotebookDeepUseCase
from app.application.notebook_rag import NotebookRagUseCase
from app.core.config import Settings, get_settings
from app.domain.ports import LLMProvider, LLMProviderError
from app.infrastructure.gemini_adapter import GeminiLLMProvider
from app.infrastructure.mistral_adapter import MistralLLMProvider
from app.infrastructure.mistral_embedding_adapter import MistralEmbeddingProvider
from app.infrastructure.ollama_adapter import OllamaLLMProvider
from app.infrastructure.ollama_embedding_adapter import OllamaEmbeddingProvider
from app.infrastructure.onemin_adapter import OneMinAiLLMProvider
from app.infrastructure.openrouter_adapter import OpenRouterLLMProvider
from app.infrastructure.pdf_extractor import PyMuPdfTextExtractor

logger = logging.getLogger(__name__)

# Extracteur PDF partagé : la détection OCR (version Tesseract) a un coût
# (subprocess) qu'on ne veut pas payer à chaque requête → singleton module.
_PDF_EXTRACTOR = PyMuPdfTextExtractor()


def _effective_import_chunk_tokens(settings: Settings) -> int:
    """Taille de morceau réellement utilisable pour l'import.

    Avec Ollama, le morceau (entrée) ET sa réécriture en sections (sortie ≈ même
    taille) doivent tenir ensemble dans `num_ctx` — sinon Ollama remplit la fenêtre
    avec le prompt et la génération s'arrête après quelques tokens (JSON coupé net,
    morceau perdu). Budget : entrée×~1.3 (les morceaux sont mesurés en tokens
    cl100k, plus compacts que les tokenizers locaux) + consignes + sortie×~1.4
    ≤ num_ctx  →  morceau ≤ (num_ctx − 800) / 2.7. On plafonne, avec un log pour
    rester transparent. Les providers cloud (gros contexte) ne sont pas plafonnés.
    """
    requested = settings.import_chunk_tokens
    if settings.llm_provider != "ollama":
        return requested
    cap = max(1000, int((settings.llm_num_ctx - 800) / 2.7))
    if requested > cap:
        logger.warning(
            "Taille de morceau d'import réduite de %s à %s tokens : avec num_ctx=%s, "
            "un morceau plus gros ne laisserait pas la place à la sortie du modèle "
            "(génération coupée). Augmentez num_ctx pour utiliser de plus gros morceaux.",
            requested, cap, settings.llm_num_ctx,
        )
        return cap
    return requested


def get_llm_provider(
    settings: Annotated[Settings, Depends(get_settings)],
) -> LLMProvider:
    """Factory d'adapter — point d'inversion de dépendance.

    C'est ici (et uniquement ici) qu'on choisit QUEL adapter concret
    incarne le port, en fonction du champ `llm_provider` des Settings
    (modifiable a chaud depuis l'ecran Parametres de l'UI).
    """
    try:
        if settings.llm_provider == "onemin":
            return OneMinAiLLMProvider(settings)
        if settings.llm_provider == "openrouter":
            return OpenRouterLLMProvider(settings)
        if settings.llm_provider == "mistral":
            return MistralLLMProvider(settings)
        if settings.llm_provider == "gemini":
            return GeminiLLMProvider(settings)
        return OllamaLLMProvider(settings)
    except LLMProviderError as exc:
        # Ex : cle 1min.ai manquante. On renvoie du 400 plutot que du 500
        # pour que le frontend puisse afficher un message actionnable.
        raise HTTPException(status_code=400, detail=str(exc)) from exc


def get_generate_page_use_case(
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
) -> GeneratePageUseCase:
    """Factory du use case — injecte le port LLMProvider sans connaître l'adapter."""
    return GeneratePageUseCase(llm=llm)


def get_chat_use_case(
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
) -> ChatUseCase:
    """Factory du use case chat.

    L'adapter OllamaLLMProvider satisfait les deux protocoles (LLMProvider
    et LLMChatProvider) par duck typing ; on lui passe la même instance.
    """
    return ChatUseCase(llm=llm)  # type: ignore[arg-type]


def get_import_rules_use_case(
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> ImportRulesUseCase:
    """Factory du use case d'import de règles PDF (extraction + structuration)."""
    return ImportRulesUseCase(
        llm=llm, extractor=_PDF_EXTRACTOR,
        chunk_target_tokens=_effective_import_chunk_tokens(settings))


def get_import_campaign_use_case(
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> ImportCampaignUseCase:
    """Factory du use case d'import de campagne PDF (extraction + arborescence)."""
    return ImportCampaignUseCase(
        llm=llm,
        extractor=_PDF_EXTRACTOR,
        chunk_target_tokens=_effective_import_chunk_tokens(settings),
        map_concurrency=settings.llm_map_concurrency,
    )


def get_adapt_campaign_use_case(
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> AdaptCampaignUseCase:
    """Factory du use case d'adaptation d'un PDF à une campagne (conseils streamés)."""
    # L'adapter satisfait aussi LLMChatProvider (stream_chat) par duck typing.
    # Budget d'entrée = taille de morceau configurée (qui passe déjà côté provider).
    return AdaptCampaignUseCase(  # type: ignore[arg-type]
        llm=llm, extractor=_PDF_EXTRACTOR, max_input_tokens=settings.import_chunk_tokens)


def get_embedding_provider(
    settings: Annotated[Settings, Depends(get_settings)],
):
    """Factory de l'adapter d'embeddings (RAG) selon `embedding_provider`."""
    try:
        if settings.embedding_provider == "mistral":
            return MistralEmbeddingProvider(settings)
        return OllamaEmbeddingProvider(settings)
    except EmbeddingError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


def get_notebook_rag_use_case(
    embedder: Annotated[object, Depends(get_embedding_provider)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> NotebookRagUseCase:
    return NotebookRagUseCase(
        extractor=_PDF_EXTRACTOR,
        embedder=embedder,  # type: ignore[arg-type]
        min_score=settings.rag_min_score,
    )


def get_notebook_chat_use_case(
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
    rag: Annotated[NotebookRagUseCase, Depends(get_notebook_rag_use_case)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> NotebookChatUseCase:
    return NotebookChatUseCase(
        rag=rag, llm=llm, rerank_enabled=settings.rag_rerank)  # type: ignore[arg-type]


def get_notebook_deep_use_case(
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
    embedder: Annotated[object, Depends(get_embedding_provider)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> NotebookDeepUseCase:
    return NotebookDeepUseCase(
        llm=llm,
        batch_tokens=settings.import_chunk_tokens,
        map_concurrency=settings.llm_map_concurrency,
        embedder=embedder,
        summary_filter=settings.deep_summary_filter,
    )
