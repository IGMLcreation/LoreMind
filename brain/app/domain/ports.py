"""Ports (contrats) du domaine du Brain LoreMind.

Un Port est une INTERFACE abstraite exposée par le domaine vers le monde
extérieur. Le domaine définit CE QU'IL ATTEND, pas COMMENT c'est implémenté.

En Python moderne on privilégie Protocol (PEP 544) sur ABC pour bénéficier
du duck typing structurel : toute classe qui possède les bonnes méthodes
satisfait le contrat, sans héritage explicite.
"""
from typing import AsyncIterator, Protocol


class LLMProvider(Protocol):
    """Port sortant — contrat pour un fournisseur de modèle de langage.

    Toute implémentation (Ollama, OpenAI, Claude, faux-mock de test) doit
    exposer au minimum cette méthode `generate`.
    """

    async def generate(
        self,
        prompt: str,
        *,
        output_format: str | None = None,
        temperature: float | None = None,
    ) -> str:
        """Génère une réponse textuelle à partir d'un prompt donné.

        Args:
            prompt: le texte envoyé au modèle.
            output_format: contrainte de format optionnelle. Exemple : "json"
                pour forcer le modèle à renvoyer du JSON valide. Les
                fournisseurs qui ne supportent pas une valeur donnée doivent
                l'ignorer silencieusement ou la traduire au mieux.
            temperature: créativité du modèle, 0.0 (déterministe/factuel) à
                1.0+ (très créatif, hallucine plus facilement). None =
                valeur par défaut de l'adapter. Recommandation LoreMind :
                ~0.4 pour du remplissage factuel, ~0.7 pour du chat créatif.

        Raises:
            LLMProviderError: si le fournisseur sous-jacent a échoué.
        """
        ...


class LLMChatProvider(Protocol):
    """Port sortant — fournisseur de chat streamé (conversation multi-tours).

    Distinct de LLMProvider par Interface Segregation Principle : le chat
    streamé est une capacité séparée (messages structurés, flux de tokens)
    qui mérite son propre contrat. Un même adapter concret (ex: Ollama)
    peut satisfaire les deux protocoles simultanément grâce au duck typing.
    """

    async def stream_chat(
        self,
        messages: list["ChatMessage"],  # forward ref, évite import circulaire
        *,
        system_prompt: str | None = None,
        temperature: float | None = None,
    ) -> AsyncIterator[str]:
        """Streame la réponse du LLM token par token.

        Args:
            messages: historique de la conversation (chronologique, le dernier
                message étant typiquement celui de l'utilisateur en attente
                de réponse).
            system_prompt: instructions système optionnelles (contexte global,
                règles de comportement). Prefixe la conversation si fourni.
            temperature: créativité du modèle (voir `LLMProvider.generate`).

        Yields:
            Fragments de texte (tokens) au fur et à mesure de la génération.

        Raises:
            LLMProviderError: si le fournisseur sous-jacent a échoué.
        """
        ...


class LLMProviderError(Exception):
    """Erreur du domaine signalant qu'un LLMProvider n'a pas pu générer.

    Définie dans le domaine (pas dans l'infra) pour que les couches
    supérieures puissent l'attraper sans connaître l'adapter concret.
    """
