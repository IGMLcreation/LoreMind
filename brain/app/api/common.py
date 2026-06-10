"""Utilitaires partagés des routers : encodage SSE + garde-fous d'upload PDF."""
from __future__ import annotations

import json

# Garde-fou taille : un livre de règles dépasse rarement quelques dizaines de Mo.
# Au-delà, on refuse (probable erreur d'upload) plutôt que d'OOM le conteneur.
MAX_PDF_BYTES = 60 * 1024 * 1024  # 60 Mo


def sse_event(event: str, data: dict) -> str:
    """Encode un évènement Server-Sent Events (accents préservés)."""
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


def pdf_upload_error(content: bytes) -> str | None:
    """Message d'erreur si l'upload PDF est invalide (vide / trop gros), sinon None.

    Utilisé par les flux SSE, où l'erreur doit partir en évènement `error`
    plutôt qu'en HTTPException (le flux est déjà ouvert en 200).
    """
    if not content:
        return "Fichier PDF vide."
    if len(content) > MAX_PDF_BYTES:
        return f"PDF trop volumineux (> {MAX_PDF_BYTES // (1024 * 1024)} Mo)."
    return None
