"""Configuration applicative centralisée (principe 12-factor : config via env).

Équivalent Python du `application.properties` Spring Boot, avec validation
Pydantic : une variable manquante/invalide = crash au démarrage, pas une
NullPointerException surprise à la 3ème requête.

Depuis l'ecran Parametres (UI) : certains champs sont surchargeables a chaud
via `settings_store` (fichier JSON). A chaque Depends(get_settings), on relit
.env + overrides fusionnes. Pas de cache : le cout d'un read JSON local est
negligeable face a un appel LLM.
"""
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict

from app.core.settings_store import load_overrides


class Settings(BaseSettings):
    """Settings chargés depuis .env ou variables d'environnement."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # Provider LLM actif. "ollama" = local ; "onemin" = 1min.ai (etage 2).
    llm_provider: Literal["ollama", "onemin"] = "ollama"

    ollama_base_url: str = "http://localhost:11434"
    llm_model: str = "gemma4:26b"
    llm_timeout_seconds: int = 120

    # Fenêtre de contexte (num_ctx Ollama). Défaut Ollama = 2048, trop étroit
    # dès que le Structural Context du Lore dépasse ~10 pages (b9). On monte
    # à 16384 pour tenir ~100 pages enrichies. Coût VRAM : ~600 MB de KV cache
    # supplémentaire (vs 2048) pour le modèle gemma 2B. Surchargeable via
    # LLM_NUM_CTX dans .env si besoin (ex: VRAM limitée → 8192).
    llm_num_ctx: int = 16384

    # 1min.ai (etage 2) — la cle et le modele sont stockes via settings_store
    # (modifiables depuis l'UI). Les defauts ici sont juste des placeholders.
    onemin_api_key: str = ""
    onemin_model: str = "gpt-4o-mini"

    # Secret partage entre le Core Spring et le Brain. Le Brain n'accepte une
    # requete que si l'entete X-Internal-Secret correspond. Volontairement
    # non-surchargeable via settings_store (securite critique, .env-only).
    internal_shared_secret: str = ""


def get_settings() -> Settings:
    """Fabrique des Settings merges (.env -> overrides runtime).

    Relu a chaque requete HTTP (via Depends). Permet a l'UI de changer
    le modele / provider sans redemarrer le Brain.
    """
    return Settings(**load_overrides())
