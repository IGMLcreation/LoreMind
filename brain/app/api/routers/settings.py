"""Endpoints de paramétrage runtime (écran Paramètres de l'UI)."""
from typing import Annotated, Literal

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.core.config import Settings, get_settings
from app.core.settings_store import save_overrides

router = APIRouter()


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


@router.get("/settings", response_model=SettingsDTO)
def read_settings(settings: Annotated[Settings, Depends(get_settings)]) -> SettingsDTO:
    """Retourne la config courante (secrets masques)."""
    return _to_settings_dto(settings)


@router.put("/settings", response_model=SettingsDTO)
def update_settings(patch: SettingsUpdateDTO) -> SettingsDTO:
    """Applique un patch partiel aux settings et persiste les overrides.

    Toute requete HTTP suivante verra les nouvelles valeurs (pas de cache).
    """
    overrides = {k: v for k, v in patch.model_dump().items() if v is not None}
    if overrides:
        save_overrides(overrides)
    # Relit .env + overrides fusionnes pour confirmation.
    return _to_settings_dto(get_settings())
