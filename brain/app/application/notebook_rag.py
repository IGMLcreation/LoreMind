"""Use case RAG des notebooks : indexer une source PDF et retrouver les passages
pertinents pour une question.

Chaîne d'indexation : PDF → extraction texte (+OCR) → découpage en extraits courts
→ embeddings → stockage vectoriel (fichier). À la requête : on embed la question
et on récupère les extraits les plus proches (cosinus) pour ancrer le chat.

Extraits PLUS COURTS que pour l'import (recopie) : ici on veut une granularité fine
pour que la recherche pointe un passage précis, pas un demi-chapitre.
"""
from __future__ import annotations

import logging

from app.application.chunking import chunk_text
from app.application.embeddings import EmbeddingProvider
from app.domain.ports import PdfTextExtractor
from app.infrastructure import vector_store

logger = logging.getLogger(__name__)

_RAG_CHUNK_TOKENS = 600
# Recouvrement entre extraits consécutifs (~13% de la cible) : une phrase-clé à
# cheval sur deux extraits reste retrouvable dans au moins l'un des deux.
_RAG_OVERLAP_TOKENS = 80
# Un extrait avec quasi aucun texte réel (en-tête/pied de page, fragment de numéro
# de page isolé « 249 250 ») ne sert à rien en RAG → on l'écarte. Seuil bas et
# conservateur : on ne coupe QUE les fragments quasi-vides, jamais une vraie phrase.
_MIN_LETTERS = 15


def _has_enough_text(piece: str) -> bool:
    return sum(c.isalpha() for c in piece) >= _MIN_LETTERS


class NotebookRagUseCase:
    def __init__(
        self,
        extractor: PdfTextExtractor,
        embedder: EmbeddingProvider,
        chunk_target_tokens: int = _RAG_CHUNK_TOKENS,
        min_score: float = 0.0,
    ) -> None:
        self._extractor = extractor
        self._embedder = embedder
        self._chunk_target_tokens = chunk_target_tokens
        # Cosinus minimal pour qu'un extrait soit injecté dans le prompt : sous ce
        # seuil, l'extrait n'a aucun rapport avec la question → bruit. 0 = désactivé.
        self._min_score = min_score

    async def index_source(self, source_id: str, pdf_bytes: bytes) -> dict:
        """Extrait, découpe PAR PAGE (pour garder le n° de page → citations), embed
        et stocke une source. Renvoie un récap."""
        doc = self._extractor.extract(pdf_bytes)
        chunks: list[str] = []
        pages: list[int] = []
        for page in doc.pages:
            for piece in chunk_text(
                page.text, self._chunk_target_tokens, overlap_tokens=_RAG_OVERLAP_TOKENS
            ):
                if not _has_enough_text(piece):
                    continue  # fragment quasi-vide (en-tête/pied/numéro) → ignoré
                chunks.append(piece)
                pages.append(page.index + 1)  # n° de page 1-based pour l'affichage
        logger.info(
            "Indexation notebook source=%s : %s page(s) (%s OCR), %s extrait(s).",
            source_id, doc.page_count, doc.ocr_page_count, len(chunks),
        )
        if not chunks:
            vector_store.save(source_id, [], [])
            return {"chunks": 0, "page_count": doc.page_count, "ocr_page_count": doc.ocr_page_count}
        vectors = await self._embedder.embed(chunks)
        count = vector_store.save(source_id, chunks, vectors, pages)
        return {
            "chunks": count,
            "page_count": doc.page_count,
            "ocr_page_count": doc.ocr_page_count,
        }

    async def retrieve(self, source_ids: list[str], query: str, top_k: int = 6) -> list[dict]:
        """Passages les plus pertinents (toutes sources) pour `query`.

        Recherche hybride (cosinus + bonus lexical sur les mots de la question) ;
        peut renvoyer moins de `top_k` passages si le seuil de pertinence écarte
        les extraits hors-sujet."""
        ids = [s for s in source_ids if vector_store.exists(s)]
        if not ids or not query.strip():
            return []
        query_vectors = await self._embedder.embed([query], kind="query")
        if not query_vectors:
            return []
        return vector_store.search(
            ids, query_vectors[0], top_k, query_text=query, min_score=self._min_score
        )
