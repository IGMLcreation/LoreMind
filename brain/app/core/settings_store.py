"""Overrides runtime persistés sur disque pour les Settings.

Les Settings par defaut viennent de .env (12-factor). L'utilisateur peut
surcharger certains champs depuis l'UI (ex: modele Ollama choisi) — ces
overrides sont stockes dans un fichier JSON local, relus a chaque requete.

Thread-safe via un lock simple : suffisant pour un deploiement mono-process
(usage local). Si un jour on passe en multi-worker, migrer vers SQLite.
"""
from __future__ import annotations

import json
import threading
from pathlib import Path
from typing import Any

_LOCK = threading.Lock()
_OVERRIDES_PATH = Path("data/settings.json")

# Allow-list stricte des cles persistables via l'API. Toute autre cle est
# silencieusement ignoree — empeche un appelant de polluer settings.json
# avec des champs arbitraires (ex: `internal_shared_secret`) ou d'exposer
# un vecteur SSRF/credential-swap via un champ non-documente.
_ALLOWED_KEYS = frozenset({
    "llm_provider",
    "ollama_base_url",
    "llm_model",
    "llm_timeout_seconds",
    "llm_num_ctx",
    "onemin_api_key",
    "onemin_model",
})


def load_overrides() -> dict[str, Any]:
    """Retourne le dict d'overrides, ou {} si le fichier n'existe pas / est corrompu."""
    if not _OVERRIDES_PATH.exists():
        return {}
    try:
        raw = json.loads(_OVERRIDES_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    if not isinstance(raw, dict):
        return {}
    # Defense en profondeur au chargement : si settings.json contient des
    # cles hors allow-list (heritage d'un ancien binaire), on les ignore.
    return {k: v for k, v in raw.items() if k in _ALLOWED_KEYS}


def save_overrides(patch: dict[str, Any]) -> dict[str, Any]:
    """Fusionne `patch` (cles allow-listees uniquement) et persiste."""
    filtered = {k: v for k, v in patch.items() if k in _ALLOWED_KEYS}
    with _LOCK:
        current = load_overrides()
        current.update(filtered)
        _OVERRIDES_PATH.parent.mkdir(parents=True, exist_ok=True)
        _OVERRIDES_PATH.write_text(
            json.dumps(current, indent=2, ensure_ascii=False),
            encoding="utf-8",
        )
        return current
