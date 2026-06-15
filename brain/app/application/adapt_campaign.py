"""Use case : conseils d'adaptation d'un PDF à une campagne EXISTANTE.

L'IA connaît la campagne de l'utilisateur (un « brief » : structure arcs/chapitres/
scènes + PNJ + univers/lore), lit le contenu du PDF, et rédige des recommandations
d'INTÉGRATION/ADAPTATION (où insérer, reskins de PNJ, transposition à l'univers,
doublons à réconcilier…). Sortie en markdown, streamée token par token.

Contrairement à l'IMPORT (qui produit une arborescence à créer), ici on produit
du CONSEIL libre : rien n'est créé, l'utilisateur applique à la main.
"""
from __future__ import annotations

import logging
from typing import AsyncIterator

from app.application.prompts import adapt_campaign as prompts
from app.core.language import DEFAULT as _DEFAULT_LANG
from app.domain.models import ChatMessage
from app.domain.ports import LLMChatProvider, PdfExtractionError, PdfTextExtractor

logger = logging.getLogger(__name__)

# Plus créatif que l'import (tâche de structuration) : ici on conseille/adapte.
_TEMPERATURE = 0.7


class AdaptCampaignUseCase:
    """Génère (en streaming) des conseils d'adaptation d'un PDF à une campagne."""

    def __init__(
        self,
        llm: LLMChatProvider,
        extractor: PdfTextExtractor,
        max_input_tokens: int = 10000,
    ) -> None:
        self._llm = llm
        self._extractor = extractor
        # L'adaptation envoie le PDF en UNE requête (pas de découpage). On plafonne
        # donc l'entrée pour ne pas dépasser la taille de requête acceptée par le
        # provider (sinon HTTP 400). Calé sur la taille des morceaux d'import.
        self._max_input_tokens = max_input_tokens

    async def stream(
        self,
        pdf_bytes: bytes,
        brief: str,
        messages: list[ChatMessage],
        language: str = _DEFAULT_LANG,
    ) -> AsyncIterator[str]:
        """Conversationnel : le PDF + la campagne sont le CONTEXTE (system prompt),
        `messages` est l'échange (demande initiale, puis feedbacks de l'utilisateur)."""
        doc = self._extractor.extract(pdf_bytes)
        pdf_text = doc.full_text
        if not pdf_text.strip():
            raise PdfExtractionError("Aucun texte exploitable n'a été extrait du PDF.")

        brief = brief or ""
        pdf_text, truncated = self._fit_pdf_to_budget(pdf_text, brief)

        logger.info(
            "Adaptation campagne : %s page(s) (%s via OCR), brief %s car., PDF %s car.%s, %s message(s).",
            doc.page_count, doc.ocr_page_count, len(brief), len(pdf_text),
            " (tronqué)" if truncated else "", len(messages),
        )

        trunc_note = (
            "\n[Note : PDF tronqué pour tenir dans une requête — base-toi sur ce début.]"
            if truncated else ""
        )
        # Concaténation (pas .format) : brief/PDF peuvent contenir des { } littéraux.
        system_prompt = (
            f"{prompts.SYSTEM_PREFIX}\n\n"
            "--- CAMPAGNE EXISTANTE DE L'UTILISATEUR ---\n"
            f"{brief.strip() or '(campagne encore vide)'}\n\n"
            "--- CONTENU DU PDF À ADAPTER ---\n"
            f"{pdf_text}{trunc_note}\n\n"
            f"{prompts.system_suffix(language)}\n\n"
            "Tu es en CONVERSATION : à chaque message de l'utilisateur, ajuste, corrige "
            "ou propose des alternatives en gardant tout ce contexte à l'esprit."
        )

        # 1er tour : si aucun message, on lance la demande initiale par défaut.
        convo = messages or [ChatMessage(
            role="user",
            content="Propose-moi comment intégrer et adapter ce PDF à ma campagne.",
        )]

        async for token in self._llm.stream_chat(
            convo, system_prompt=system_prompt, temperature=_TEMPERATURE
        ):
            yield token

    def _fit_pdf_to_budget(self, pdf_text: str, brief: str) -> tuple[str, bool]:
        """Tronque le texte du PDF pour que (brief + PDF) tienne dans le budget tokens.

        Évite un HTTP 400 « requête trop grosse » côté provider. Réserve une marge
        pour le prompt système et le brief.
        """
        import tiktoken

        enc = tiktoken.get_encoding("cl100k_base")
        brief_tokens = len(enc.encode(brief))
        budget = max(2000, self._max_input_tokens - brief_tokens - 1000)  # 1000 = marge système
        pdf_tokens = enc.encode(pdf_text)
        if len(pdf_tokens) <= budget:
            return pdf_text, False
        return enc.decode(pdf_tokens[:budget]), True
