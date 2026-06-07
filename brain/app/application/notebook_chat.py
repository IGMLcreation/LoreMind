"""Use case : chat ANCRÉ sur les sources d'un notebook (RAG).

À chaque message, on retrouve les passages pertinents des sources (via le RAG) et
on les injecte dans le prompt système, en plus du contexte de campagne. Le modèle
répond donc en s'appuyant sur la/les source(s) — pas sur ses connaissances générales.
"""
from __future__ import annotations

from typing import AsyncIterator

from app.application.notebook_rag import NotebookRagUseCase
from app.domain.models import ChatMessage
from app.domain.ports import LLMChatProvider

_SYSTEM_PROMPT = """Tu es un assistant de jeu de rôle qui aide à ADAPTER une source (PDF) à la CAMPAGNE de l'utilisateur.

Tu disposes de DEUX connaissances, toutes deux ci-dessous :
1) LA CAMPAGNE de l'utilisateur (sa structure arcs/chapitres/scènes, ses PNJ, son univers) ;
2) LA SOURCE (extraits pertinents du PDF).

Règles :
- Pour une question sur SA CAMPAGNE (ex. « mon chapitre 3 », « mes PNJ »), appuie-toi sur la section CAMPAGNE.
- Pour une question sur le livre, appuie-toi sur les EXTRAITS DE LA SOURCE.
- CROISE les deux pour proposer des adaptations cohérentes avec sa campagne existante.
- N'invente pas ce qui ne figure ni dans la campagne ni dans la source ; si tu ne sais pas, dis-le.
- Quand un extrait porte un numéro de page (« (p. 12) »), cite-le (« d'après la p. 12 »).

{context_block}
--- EXTRAITS PERTINENTS DE LA SOURCE ---
{sources_block}
--- FIN DES EXTRAITS ---

PROPOSITIONS D'INTÉGRATION (IMPORTANT) :
Quand l'utilisateur veut CRÉER ou ADAPTER un élément concret pour sa campagne (un PNJ,
une scène, un chapitre, un arc, une table aléatoire), termine ta réponse par un ou
plusieurs BLOCS D'ACTION — un objet JSON par bloc, dans une clôture ```loremind-action.
L'interface les transformera en boutons « Créer dans la campagne ». N'en mets que si
c'est pertinent et explicitement souhaité. Formats acceptés :

```loremind-action
{{"type": "npc", "name": "Nom", "description": "Fiche en quelques phrases."}}
```
```loremind-action
{{"type": "scene", "name": "Nom", "description": "Résumé", "content": "Déroulé détaillé."}}
```
```loremind-action
{{"type": "chapter", "name": "Nom", "description": "Résumé du chapitre."}}
```
```loremind-action
{{"type": "arc", "name": "Nom", "description": "Résumé", "arcType": "LINEAR"}}
```
```loremind-action
{{"type": "table", "name": "Nom", "diceFormula": "1d8", "entries": [{{"minRoll":1,"maxRoll":4,"label":"...","detail":"..."}}]}}
```

Réponds en français, de façon utile et concise. Mets le texte explicatif AVANT les blocs d'action."""


class NotebookChatUseCase:
    def __init__(self, rag: NotebookRagUseCase, llm: LLMChatProvider) -> None:
        self._rag = rag
        self._llm = llm

    async def stream(
        self,
        source_ids: list[str],
        messages: list[ChatMessage],
        context: str = "",
        top_k: int = 6,
    ) -> AsyncIterator[str]:
        last_user = next((m.content for m in reversed(messages) if m.role == "user"), "")
        passages = await self._rag.retrieve(source_ids, last_user, top_k=top_k)
        sources_block = (
            "\n\n".join(self._format_passage(p) for p in passages)
            if passages else "(aucun passage pertinent trouvé dans les sources)"
        )
        context_block = (
            f"--- TA CAMPAGNE ---\n{context.strip()}\n--- FIN CAMPAGNE ---\n\n"
            if context.strip() else "--- TA CAMPAGNE ---\n(aucune donnée de campagne)\n--- FIN CAMPAGNE ---\n\n"
        )
        system_prompt = _SYSTEM_PROMPT.format(
            context_block=context_block, sources_block=sources_block)
        async for token in self._llm.stream_chat(messages, system_prompt=system_prompt):
            yield token

    @staticmethod
    def _format_passage(p: dict) -> str:
        page = p.get("page")
        prefix = f"(p. {page}) " if page else ""
        return f"• {prefix}{p['text'].strip()}"
