"""Point d'entrée FastAPI du Brain LoreMind.

Controller volontairement FIN : il valide l'entrée (DTOs Pydantic), délègue
au domaine via injection de dépendance (ports + use cases), et transforme les
erreurs du domaine en réponses HTTP. Aucune connaissance d'Ollama ici.
"""
import asyncio
import json
import logging
from typing import Annotated, AsyncIterator, Literal

import hmac
import httpx
import tiktoken
from fastapi import Depends, FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, Field

import re

from app.application.adapt_campaign import AdaptCampaignUseCase
from app.application.chat import ChatUseCase
from app.application.generate_page import GeneratePageUseCase
from app.application.import_campaign import ImportCampaignUseCase
from app.application.import_rules import ImportRulesUseCase
from app.application.llm_json import load_json_object
from app.application.llm_retry import generate_with_retry
from app.application.notebook_rag import NotebookRagUseCase
from app.application.notebook_chat import NotebookChatUseCase
from app.application.notebook_deep import NotebookDeepUseCase
from app.application.embeddings import EmbeddingError
from app.infrastructure import vector_store
from app.infrastructure.ollama_embedding_adapter import OllamaEmbeddingProvider
from app.infrastructure.mistral_embedding_adapter import MistralEmbeddingProvider
from app.core.config import Settings, get_settings
from app.core.settings_store import save_overrides
from app.domain.models import (
    ArcSummary,
    CampaignStructuralContext,
    ChapterSummary,
    CharacterSummary,
    NpcSummary,
    ChatMessage,
    GameSystemContext,
    JournalEntrySummary,
    LoreStructuralContext,
    NarrativeEntityContext,
    PageContext,
    PageGenerationContext,
    PageSummary,
    QuestSummary,
    RoomBranchHint,
    RoomSummary,
    SceneBranchHint,
    SceneSummary,
    SessionContext,
)
from app.domain.ports import LLMProvider, LLMProviderError, PdfExtractionError
from app.infrastructure.ollama_adapter import OllamaLLMProvider
from app.infrastructure.onemin_adapter import OneMinAiLLMProvider
from app.infrastructure.openrouter_adapter import OpenRouterLLMProvider
from app.infrastructure.mistral_adapter import MistralLLMProvider
from app.infrastructure.gemini_adapter import GeminiLLMProvider
from app.infrastructure.pdf_extractor import PyMuPdfTextExtractor

app = FastAPI(
    title="LoreMind Brain",
    description="Backend IA pour la génération de contenu narratif.",
    version="0.11.2-beta",
)

logger = logging.getLogger(__name__)


# Encodeur tiktoken partagé — chargé une fois pour éviter le coût de lookup
# à chaque requête. On utilise cl100k_base (GPT-3.5/4) comme tokenizer
# universel approximatif : ±10% d'écart avec Llama/Gemma mais largement
# suffisant pour une jauge visuelle à l'utilisateur.
_TOKEN_ENCODER: tiktoken.Encoding | None = None


def _count_tokens(text: str | None) -> int:
    """Compte les tokens d'un texte via tiktoken. Null/empty → 0."""
    if not text:
        return 0
    global _TOKEN_ENCODER
    if _TOKEN_ENCODER is None:
        _TOKEN_ENCODER = tiktoken.get_encoding("cl100k_base")
    return len(_TOKEN_ENCODER.encode(text))


# Chemins exemptes d'auth inter-service : healthcheck docker + introspection
# FastAPI (docs uniquement utiles en dev ; en prod docker-compose, le Brain
# n'est pas expose en dehors du reseau interne donc pas un risque).
_PUBLIC_PATHS = frozenset({"/health", "/docs", "/redoc", "/openapi.json"})


@app.middleware("http")
async def require_internal_secret(request: Request, call_next):
    """Refuse toute requete qui ne presente pas le secret partage core<->brain.

    Fail-closed : si `INTERNAL_SHARED_SECRET` n'est pas configure cote Brain,
    TOUTES les requetes non-publiques sont rejetees. Force la configuration
    explicite en prod et empeche un deploiement par defaut non-authentifie.

    Comparaison en temps-constant via `hmac.compare_digest` pour eviter les
    attaques par timing side-channel sur la validation du secret.
    """
    if request.url.path in _PUBLIC_PATHS:
        return await call_next(request)

    expected = get_settings().internal_shared_secret
    provided = request.headers.get("x-internal-secret", "")
    if not expected or not hmac.compare_digest(expected, provided):
        return JSONResponse(
            {"detail": "Unauthorized: invalid or missing X-Internal-Secret"},
            status_code=401,
        )
    return await call_next(request)


# --- DTOs HTTP (frontière, c'est ici et seulement ici qu'on utilise Pydantic) ---


class GenerateRequest(BaseModel):
    prompt: str


class GenerateResponse(BaseModel):
    model: str
    response: str


class GeneratePageRequestDTO(BaseModel):
    """Contexte envoyé par le Core Java pour remplir une page via le LLM."""

    lore_name: str
    folder_name: str
    template_name: str
    template_fields: list[str] = Field(min_length=1)
    page_title: str
    lore_description: str | None = None


class GeneratePageResponseDTO(BaseModel):
    """Retour : une valeur textuelle par champ du template (clé = field name)."""

    values: dict[str, str]


class ChatMessageDTO(BaseModel):
    """Un message de la conversation. Rôles acceptés : user, assistant, system."""

    role: str = Field(pattern="^(user|assistant|system)$")
    content: str


class PageSummaryDTO(BaseModel):
    """Résumé enrichi d'une page : identité + contenu + interconnexions.

    Depuis b9 : values/tags/related_page_titles sont optionnels côté JSON —
    le Core Java ne les sérialise que s'ils sont non-vides (payload léger
    pour un Lore avec beaucoup de pages vierges).
    """

    title: str
    template_name: str
    values: dict[str, str] = Field(default_factory=dict)
    tags: list[str] = Field(default_factory=list)
    related_page_titles: list[str] = Field(default_factory=list)


class LoreContextDTO(BaseModel):
    """Carte structurelle du Lore avec contenu des pages (b9+)."""

    lore_name: str
    lore_description: str | None = None
    folders: dict[str, list[PageSummaryDTO]] = Field(default_factory=dict)
    tags: list[str] = Field(default_factory=list)


class PageContextDTO(BaseModel):
    """Contexte d'une page spécifique pour focaliser le chat (optionnel)."""

    title: str
    template_name: str
    template_fields: list[str] = Field(default_factory=list)
    values: dict[str, str] = Field(default_factory=dict)


class SceneBranchHintDTO(BaseModel):
    """Indice d'une branche narrative (le Core a deja resolu le nom cible)."""

    label: str
    target_scene_name: str
    condition: str | None = None


class RoomBranchHintDTO(BaseModel):
    """Sortie d'une pièce vers une autre pièce du même lieu (donjon)."""

    label: str
    target_room_name: str
    condition: str | None = None


class RoomSummaryDTO(BaseModel):
    """Pièce d'un lieu explorable. Omise par le Core si la scène est classique."""

    name: str
    floor: int | None = None
    description: str | None = None
    enemies: str | None = None
    branches: list[RoomBranchHintDTO] = Field(default_factory=list)


class SceneSummaryDTO(BaseModel):
    """Résumé d'une scène : nom + description courte (synopsis)."""

    name: str
    description: str | None = None
    # Optionnel : le Core Java ne serialise illustration_count QUE si > 0
    # (payload plus leger). Defaut 0 = pas d'illustrations ou champ absent.
    illustration_count: int = 0
    # Branches narratives sortantes, omises cote Core si vides.
    branches: list[SceneBranchHintDTO] = Field(default_factory=list)
    # Pièces du lieu explorable, omises par Core si scène classique.
    rooms: list[RoomSummaryDTO] = Field(default_factory=list)


class ChapterSummaryDTO(BaseModel):
    """Résumé d'un chapitre : nom + description courte + ses scènes."""

    name: str
    description: str | None = None
    scenes: list[SceneSummaryDTO] = Field(default_factory=list)
    illustration_count: int = 0


class ArcSummaryDTO(BaseModel):
    """Résumé d'un arc narratif : nom + description courte + ses chapitres."""

    name: str
    description: str | None = None
    chapters: list[ChapterSummaryDTO] = Field(default_factory=list)
    illustration_count: int = 0


class CharacterSummaryDTO(BaseModel):
    """Résumé d'un PJ : nom + snippet. Pas de fiche complète au niveau résumé."""

    name: str
    snippet: str = ""


class NpcSummaryDTO(BaseModel):
    """Résumé d'un PNJ : symétrique à CharacterSummaryDTO."""

    name: str
    snippet: str = ""


class CampaignContextDTO(BaseModel):
    """Carte narrative enrichie : arcs → chapitres → scènes avec synopsis."""

    campaign_name: str
    campaign_description: str | None = None
    arcs: list[ArcSummaryDTO] = Field(default_factory=list)
    characters: list[CharacterSummaryDTO] = Field(default_factory=list)
    npcs: list[NpcSummaryDTO] = Field(default_factory=list)


class NarrativeEntityDTO(BaseModel):
    """Entité narrative (arc/chapter/scene/character) en cours d'édition — focus optionnel."""

    entity_type: str = Field(pattern="^(arc|chapter|scene|character|npc)$")
    title: str
    fields: dict[str, str] = Field(default_factory=dict)


class GameSystemContextDTO(BaseModel):
    """Règles de JDR présélectionnées par le Core (filtrées par intent).

    Les sections sont un dict titre_H2 → contenu_markdown. Peuvent être
    vides si aucune section ne matchait l'intent de génération courant.
    """

    system_name: str
    system_description: str | None = None
    sections: dict[str, str] = Field(default_factory=dict)


class JournalEntrySummaryDTO(BaseModel):
    """Une entrée du journal de session.

    `source_session_name` est présent uniquement pour les évènements issus
    des sessions précédentes — sert à ancrer temporellement dans le prompt.
    """

    type: str
    content: str
    occurred_at: str | None = None
    source_session_name: str | None = None


class QuestSummaryDTO(BaseModel):
    """Résumé d'une quête (Chapter dans un Arc HUB). Voir QuestSummary côté domaine."""

    name: str
    arc_name: str
    description: str | None = None


class SessionContextDTO(BaseModel):
    """Contexte d'une Session de jeu en cours (Play Context).

    Combine le journal complet (`entries`), les EVENTs des sessions précédentes
    (`previous_events`), et — depuis l'ajout du mode Hub — l'état des quêtes
    Hub de la campagne (disponibles / en cours / verrouillées) plus les flags
    narratifs actuellement actifs.
    """

    session_name: str
    active: bool
    started_at: str | None = None
    entries: list[JournalEntrySummaryDTO] = Field(default_factory=list)
    previous_events: list[JournalEntrySummaryDTO] = Field(default_factory=list)
    available_quests: list[QuestSummaryDTO] = Field(default_factory=list)
    in_progress_quests: list[QuestSummaryDTO] = Field(default_factory=list)
    locked_quest_titles: list[str] = Field(default_factory=list)
    active_flags: list[str] = Field(default_factory=list)


class ChatStreamRequestDTO(BaseModel):
    """Requête de chat streamé : historique + contextes structurels.

    Les contextes (lore, page, campaign, narrative_entity, session) sont
    optionnels, mais au moins l'un des contextes "racines" (lore_context,
    campaign_context ou session_context) doit être fourni. Le validateur
    `check_scope` applique cette règle à la frontière HTTP.
    """

    messages: list[ChatMessageDTO] = Field(min_length=1)
    lore_context: LoreContextDTO | None = None
    page_context: PageContextDTO | None = None
    campaign_context: CampaignContextDTO | None = None
    narrative_entity: NarrativeEntityDTO | None = None
    game_system_context: GameSystemContextDTO | None = None
    session_context: SessionContextDTO | None = None

    def has_scope(self) -> bool:
        """Vrai si au moins un contexte racine (Lore, Campagne ou Session) est fourni."""
        return (
            self.lore_context is not None
            or self.campaign_context is not None
            or self.session_context is not None
        )


# --- Factories d'injection de dépendance ---


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


# Extracteur PDF partagé : la détection OCR (version Tesseract) a un coût
# (subprocess) qu'on ne veut pas payer à chaque requête → singleton module.
_PDF_EXTRACTOR = PyMuPdfTextExtractor()


def get_import_rules_use_case(
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> ImportRulesUseCase:
    """Factory du use case d'import de règles PDF (extraction + structuration)."""
    return ImportRulesUseCase(
        llm=llm, extractor=_PDF_EXTRACTOR, chunk_target_tokens=settings.import_chunk_tokens)


def get_import_campaign_use_case(
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> ImportCampaignUseCase:
    """Factory du use case d'import de campagne PDF (extraction + arborescence)."""
    return ImportCampaignUseCase(
        llm=llm, extractor=_PDF_EXTRACTOR, chunk_target_tokens=settings.import_chunk_tokens)


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
) -> NotebookRagUseCase:
    return NotebookRagUseCase(extractor=_PDF_EXTRACTOR, embedder=embedder)  # type: ignore[arg-type]


def get_notebook_chat_use_case(
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
    rag: Annotated[NotebookRagUseCase, Depends(get_notebook_rag_use_case)],
) -> NotebookChatUseCase:
    return NotebookChatUseCase(rag=rag, llm=llm)  # type: ignore[arg-type]


def get_notebook_deep_use_case(
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> NotebookDeepUseCase:
    return NotebookDeepUseCase(llm=llm, batch_tokens=settings.import_chunk_tokens)


# --- Endpoints ---


@app.get("/health")
def health() -> dict[str, str]:
    """Sonde de santé — permet au Core Java de vérifier que le Brain répond."""
    return {"status": "ok", "service": "brain"}


@app.on_event("startup")
async def _auto_install_embedding_model() -> None:
    """Au démarrage : si le provider d'embeddings est Ollama et que le modèle n'est
    pas installé, on le télécharge EN ARRIÈRE-PLAN → le RAG marche d'emblée pour un
    nouvel utilisateur, sans bloquer le démarrage du Brain. Best-effort (Ollama peut
    être absent / la connexion limitée) ; désactivable via `auto_pull_embedding_model`.
    """
    settings = get_settings()
    if not settings.auto_pull_embedding_model or settings.embedding_provider != "ollama":
        return
    asyncio.create_task(_ensure_ollama_embedding_model(settings.ollama_base_url, settings.ollama_embedding_model))


async def _ensure_ollama_embedding_model(base_url: str, model: str) -> None:
    # Attend qu'Ollama soit joignable (ordre de démarrage des conteneurs), puis
    # vérifie la présence du modèle avant de le tirer.
    for attempt in range(10):
        try:
            async with httpx.AsyncClient(timeout=10) as client:
                tags = await client.get(f"{base_url}/api/tags")
                tags.raise_for_status()
                names = [m.get("name", "") for m in tags.json().get("models", [])]
            if any(n == model or n.startswith(model + ":") for n in names):
                logger.info("Modèle d'embedding '%s' déjà présent.", model)
                return
            break  # Ollama joignable, modèle absent → on tire (ci-dessous)
        except httpx.HTTPError:
            await asyncio.sleep(min(5 * (attempt + 1), 30))
    else:
        logger.warning(
            "Ollama injoignable au démarrage — modèle d'embedding '%s' non auto-installé "
            "(il sera tirable manuellement : ollama pull %s).", model, model)
        return

    logger.info("Téléchargement automatique du modèle d'embedding '%s'…", model)
    try:
        async with httpx.AsyncClient(timeout=None) as client:
            async with client.stream("POST", f"{base_url}/api/pull", json={"name": model}) as resp:
                resp.raise_for_status()
                async for _line in resp.aiter_lines():
                    pass  # on draine la progression NDJSON jusqu'à la fin
        logger.info("Modèle d'embedding '%s' prêt.", model)
    except httpx.HTTPError as exc:
        logger.warning(
            "Auto-installation du modèle d'embedding '%s' échouée : %s "
            "(tirage manuel possible : ollama pull %s).", model, exc, model)


@app.post("/generate", response_model=GenerateResponse)
async def generate(
    body: GenerateRequest,
    settings: Annotated[Settings, Depends(get_settings)],
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
) -> GenerateResponse:
    """Endpoint libre : prompt → texte brut. Utile pour debug et exploration."""
    try:
        text = await llm.generate(body.prompt)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    return GenerateResponse(model=settings.llm_model, response=text)


@app.post("/generate-page", response_model=GeneratePageResponseDTO)
async def generate_page(
    body: GeneratePageRequestDTO,
    use_case: Annotated[
        GeneratePageUseCase, Depends(get_generate_page_use_case)
    ],
) -> GeneratePageResponseDTO:
    """Endpoint métier : contexte LoreMind → valeurs structurées par champ.

    Branche tout le use case `GeneratePageUseCase`. Ce controller ne fait
    que le mapping DTO ↔ dataclass et la traduction d'erreur domaine → HTTP.
    """
    context = PageGenerationContext(
        lore_name=body.lore_name,
        lore_description=body.lore_description,
        folder_name=body.folder_name,
        template_name=body.template_name,
        template_fields=body.template_fields,
        page_title=body.page_title,
    )

    try:
        result = await use_case.execute(context)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    return GeneratePageResponseDTO(values=result.values)


class RulesImportResponseDTO(BaseModel):
    """Proposition de sections de règles extraites d'un PDF.

    `sections` = {titre → contenu markdown}. C'est une PROPOSITION : le Core
    et l'UI laissent l'utilisateur réviser/éditer avant toute persistance.
    `ocr_page_count` permet d'indiquer si le PDF était un scan (OCR utilisé).
    """

    sections: dict[str, str]
    page_count: int
    ocr_page_count: int


# Garde-fou taille : un livre de règles dépasse rarement quelques dizaines de Mo.
# Au-delà, on refuse (probable erreur d'upload) plutôt que d'OOM le conteneur.
_MAX_PDF_BYTES = 60 * 1024 * 1024  # 60 Mo


@app.post("/import/rules", response_model=RulesImportResponseDTO)
async def import_rules(
    use_case: Annotated[ImportRulesUseCase, Depends(get_import_rules_use_case)],
    file: UploadFile = File(...),
) -> RulesImportResponseDTO:
    """Import d'un PDF de règles → sections markdown structurées (proposition).

    Extrait le texte (couche texte + repli OCR par page pour les scans), découpe,
    et demande au LLM de répartir les règles en sections thématiques. Ne persiste
    rien : renvoie la proposition au Core, qui la présente pour révision.
    """
    content = await file.read()
    if not content:
        raise HTTPException(status_code=422, detail="Fichier PDF vide.")
    if len(content) > _MAX_PDF_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"PDF trop volumineux (> {_MAX_PDF_BYTES // (1024 * 1024)} Mo).",
        )

    try:
        result = await use_case.execute(content)
    except PdfExtractionError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    return RulesImportResponseDTO(
        sections=result.sections,
        page_count=result.page_count,
        ocr_page_count=result.ocr_page_count,
    )


@app.post("/import/rules/stream")
async def import_rules_stream(
    use_case: Annotated[ImportRulesUseCase, Depends(get_import_rules_use_case)],
    file: UploadFile = File(...),
) -> StreamingResponse:
    """Import streamé : émet l'avancement (SSE) puis le résultat final.

    Évènements SSE :
      - `event: extracting`  → data: {}                    (extraction en cours)
      - `event: start`       → data: {page_count, ocr_page_count, total}
      - `event: progress`    → data: {current, total, new_sections:[...]}
      - `event: done`        → data: {sections, page_count, ocr_page_count}
      - `event: error`       → data: {message}
    """
    content = await file.read()

    def _sse(event: str, data: dict) -> str:
        return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"

    async def event_stream() -> AsyncIterator[str]:
        if not content:
            yield _sse("error", {"message": "Fichier PDF vide."})
            return
        if len(content) > _MAX_PDF_BYTES:
            yield _sse("error", {"message": f"PDF trop volumineux (> {_MAX_PDF_BYTES // (1024 * 1024)} Mo)."})
            return
        try:
            async for ev in use_case.stream(content):
                event_type = ev.pop("type")
                yield _sse(event_type, ev)
        except PdfExtractionError as exc:
            yield _sse("error", {"message": str(exc)})
        except LLMProviderError as exc:
            yield _sse("error", {"message": str(exc)})
        except Exception as exc:  # noqa: BLE001 — filet : une erreur inattendue ne doit
            # PAS casser le flux SSE brutalement (sinon le Core n'a qu'un message générique
            # sans détail). On la transforme en évènement `error` propre + log avec trace.
            logger.exception("Import règles : erreur inattendue dans le flux.")
            yield _sse("error", {"message": f"Erreur inattendue du Brain : {type(exc).__name__} : {exc}"})

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@app.post("/import/campaign/stream")
async def import_campaign_stream(
    use_case: Annotated[ImportCampaignUseCase, Depends(get_import_campaign_use_case)],
    file: UploadFile = File(...),
) -> StreamingResponse:
    """Import streamé d'un PDF de campagne → arbre arc→chapitre→scène (SSE).

    Évènements : `extracting`, `start` {page_count, ocr_page_count, total},
    `progress` {current, total, arc_count, chapter_count, scene_count},
    `done` {arcs:[...], page_count, ocr_page_count}, `error` {message}.
    """
    content = await file.read()

    def _sse(event: str, data: dict) -> str:
        return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"

    async def event_stream() -> AsyncIterator[str]:
        if not content:
            yield _sse("error", {"message": "Fichier PDF vide."})
            return
        if len(content) > _MAX_PDF_BYTES:
            yield _sse("error", {"message": f"PDF trop volumineux (> {_MAX_PDF_BYTES // (1024 * 1024)} Mo)."})
            return
        try:
            async for ev in use_case.stream(content):
                event_type = ev.pop("type")
                yield _sse(event_type, ev)
        except PdfExtractionError as exc:
            yield _sse("error", {"message": str(exc)})
        except LLMProviderError as exc:
            yield _sse("error", {"message": str(exc)})
        except Exception as exc:  # noqa: BLE001 — voir import règles : on ne laisse pas
            # une erreur inattendue casser le flux sans détail.
            logger.exception("Import campagne : erreur inattendue dans le flux.")
            yield _sse("error", {"message": f"Erreur inattendue du Brain : {type(exc).__name__} : {exc}"})

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@app.post("/adapt/campaign/stream")
async def adapt_campaign_stream(
    use_case: Annotated[AdaptCampaignUseCase, Depends(get_adapt_campaign_use_case)],
    file: UploadFile = File(...),
    brief: str = Form(""),
    messages: str = Form("[]"),
) -> StreamingResponse:
    """Adaptation CONVERSATIONNELLE d'un PDF à une campagne (SSE markdown).

    `brief` = description de la campagne (Core). `messages` = JSON de l'échange
    ([{role, content}, …]) ; vide au 1er tour. Évènements : `token`, `done`, `error`.
    """
    content = await file.read()

    try:
        raw_messages = json.loads(messages) if messages else []
    except json.JSONDecodeError:
        raw_messages = []
    convo = [
        ChatMessage(role=str(m.get("role", "user")), content=str(m.get("content", "")))
        for m in raw_messages
        if isinstance(m, dict) and str(m.get("content", "")).strip()
    ]

    def _sse(event: str, data: dict) -> str:
        return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"

    async def event_stream() -> AsyncIterator[str]:
        if not content:
            yield _sse("error", {"message": "Fichier PDF vide."})
            return
        if len(content) > _MAX_PDF_BYTES:
            yield _sse("error", {"message": f"PDF trop volumineux (> {_MAX_PDF_BYTES // (1024 * 1024)} Mo)."})
            return
        try:
            async for token in use_case.stream(content, brief, convo):
                yield _sse("token", {"token": token})
            yield _sse("done", {})
        except PdfExtractionError as exc:
            yield _sse("error", {"message": str(exc)})
        except LLMProviderError as exc:
            yield _sse("error", {"message": str(exc)})

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@app.post("/chat/stream")
async def chat_stream(
    body: ChatStreamRequestDTO,
    use_case: Annotated[ChatUseCase, Depends(get_chat_use_case)],
) -> StreamingResponse:
    """Chat streamé (Server-Sent Events) avec Structural Context.

    Accepte jusqu'à 4 contextes optionnels (Lore, Page focalisée, Campagne,
    entité narrative focalisée). Au moins un contexte racine (Lore ou
    Campagne) est requis pour que la requête ait du sens.

    Format de flux :
      - Chaque token : `data: {"token": "..."}\\n\\n`
      - Fin normale  : `event: done\\ndata: {}\\n\\n`
      - Erreur LLM   : `event: error\\ndata: {"message": "..."}\\n\\n`
    """
    if not body.has_scope():
        raise HTTPException(
            status_code=422,
            detail="Au moins un des deux contextes racines (lore_context ou campaign_context) est requis.",
        )

    messages = [ChatMessage(role=m.role, content=m.content) for m in body.messages]
    lore_context = _to_lore_context(body.lore_context)
    page_context = _to_page_context(body.page_context)
    campaign_context = _to_campaign_context(body.campaign_context)
    narrative_entity = _to_narrative_entity(body.narrative_entity)
    game_system_context = _to_game_system_context(body.game_system_context)
    session_context = _to_session_context(body.session_context)

    # --- Comptage tokens pour la jauge de contexte frontend ---
    # On construit le system prompt une fois ici pour le compter — le use case
    # le reconstruira à l'identique en interne (coût négligeable : concat de str).
    # Cette duplication évite de complexifier le contrat stream() avec un
    # paramètre optionnel system_prompt précalculé.
    system_prompt_preview = use_case.build_system_prompt(
        lore_context=lore_context,
        page_context=page_context,
        campaign_context=campaign_context,
        narrative_entity=narrative_entity,
        game_system_context=game_system_context,
        session_context=session_context,
    )
    # Dernier message = "current" (souvent user), le reste = historique accumulé.
    current_msg = messages[-1] if messages else None
    history_msgs = messages[:-1] if messages else []
    settings = get_settings()
    usage_payload = {
        "system": _count_tokens(system_prompt_preview),
        "history": sum(_count_tokens(m.content) for m in history_msgs),
        "current": _count_tokens(current_msg.content) if current_msg else 0,
        # Plafond connu seulement pour Ollama (num_ctx). Pour le cloud (1min/OpenRouter)
        # on ne connaît pas la fenêtre réelle → 0 = "pas de max" (jauge sans dénominateur).
        "max": settings.llm_num_ctx if settings.llm_provider == "ollama" else 0,
    }

    async def event_stream() -> AsyncIterator[str]:
        # Event 'usage' émis en tout premier : le frontend peut afficher la
        # jauge avant même le premier token de réponse.
        yield f"event: usage\ndata: {json.dumps(usage_payload, ensure_ascii=False)}\n\n"
        try:
            async for token in use_case.stream(
                messages,
                lore_context=lore_context,
                page_context=page_context,
                campaign_context=campaign_context,
                narrative_entity=narrative_entity,
                game_system_context=game_system_context,
                session_context=session_context,
            ):
                # json.dumps avec ensure_ascii=False pour préserver les accents
                yield f"data: {json.dumps({'token': token}, ensure_ascii=False)}\n\n"
            yield "event: done\ndata: {}\n\n"
        except LLMProviderError as exc:
            yield f"event: error\ndata: {json.dumps({'message': str(exc)})}\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


# --- Auto-titre d'une conversation persistee --------------------------------


class SummarizeTitleMessageDTO(BaseModel):
    role: Literal["user", "assistant", "system"]
    content: str


class SummarizeTitleRequestDTO(BaseModel):
    """Premiers messages d'une conversation pour auto-generer un titre court."""

    messages: list[SummarizeTitleMessageDTO] = Field(default_factory=list)


class SummarizeTitleResponseDTO(BaseModel):
    title: str


_TITLE_SYSTEM_PROMPT = (
    "Tu generes un titre court (4 a 7 mots max) qui resume le sujet de la "
    "conversation ci-dessous. Reponds UNIQUEMENT par le titre, sans guillemets, "
    "sans ponctuation finale, sans prefixe type 'Titre :'. Le titre doit etre "
    "en francais et capturer le sujet metier (pas 'Conversation IA')."
)


@app.post("/summarize/conversation-title", response_model=SummarizeTitleResponseDTO)
async def summarize_conversation_title(
    body: SummarizeTitleRequestDTO,
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
) -> SummarizeTitleResponseDTO:
    """Genere un titre court a partir des premiers echanges de la conversation.

    Appele par le core apres le 1er couple user/assistant, pour remplacer le
    titre provisoire "Nouvelle conversation" par quelque chose de parlant.
    """
    if not body.messages:
        raise HTTPException(status_code=422, detail="Au moins un message requis")

    transcript = "\n".join(f"{m.role.upper()}: {m.content}" for m in body.messages[:6])
    prompt = f"{_TITLE_SYSTEM_PROMPT}\n\nConversation :\n{transcript}\n\nTitre :"
    try:
        raw = await llm.generate(prompt)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    title = raw.strip().splitlines()[0].strip().strip('"').strip("'").rstrip(".")
    if len(title) > 80:
        title = title[:80].rstrip()
    if not title:
        title = "Nouvelle conversation"
    return SummarizeTitleResponseDTO(title=title)


# --- Tables aléatoires : génération IA + improvisation -----------------------

_DICE_FORMULA_RE = re.compile(r"^\s*(\d*)\s*[dD]\s*(\d+)\s*$")


def _dice_total_range(formula: str) -> tuple[int, int] | None:
    """(min, max) des totaux possibles d'une formule NdM, ou None si invalide."""
    match = _DICE_FORMULA_RE.match(formula or "")
    if not match:
        return None
    count = int(match.group(1)) if match.group(1) else 1
    faces = int(match.group(2))
    if count < 1 or count > 100 or faces < 2 or faces > 10000:
        return None
    return count, count * faces


class GenerateTableRequestDTO(BaseModel):
    description: str
    dice_formula: str = Field(default="1d20")
    # Contexte libre assemblé par le Core (nom de campagne, système, ambiance…).
    context: str = Field(default="")


class GeneratedTableEntryDTO(BaseModel):
    min_roll: int
    max_roll: int
    label: str
    detail: str = ""


class GenerateTableResponseDTO(BaseModel):
    name: str
    description: str = ""
    entries: list[GeneratedTableEntryDTO]


@app.post("/generate/random-table", response_model=GenerateTableResponseDTO)
async def generate_random_table(
    body: GenerateTableRequestDTO,
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
) -> GenerateTableResponseDTO:
    """Génère une table aléatoire (entrées par plage) couvrant la formule de dé."""
    rng = _dice_total_range(body.dice_formula)
    if rng is None:
        raise HTTPException(status_code=422, detail="Formule de dé invalide (ex. 1d20, 2d6, d100).")
    lo, hi = rng
    context_block = f"\nContexte de la campagne :\n{body.context.strip()}\n" if body.context.strip() else ""
    prompt = (
        "Tu es un assistant de jeu de rôle. Génère une TABLE ALÉATOIRE évocatrice.\n"
        f"Dé : {body.dice_formula} (résultats possibles de {lo} à {hi}).\n"
        f"Sujet : {body.description.strip()}\n"
        f"{context_block}\n"
        "Règles IMPÉRATIVES :\n"
        "- Réponds UNIQUEMENT par un objet JSON valide, sans texte autour.\n"
        '- Format : {"name": "...", "description": "...", "entries": '
        '[{"min_roll": N, "max_roll": M, "label": "résultat court", "detail": "1-2 phrases"}]}\n'
        f"- Les plages (min_roll..max_roll) doivent COUVRIR EXACTEMENT {lo}..{hi}, "
        "sans trou ni chevauchement, dans l'ordre croissant.\n"
        "- Des résultats variés, cohérents avec le sujet (et le contexte s'il est fourni).\n"
        "- En français. 'label' = résultat bref ; 'detail' = description/effet concret.\n"
        "Renvoie maintenant le JSON."
    )
    try:
        raw = await generate_with_retry(llm, prompt, output_format="json", temperature=0.7)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    parsed, _ = load_json_object(raw)
    if not isinstance(parsed, dict):
        raise HTTPException(status_code=502, detail="Le modèle n'a pas renvoyé de table exploitable.")

    entries: list[GeneratedTableEntryDTO] = []
    for e in parsed.get("entries", []) or []:
        if not isinstance(e, dict):
            continue
        try:
            mn = int(e["min_roll"])
            mx = int(e["max_roll"])
        except (KeyError, TypeError, ValueError):
            continue
        label = str(e.get("label") or "").strip()
        if not label:
            continue
        entries.append(GeneratedTableEntryDTO(
            min_roll=mn, max_roll=max(mn, mx), label=label[:200],
            detail=str(e.get("detail") or "").strip(),
        ))
    if not entries:
        raise HTTPException(status_code=502, detail="Aucune entrée générée — réessaie ou reformule.")

    name = str(parsed.get("name") or body.description).strip()[:120] or "Table générée"
    return GenerateTableResponseDTO(
        name=name,
        description=str(parsed.get("description") or "").strip(),
        entries=entries,
    )


class ImproviseRollRequestDTO(BaseModel):
    table_name: str
    result_label: str
    result_detail: str = Field(default="")
    context: str = Field(default="")


class ImproviseRollResponseDTO(BaseModel):
    narration: str


@app.post("/improvise/table-roll", response_model=ImproviseRollResponseDTO)
async def improvise_table_roll(
    body: ImproviseRollRequestDTO,
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
) -> ImproviseRollResponseDTO:
    """Brode un court récit (2-3 phrases) sur un résultat tiré, pour lancer la scène."""
    detail = f" ({body.result_detail.strip()})" if body.result_detail.strip() else ""
    context_block = f"\nContexte : {body.context.strip()}" if body.context.strip() else ""
    prompt = (
        "Tu es le Maître du Jeu. Les joueurs viennent de tirer sur la table "
        f"« {body.table_name.strip()} » et ont obtenu : « {body.result_label.strip()} »{detail}."
        f"{context_block}\n\n"
        "Décris en 2-3 phrases vivantes et immédiates ce qui se passe, pour lancer la scène. "
        "Pas de méta, pas d'options : juste la narration, en français."
    )
    try:
        raw = await llm.generate(prompt, temperature=0.8)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    return ImproviseRollResponseDTO(narration=raw.strip())


# --- Catalogues d'objets (boutiques) : génération IA -------------------------


class GenerateCatalogRequestDTO(BaseModel):
    description: str
    context: str = Field(default="")


class GeneratedCatalogItemDTO(BaseModel):
    name: str
    price: str = ""
    category: str = ""
    description: str = ""


class GenerateCatalogResponseDTO(BaseModel):
    name: str
    description: str = ""
    items: list[GeneratedCatalogItemDTO]


@app.post("/generate/item-catalog", response_model=GenerateCatalogResponseDTO)
async def generate_item_catalog(
    body: GenerateCatalogRequestDTO,
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
) -> GenerateCatalogResponseDTO:
    """Génère un catalogue d'objets (boutique, butin…) — nom, prix, catégorie, description."""
    context_block = f"\nContexte de la campagne :\n{body.context.strip()}\n" if body.context.strip() else ""
    prompt = (
        "Tu es un assistant de jeu de rôle. Génère un CATALOGUE D'OBJETS (boutique, butin, trésor…).\n"
        f"Sujet : {body.description.strip()}\n"
        f"{context_block}\n"
        "Règles IMPÉRATIVES :\n"
        "- Réponds UNIQUEMENT par un objet JSON valide, sans texte autour.\n"
        '- Format : {"name": "...", "description": "...", "items": '
        '[{"name": "Objet", "price": "ex. 50 po", "category": "ex. Armes", "description": "effet/détails"}]}\n'
        "- Des objets variés et cohérents avec le sujet (et le contexte s'il est fourni).\n"
        "- 'price' = prix court dans la monnaie du jeu ; 'category' = regroupement (Armes, Potions…) ; "
        "'description' = effet/détails en une phrase. En français.\n"
        "Renvoie maintenant le JSON."
    )
    try:
        raw = await generate_with_retry(llm, prompt, output_format="json", temperature=0.7)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    parsed, _ = load_json_object(raw)
    if not isinstance(parsed, dict):
        raise HTTPException(status_code=502, detail="Le modèle n'a pas renvoyé de catalogue exploitable.")

    items: list[GeneratedCatalogItemDTO] = []
    for it in parsed.get("items", []) or []:
        if not isinstance(it, dict):
            continue
        name = str(it.get("name") or "").strip()
        if not name:
            continue
        items.append(GeneratedCatalogItemDTO(
            name=name[:200],
            price=str(it.get("price") or "").strip(),
            category=str(it.get("category") or "").strip(),
            description=str(it.get("description") or "").strip(),
        ))
    if not items:
        raise HTTPException(status_code=502, detail="Aucun objet généré — réessaie ou reformule.")

    name = str(parsed.get("name") or body.description).strip()[:120] or "Catalogue généré"
    return GenerateCatalogResponseDTO(
        name=name,
        description=str(parsed.get("description") or "").strip(),
        items=items,
    )


# --- Notebooks (atelier RAG) : indexation des sources + chat ancré ----------


class IndexSourceResponseDTO(BaseModel):
    chunks: int
    page_count: int
    ocr_page_count: int


@app.post("/index/notebook-source", response_model=IndexSourceResponseDTO)
async def index_notebook_source(
    rag: Annotated[NotebookRagUseCase, Depends(get_notebook_rag_use_case)],
    source_id: str = Form(...),
    file: UploadFile = File(...),
) -> IndexSourceResponseDTO:
    """Indexe une source PDF (extraction + embeddings + stockage vectoriel)."""
    content = await file.read()
    if not content:
        raise HTTPException(status_code=422, detail="Fichier PDF vide.")
    if len(content) > _MAX_PDF_BYTES:
        raise HTTPException(
            status_code=413, detail=f"PDF trop volumineux (> {_MAX_PDF_BYTES // (1024 * 1024)} Mo).")
    try:
        recap = await rag.index_source(source_id, content)
    except PdfExtractionError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except EmbeddingError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    return IndexSourceResponseDTO(**recap)


@app.delete("/index/notebook-source/{source_id}")
def delete_notebook_source(source_id: str) -> dict[str, str]:
    """Supprime les vecteurs d'une source (au DELETE d'une source/notebook)."""
    vector_store.delete(source_id)
    return {"status": "deleted", "source_id": source_id}


class NotebookChatMessageDTO(BaseModel):
    role: str
    content: str


class NotebookChatRequestDTO(BaseModel):
    source_ids: list[str] = Field(default_factory=list)
    messages: list[NotebookChatMessageDTO] = Field(default_factory=list)
    context: str = Field(default="")


@app.post("/chat/notebook/stream")
async def chat_notebook_stream(
    body: NotebookChatRequestDTO,
    use_case: Annotated[NotebookChatUseCase, Depends(get_notebook_chat_use_case)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> StreamingResponse:
    """Chat ANCRÉ sur les sources (RAG) : récupère les passages pertinents puis
    streame la réponse. Évènements SSE : `token` {token}, `done` {}, `error` {message}."""
    messages = [ChatMessage(role=m.role, content=m.content) for m in body.messages]
    top_k = max(1, min(settings.rag_top_k, 200))

    def _sse(event: str, data: dict) -> str:
        return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"

    async def event_stream() -> AsyncIterator[str]:
        try:
            async for token in use_case.stream(body.source_ids, messages, context=body.context, top_k=top_k):
                if token:
                    yield _sse("token", {"token": token})
            yield _sse("done", {})
        except (LLMProviderError, EmbeddingError) as exc:
            yield _sse("error", {"message": str(exc)})
        except Exception as exc:  # noqa: BLE001 — filet : pas de coupure brutale du flux.
            logger.exception("Chat notebook : erreur inattendue.")
            yield _sse("error", {"message": f"Erreur inattendue du Brain : {type(exc).__name__} : {exc}"})

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@app.post("/chat/notebook/deep/stream")
async def chat_notebook_deep_stream(
    body: NotebookChatRequestDTO,
    use_case: Annotated[NotebookDeepUseCase, Depends(get_notebook_deep_use_case)],
) -> StreamingResponse:
    """Analyse APPROFONDIE (map-reduce sur tout le document). Évènements SSE :
    `progress` {current,total} pendant la lecture, puis `token` {token}, puis `done`."""
    messages = [ChatMessage(role=m.role, content=m.content) for m in body.messages]
    question = next((m.content for m in reversed(messages) if m.role == "user"), "")

    def _sse(event: str, data: dict) -> str:
        return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"

    async def event_stream() -> AsyncIterator[str]:
        if not question.strip():
            yield _sse("error", {"message": "Question vide."})
            return
        try:
            async for ev in use_case.stream(body.source_ids, messages, context=body.context):
                ev_type = ev.pop("type")
                yield _sse(ev_type, ev)
        except (LLMProviderError, EmbeddingError) as exc:
            yield _sse("error", {"message": str(exc)})
        except Exception as exc:  # noqa: BLE001 — filet : pas de coupure brutale.
            logger.exception("Analyse approfondie : erreur inattendue.")
            yield _sse("error", {"message": f"Erreur inattendue du Brain : {type(exc).__name__} : {exc}"})

    return StreamingResponse(event_stream(), media_type="text/event-stream")


# --- Mapping DTO → domaine (frontière HTTP) ---------------------------------


def _to_lore_context(dto: LoreContextDTO | None) -> LoreStructuralContext | None:
    if dto is None:
        return None
    return LoreStructuralContext(
        lore_name=dto.lore_name,
        lore_description=dto.lore_description,
        folders={
            folder: [_to_page_summary(p) for p in pages]
            for folder, pages in dto.folders.items()
        },
        tags=dto.tags,
    )


def _to_page_summary(dto: PageSummaryDTO) -> PageSummary:
    return PageSummary(
        title=dto.title,
        template_name=dto.template_name,
        values=dict(dto.values),
        tags=list(dto.tags),
        related_page_titles=list(dto.related_page_titles),
    )


def _to_page_context(dto: PageContextDTO | None) -> PageContext | None:
    if dto is None:
        return None
    return PageContext(
        title=dto.title,
        template_name=dto.template_name,
        template_fields=dto.template_fields,
        values=dto.values,
    )


def _to_campaign_context(dto: CampaignContextDTO | None) -> CampaignStructuralContext | None:
    if dto is None:
        return None
    arcs = [
        ArcSummary(
            name=arc.name,
            description=arc.description,
            illustration_count=arc.illustration_count,
            chapters=[
                ChapterSummary(
                    name=ch.name,
                    description=ch.description,
                    illustration_count=ch.illustration_count,
                    scenes=[
                        SceneSummary(
                            name=sc.name,
                            description=sc.description,
                            illustration_count=sc.illustration_count,
                            branches=[
                                SceneBranchHint(
                                    label=br.label,
                                    target_scene_name=br.target_scene_name,
                                    condition=br.condition,
                                )
                                for br in sc.branches
                            ],
                            rooms=[
                                RoomSummary(
                                    name=room.name,
                                    floor=room.floor,
                                    description=room.description,
                                    enemies=room.enemies,
                                    branches=[
                                        RoomBranchHint(
                                            label=rb.label,
                                            target_room_name=rb.target_room_name,
                                            condition=rb.condition,
                                        )
                                        for rb in room.branches
                                    ],
                                )
                                for room in sc.rooms
                            ],
                        )
                        for sc in ch.scenes
                    ],
                )
                for ch in arc.chapters
            ],
        )
        for arc in dto.arcs
    ]
    characters = [
        CharacterSummary(name=c.name, snippet=c.snippet)
        for c in dto.characters
    ]
    npcs = [
        NpcSummary(name=n.name, snippet=n.snippet)
        for n in dto.npcs
    ]
    return CampaignStructuralContext(
        campaign_name=dto.campaign_name,
        campaign_description=dto.campaign_description,
        arcs=arcs,
        characters=characters,
        npcs=npcs,
    )


# --- Settings (parametrage runtime depuis l'UI) ------------------------------


class SettingsDTO(BaseModel):
    """Vue serialisable des settings modifiables depuis l'UI.

    Expose uniquement les champs que l'utilisateur peut changer a chaud.
    Les secrets (onemin_api_key) sont masques en lecture.
    """

    llm_provider: Literal["ollama", "onemin", "openrouter", "mistral", "gemini"]
    ollama_base_url: str
    llm_model: str
    onemin_model: str
    # True si une cle 1min.ai est deja configuree — pas de leak de la cle elle-meme.
    onemin_api_key_set: bool
    openrouter_model: str
    # True si une cle OpenRouter est deja configuree (cle elle-meme jamais renvoyee).
    openrouter_api_key_set: bool
    mistral_model: str
    # True si une cle Mistral est deja configuree (cle elle-meme jamais renvoyee).
    mistral_api_key_set: bool
    gemini_model: str
    # True si une cle Gemini est deja configuree (cle elle-meme jamais renvoyee).
    gemini_api_key_set: bool
    # Embeddings (RAG des ateliers) : provider + modeles + auto-pull Ollama.
    embedding_provider: Literal["ollama", "mistral"]
    ollama_embedding_model: str
    mistral_embedding_model: str
    auto_pull_embedding_model: bool
    rag_top_k: int
    # Fenetre de contexte effective passee au modele (num_ctx Ollama) — sert
    # aussi de plafond a la jauge de contexte UI.
    llm_num_ctx: int
    # Taille cible d'un morceau (tokens) pour l'import de PDF (regles/campagne).
    import_chunk_tokens: int
    # Timeout HTTP des appels LLM (s). A monter si les imports lourds expirent.
    llm_timeout_seconds: int


class SettingsUpdateDTO(BaseModel):
    """Patch partiel des settings. Tous les champs sont optionnels."""

    llm_provider: Literal["ollama", "onemin", "openrouter", "mistral", "gemini"] | None = None
    ollama_base_url: str | None = None
    llm_model: str | None = None
    onemin_model: str | None = None
    # Chaine vide => on efface la cle. None => pas de changement.
    onemin_api_key: str | None = None
    openrouter_model: str | None = None
    openrouter_api_key: str | None = None
    mistral_model: str | None = None
    mistral_api_key: str | None = None
    gemini_model: str | None = None
    gemini_api_key: str | None = None
    embedding_provider: Literal["ollama", "mistral"] | None = None
    ollama_embedding_model: str | None = None
    mistral_embedding_model: str | None = None
    auto_pull_embedding_model: bool | None = None
    rag_top_k: int | None = None
    llm_num_ctx: int | None = None
    import_chunk_tokens: int | None = None
    llm_timeout_seconds: int | None = None


def _to_settings_dto(s: Settings) -> SettingsDTO:
    return SettingsDTO(
        llm_provider=s.llm_provider,
        ollama_base_url=s.ollama_base_url,
        llm_model=s.llm_model,
        onemin_model=s.onemin_model,
        onemin_api_key_set=bool(s.onemin_api_key),
        openrouter_model=s.openrouter_model,
        openrouter_api_key_set=bool(s.openrouter_api_key),
        mistral_model=s.mistral_model,
        mistral_api_key_set=bool(s.mistral_api_key),
        gemini_model=s.gemini_model,
        gemini_api_key_set=bool(s.gemini_api_key),
        embedding_provider=s.embedding_provider,
        ollama_embedding_model=s.ollama_embedding_model,
        mistral_embedding_model=s.mistral_embedding_model,
        auto_pull_embedding_model=s.auto_pull_embedding_model,
        rag_top_k=s.rag_top_k,
        llm_num_ctx=s.llm_num_ctx,
        import_chunk_tokens=s.import_chunk_tokens,
        llm_timeout_seconds=s.llm_timeout_seconds,
    )


@app.get("/settings", response_model=SettingsDTO)
def read_settings(settings: Annotated[Settings, Depends(get_settings)]) -> SettingsDTO:
    """Retourne la config courante (secrets masques)."""
    return _to_settings_dto(settings)


@app.put("/settings", response_model=SettingsDTO)
def update_settings(patch: SettingsUpdateDTO) -> SettingsDTO:
    """Applique un patch partiel aux settings et persiste les overrides.

    Toute requete HTTP suivante verra les nouvelles valeurs (pas de cache).
    """
    overrides = {k: v for k, v in patch.model_dump().items() if v is not None}
    if overrides:
        save_overrides(overrides)
    # Relit .env + overrides fusionnes pour confirmation.
    return _to_settings_dto(get_settings())


@app.get("/models/ollama")
async def list_ollama_models(
    settings: Annotated[Settings, Depends(get_settings)],
) -> dict[str, list[str]]:
    """Liste les modeles disponibles sur le serveur Ollama configure.

    Retourne une liste vide si Ollama est injoignable — l'UI affichera un
    message plutot qu'une 500.
    """
    url = f"{settings.ollama_base_url}/api/tags"
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            response = await client.get(url)
            response.raise_for_status()
            data = response.json()
    except httpx.HTTPError:
        return {"models": []}
    models = [m.get("name", "") for m in data.get("models", []) if m.get("name")]
    return {"models": sorted(models)}


class OllamaModelInfoDTO(BaseModel):
    """Info utile extraite de /api/show pour un modele Ollama donne.

    `context_length` = fenetre de contexte max supportee par le modele
    (extraite des metadonnees GGUF). 0 si inconnue. Le frontend s'en sert
    pour borner le slider de num_ctx dans les Parametres.
    """

    context_length: int = 0


@app.post("/models/ollama/info", response_model=OllamaModelInfoDTO)
async def get_ollama_model_info(
    body: dict[str, str],
    settings: Annotated[Settings, Depends(get_settings)],
) -> OllamaModelInfoDTO:
    """Retourne les metadonnees d'un modele Ollama via /api/show.

    On passe par POST (et pas GET /models/ollama/{name}) parce que les noms
    Ollama contiennent souvent un `:` (ex: `gemma3:e2b`) qui se segmente
    mal dans une URL — le body JSON evite le probleme d'escaping.

    Le champ qui nous interesse est `model_info["<arch>.context_length"]`
    (ex: `gemma3.context_length: 131072`). L'arch varie selon le modele, on
    scanne donc tous les champs finissant par `.context_length`.
    """
    name = (body.get("name") or "").strip()
    if not name:
        raise HTTPException(status_code=400, detail="name requis")
    url = f"{settings.ollama_base_url}/api/show"
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            response = await client.post(url, json={"model": name})
            response.raise_for_status()
            data = response.json()
    except httpx.HTTPError:
        return OllamaModelInfoDTO(context_length=0)
    model_info = data.get("model_info") or {}
    for key, value in model_info.items():
        if key.endswith(".context_length") and isinstance(value, int):
            return OllamaModelInfoDTO(context_length=value)
    return OllamaModelInfoDTO(context_length=0)


@app.post("/models/ollama/pull")
async def pull_ollama_model(
    body: dict[str, str],
    settings: Annotated[Settings, Depends(get_settings)],
) -> StreamingResponse:
    """Telecharge un modele depuis Ollama et streame la progression.

    Proxifie l'endpoint `/api/pull` d'Ollama qui renvoie du JSON ligne par
    ligne (NDJSON) avec le statut de chaque etape : manifest, layers,
    digest, success. On reemet ce flux tel quel au client (le front
    parsera les lignes et affichera une barre de progression).

    Le timeout est intentionnellement tres long (60 min) car certains
    modeles font 30+ Go.
    """
    name = (body.get("name") or "").strip()
    if not name:
        raise HTTPException(status_code=400, detail="name requis")
    url = f"{settings.ollama_base_url}/api/pull"

    async def stream() -> AsyncIterator[bytes]:
        # On utilise un timeout long pour la lecture (60 min) mais court pour
        # la connexion (10s) — si Ollama n'est pas joignable, on echoue vite.
        timeout = httpx.Timeout(connect=10, read=3600, write=10, pool=10)
        try:
            async with httpx.AsyncClient(timeout=timeout) as client:
                async with client.stream("POST", url, json={"model": name, "stream": True}) as r:
                    if r.status_code != 200:
                        # Ollama renvoie un message JSON d'erreur. On le passe
                        # tel quel au client en preservant le code HTTP.
                        body_text = await r.aread()
                        yield body_text
                        return
                    async for chunk in r.aiter_bytes():
                        yield chunk
        except httpx.HTTPError as e:
            # Erreur reseau : on emet une ligne JSON d'erreur compatible
            # avec le format NDJSON d'Ollama.
            err = json.dumps({"error": f"Connexion a Ollama impossible : {e}"}) + "\n"
            yield err.encode("utf-8")

    # application/x-ndjson : un objet JSON par ligne, pas de wrapping SSE.
    # C'est le format natif d'Ollama, le front le parsera ligne par ligne.
    return StreamingResponse(stream(), media_type="application/x-ndjson")


@app.delete("/models/ollama/{name:path}")
async def delete_ollama_model(
    name: str,
    settings: Annotated[Settings, Depends(get_settings)],
) -> dict[str, str]:
    """Supprime un modele du serveur Ollama.

    Le `:path` dans le pattern autorise les `:` du nom (ex: `gemma4:e4b`)
    sans avoir besoin de URL-encoder cote client.
    """
    if not name.strip():
        raise HTTPException(status_code=400, detail="name requis")
    url = f"{settings.ollama_base_url}/api/delete"
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            response = await client.request("DELETE", url, json={"model": name})
            if response.status_code == 404:
                raise HTTPException(status_code=404, detail=f"Modele '{name}' introuvable")
            response.raise_for_status()
    except httpx.HTTPError as e:
        raise HTTPException(status_code=502, detail=f"Ollama injoignable : {e}")
    return {"status": "deleted", "name": name}


@app.get("/models/openrouter")
async def list_openrouter_models() -> dict[str, list[dict[str, object]]]:
    """Catalogue DYNAMIQUE des modeles OpenRouter (API publique, sans cle).

    Renvoie {models: [{id, name, context_length, free}]}, trie gratuits d'abord
    puis contexte decroissant. `free` = id finissant par ':free' OU prix nul.
    """
    try:
        async with httpx.AsyncClient(timeout=20) as client:
            response = await client.get("https://openrouter.ai/api/v1/models")
            response.raise_for_status()
            data = response.json()
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"OpenRouter injoignable : {exc}")

    def _is_zero(value: object) -> bool:
        try:
            return float(value) == 0.0  # type: ignore[arg-type]
        except (TypeError, ValueError):
            return False

    models: list[dict[str, object]] = []
    for m in data.get("data", []) or []:
        mid = str(m.get("id") or "")
        if not mid:
            continue
        pricing = m.get("pricing") or {}
        is_free = mid.endswith(":free") or (
            _is_zero(pricing.get("prompt")) and _is_zero(pricing.get("completion"))
        )
        try:
            ctx = int(m.get("context_length") or 0)
        except (TypeError, ValueError):
            ctx = 0
        models.append({
            "id": mid,
            "name": str(m.get("name") or mid),
            "context_length": ctx,
            "free": is_free,
        })

    models.sort(key=lambda x: (not x["free"], -int(x["context_length"])))  # type: ignore[index]
    return {"models": models}


# Repli statique si la cle Mistral n'est pas (encore) configuree ou si l'API est
# injoignable — l'utilisateur peut quand meme choisir un modele. Liste curee
# (juin 2026) ; pour l'extraction de PDF, prefere `large` (fidele, 128k) ou `small`.
_MISTRAL_FALLBACK_MODELS = [
    "mistral-large-latest",
    "mistral-medium-latest",
    "mistral-small-latest",
    "open-mistral-nemo",
    "ministral-8b-latest",
    "ministral-3b-latest",
    "magistral-medium-latest",
    "magistral-small-latest",
    "pixtral-large-latest",
    "codestral-latest",
]


@app.get("/models/mistral")
async def list_mistral_models(
    settings: Annotated[Settings, Depends(get_settings)],
) -> dict[str, list[dict[str, object]]]:
    """Catalogue des modeles Mistral. Dynamique si une cle est configuree
    (GET /v1/models, qui requiert l'auth), sinon repli statique.

    Renvoie {models: [{id}]} (tous accessibles sur le tier gratuit Experiment)."""
    key = settings.mistral_api_key
    if not key:
        return {"models": [{"id": m} for m in _MISTRAL_FALLBACK_MODELS]}
    try:
        async with httpx.AsyncClient(timeout=20) as client:
            response = await client.get(
                "https://api.mistral.ai/v1/models",
                headers={"Authorization": f"Bearer {key}"},
            )
            response.raise_for_status()
            data = response.json()
    except httpx.HTTPError:
        # Cle invalide / API down : on ne casse pas l'UI, on propose le repli.
        return {"models": [{"id": m} for m in _MISTRAL_FALLBACK_MODELS]}

    ids = sorted({str(m.get("id")) for m in data.get("data", []) or [] if m.get("id")})
    if not ids:
        ids = _MISTRAL_FALLBACK_MODELS
    return {"models": [{"id": i} for i in ids]}


# Repli statique Gemini (juin 2026). Pour l'extraction, prefere un Flash a grand
# contexte ; `gemini-2.0-flash` a le quota gratuit le plus genereux.
_GEMINI_FALLBACK_MODELS = [
    "gemini-2.0-flash",
    "gemini-2.0-flash-lite",
    "gemini-2.5-flash",
    "gemini-2.5-flash-lite",
    "gemini-2.5-pro",
    "gemini-1.5-flash",
    "gemini-1.5-pro",
]


@app.get("/models/gemini")
async def list_gemini_models(
    settings: Annotated[Settings, Depends(get_settings)],
) -> dict[str, list[dict[str, object]]]:
    """Catalogue des modeles Gemini. Dynamique si une cle est configuree (endpoint
    OpenAI-compatible /openai/models), sinon repli statique. Renvoie {models:[{id}]}."""
    key = settings.gemini_api_key
    if not key:
        return {"models": [{"id": m} for m in _GEMINI_FALLBACK_MODELS]}
    try:
        async with httpx.AsyncClient(timeout=20) as client:
            response = await client.get(
                "https://generativelanguage.googleapis.com/v1beta/openai/models",
                headers={"Authorization": f"Bearer {key}"},
            )
            response.raise_for_status()
            data = response.json()
    except httpx.HTTPError:
        return {"models": [{"id": m} for m in _GEMINI_FALLBACK_MODELS]}

    # Les ids peuvent arriver prefixes "models/" → on nettoie pour que la valeur
    # selectionnee soit directement utilisable dans l'appel chat. On garde les
    # modeles "gemini-*" (hors embeddings/aqa) pour ne pas noyer la liste.
    ids: set[str] = set()
    for m in data.get("data", []) or []:
        mid = str(m.get("id") or "")
        if mid.startswith("models/"):
            mid = mid[len("models/"):]
        if mid.startswith("gemini-"):
            ids.add(mid)
    clean = sorted(ids) if ids else _GEMINI_FALLBACK_MODELS
    return {"models": [{"id": i} for i in clean]}


@app.get("/models/onemin")
def list_onemin_models() -> dict[str, list[dict[str, object]]]:
    """Catalogue statique des modeles 1min.ai, groupes par fournisseur.

    Liste construite par probing direct de l'endpoint chat-with-ai avec
    une vraie cle API (avril 2026) : chaque ID renvoie 200, les IDs
    absents renvoient 400 UNSUPPORTED_MODEL.

    Nota : les IDs Anthropic utilisent la nomenclature propre a 1min.ai
    (`claude-<family>-<version>`), pas la convention officielle Anthropic.
    """
    return {
        "groups": [
            {
                "provider": "Anthropic",
                "models": ["claude-opus-4-6", "claude-sonnet-4-6"],
            },
            {
                "provider": "OpenAI",
                "models": [
                    "gpt-5",
                    "gpt-5-mini",
                    "gpt-5-nano",
                    "gpt-4.1",
                    "gpt-4.1-mini",
                    "gpt-4.1-nano",
                    "gpt-4o",
                    "gpt-4o-mini",
                    "gpt-4-turbo",
                    "gpt-3.5-turbo",
                    "o3",
                    "o3-pro",
                    "o3-mini",
                    "o4-mini",
                ],
            },
            {
                "provider": "Google",
                "models": ["gemini-2.5-pro", "gemini-2.5-flash"],
            },
            {
                "provider": "Mistral",
                "models": [
                    "mistral-large-latest",
                    "mistral-medium-latest",
                    "mistral-small-latest",
                    "open-mistral-nemo",
                ],
            },
            {
                "provider": "DeepSeek",
                "models": ["deepseek-chat", "deepseek-reasoner"],
            },
            {
                "provider": "xAI",
                "models": ["grok-3", "grok-3-mini"],
            },
            {
                "provider": "Meta",
                "models": [
                    "meta/meta-llama-3.1-405b-instruct",
                    "meta/meta-llama-3-70b-instruct",
                ],
            },
            {
                "provider": "Alibaba",
                "models": ["qwen-plus", "qwen3-max"],
            },
            {
                "provider": "Perplexity",
                "models": ["sonar", "sonar-pro"],
            },
        ]
    }


def _to_narrative_entity(dto: NarrativeEntityDTO | None) -> NarrativeEntityContext | None:
    if dto is None:
        return None
    return NarrativeEntityContext(
        entity_type=dto.entity_type,
        title=dto.title,
        fields=dict(dto.fields),
    )


def _to_game_system_context(dto: GameSystemContextDTO | None) -> GameSystemContext | None:
    if dto is None:
        return None
    return GameSystemContext(
        system_name=dto.system_name,
        system_description=dto.system_description,
        sections=dict(dto.sections),
    )


def _to_session_context(dto: SessionContextDTO | None) -> SessionContext | None:
    if dto is None:
        return None
    return SessionContext(
        session_name=dto.session_name,
        active=dto.active,
        started_at=dto.started_at,
        entries=[_to_journal_entry(e) for e in dto.entries],
        previous_events=[_to_journal_entry(e) for e in dto.previous_events],
        available_quests=[_to_quest_summary(q) for q in dto.available_quests],
        in_progress_quests=[_to_quest_summary(q) for q in dto.in_progress_quests],
        locked_quest_titles=list(dto.locked_quest_titles),
        active_flags=list(dto.active_flags),
    )


def _to_quest_summary(dto: QuestSummaryDTO) -> QuestSummary:
    return QuestSummary(
        name=dto.name,
        arc_name=dto.arc_name,
        description=dto.description,
    )


def _to_journal_entry(dto: JournalEntrySummaryDTO) -> JournalEntrySummary:
    return JournalEntrySummary(
        type=dto.type,
        content=dto.content,
        occurred_at=dto.occurred_at,
        source_session_name=dto.source_session_name,
    )
