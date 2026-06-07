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

    # Provider LLM actif. "ollama" = local ; "onemin" = 1min.ai ;
    # "openrouter" = OpenRouter ; "mistral" = Mistral ; "gemini" = Google Gemini.
    llm_provider: Literal["ollama", "onemin", "openrouter", "mistral", "gemini"] = "ollama"

    ollama_base_url: str = "http://localhost:11434"
    llm_model: str = "gemma4:26b"
    # Timeout HTTP des appels au LLM. Les imports/adaptations PDF génèrent de gros
    # blocs (surtout avec l'extraction riche) → 120s était trop court. Surchargeable
    # depuis l'UI (Paramètres) si un import lourd dépasse encore.
    llm_timeout_seconds: int = 300

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

    # OpenRouter (OpenAI-compatible). Cle + modele modifiables depuis l'UI.
    # Defaut = routeur `openrouter/free` : choisit un modele GRATUIT (0 credit).
    # Pour un modele precis gratuit : id finissant par `:free`.
    openrouter_api_key: str = ""
    openrouter_model: str = "openrouter/free"

    # Mistral (La Plateforme, OpenAI-compatible). Cle + modele modifiables depuis
    # l'UI. Tier gratuit « Experiment » sur console.mistral.ai (sans CB). Defaut =
    # mistral-large-latest (128k contexte, bon en francais et en JSON fidele).
    mistral_api_key: str = ""
    mistral_model: str = "mistral-large-latest"

    # Google Gemini (endpoint OpenAI-compatible). Cle gratuite sur
    # aistudio.google.com (sans CB). Defaut = gemini-2.0-flash : ~1M de contexte
    # (un livre tient en 1-2 appels), rapide, fidele, quota gratuit genereux.
    gemini_api_key: str = ""
    gemini_model: str = "gemini-2.0-flash"

    # Embeddings (RAG des notebooks/ateliers). Modele SEPARE du chat.
    # "ollama" = local (gratuit, illimite, ideal pour indexer un livre = bcp
    # d'appels) ; "mistral" = cloud EU (mistral-embed, soumis au rate limit).
    embedding_provider: Literal["ollama", "mistral"] = "ollama"
    ollama_embedding_model: str = "nomic-embed-text"
    mistral_embedding_model: str = "mistral-embed"
    # Au démarrage, si le provider d'embeddings est Ollama et que le modèle n'est
    # pas présent, le Brain le télécharge automatiquement (en arrière-plan) → le RAG
    # marche "out of the box" pour un nouvel utilisateur. Désactivable (connexion
    # limitée, gestion manuelle des modèles).
    auto_pull_embedding_model: bool = True

    # Nombre d'extraits récupérés par question dans le chat des ateliers (RAG).
    # Plus haut = plus de couverture pour les questions larges (« liste les… »),
    # mais prompt plus long. 8 par défaut (montable jusqu'à ~20 sur grand contexte).
    rag_top_k: int = 8

    # Taille cible d'un morceau (en tokens) pour l'import de PDF (regles/campagne).
    # Plus c'est gros, moins il y a de morceaux => moins de fragmentation et un
    # import plus rapide, MAIS il faut que ca tienne dans la fenetre du modele.
    # Defaut prudent (compatible Ollama num_ctx 16384). Sur un modele a grand
    # contexte (ex: GPT-5 mini, 400k), monter a ~100000 traite un livre en 1 passe.
    import_chunk_tokens: int = 10000

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
