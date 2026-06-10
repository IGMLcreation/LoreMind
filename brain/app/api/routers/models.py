"""Endpoints de catalogue de modèles (Ollama, OpenRouter, Mistral, Gemini, 1min.ai).

Proxifie les APIs des providers pour que l'UI propose des listes de modèles ;
repli statique quand l'API est injoignable ou la clé absente (pas de 500 à l'UI).
"""
import json
from typing import Annotated, AsyncIterator

import httpx
from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from app.core.config import Settings, get_settings

router = APIRouter()


@router.get("/models/ollama")
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


@router.post("/models/ollama/info", response_model=OllamaModelInfoDTO)
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


@router.post("/models/ollama/pull")
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


@router.delete("/models/ollama/{name:path}")
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


@router.get("/models/openrouter")
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


@router.get("/models/mistral")
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


@router.get("/models/gemini")
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


@router.get("/models/onemin")
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
