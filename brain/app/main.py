"""Point d'entrée FastAPI du Brain LoreMind : assemblage de l'application.

Responsabilité UNIQUE : créer l'app, brancher le middleware d'auth inter-service,
le hook de démarrage et les routers (un par responsabilité, voir `app.api.routers`).
Toute la logique HTTP vit dans les routers ; la logique métier dans `app.application`.
"""
import asyncio
import hmac
import logging

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.api.routers import (
    chat,
    generation,
    imports,
    models,
    notebooks,
    settings as settings_router,
    tables,
)
from app.core.config import get_settings
from app.infrastructure.ollama_model_installer import ensure_ollama_embedding_model

app = FastAPI(
    title="LoreMind Brain",
    description="Backend IA pour la génération de contenu narratif.",
    version="0.17.3",
)

logger = logging.getLogger(__name__)

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
    asyncio.create_task(ensure_ollama_embedding_model(
        settings.ollama_base_url, settings.ollama_embedding_model))


# Un router par responsabilité (SRP) — chemins identiques à l'ancien monolithe.
app.include_router(generation.router)
app.include_router(chat.router)
app.include_router(tables.router)
app.include_router(imports.router)
app.include_router(notebooks.router)
app.include_router(settings_router.router)
app.include_router(models.router)
