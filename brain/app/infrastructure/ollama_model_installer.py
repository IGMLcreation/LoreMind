"""Auto-installation du modèle d'embeddings Ollama au démarrage du Brain.

Adapter d'infrastructure : parle directement à l'API HTTP d'Ollama. Best-effort
(Ollama peut être absent / la connexion limitée) — n'empêche jamais le démarrage.
"""
from __future__ import annotations

import asyncio
import logging

import httpx

logger = logging.getLogger(__name__)


async def ensure_ollama_embedding_model(base_url: str, model: str) -> None:
    """Télécharge `model` sur le serveur Ollama s'il n'y est pas déjà.

    Attend qu'Ollama soit joignable (ordre de démarrage des conteneurs), puis
    vérifie la présence du modèle avant de le tirer.
    """
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
