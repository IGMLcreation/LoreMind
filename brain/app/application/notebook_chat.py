"""Use case : chat ANCRÉ sur les sources d'un notebook (RAG).

À chaque message, on retrouve les passages pertinents des sources (via le RAG) et
on les injecte dans le prompt système, en plus du contexte de campagne. Le modèle
répond donc en s'appuyant sur la/les source(s) — pas sur ses connaissances générales.
"""
from __future__ import annotations

from typing import AsyncIterator

from app.application.notebook_rag import NotebookRagUseCase
from app.application.query_rewrite import standalone_question
from app.application.rerank import pool_size, rerank
from app.core.language import DEFAULT as _DEFAULT_LANG, language_name
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
une scène, un chapitre, une quête, un arc, une table aléatoire), termine ta réponse par
un ou plusieurs BLOCS D'ACTION — un objet JSON par bloc, dans une clôture
```loremind-action. L'interface les transformera en boutons « Créer dans la campagne ».
Si l'utilisateur demande PLUSIEURS éléments (« propose-moi 3 quêtes »), produis UN bloc
par élément. N'en mets pas si l'utilisateur pose une simple question.

VOCABULAIRE DE LA CAMPAGNE : une « quête » n'est PAS un type à part — c'est un CHAPITRE
rangé dans un arc de type HUB (quêtes parallèles, sans ordre imposé), tandis qu'un arc
LINEAR contient des chapitres joués en séquence. Donc :
- demande de QUÊTE → action "chapter" (l'utilisateur la placera dans son arc HUB) ;
  s'il n'a aucun arc HUB dans sa campagne, propose AUSSI une action "arc" avec
  "arcType": "HUB" pour les accueillir.
- demande de CHAPITRE → action "chapter" (destinée plutôt à un arc LINEAR).

RÈGLE CLÉ : remplis TOUS les champs pour lesquels tu as de la matière — pas seulement
le résumé ou les notes MJ. Chaque champ rempli atterrit au bon endroit de la fiche ;
un champ laissé vide est une fiche que l'utilisateur devra compléter à la main. Vise
2 à 5 phrases concrètes par champ narratif, tirées de la source et de la campagne.
Omets simplement un champ si tu n'as rien de précis à y mettre. Formats acceptés :

```loremind-action
{{"type": "npc", "name": "Nom",
  "description": "Résumé du PNJ (rôle, apparence, motivation).",
  "values": {{"<champ de la fiche PNJ>": "contenu", "<autre champ>": "contenu"}}}}
```
(`values` : utilise comme clés les CHAMPS DE LA FICHE PNJ listés dans le contexte
campagne s'ils y figurent — ex. "Histoire", "Apparence" — sinon omets `values`.)

```loremind-action
{{"type": "scene", "name": "Nom",
  "description": "Résumé court de la scène.",
  "location": "Lieu précis", "timing": "Quand elle survient",
  "atmosphere": "Ambiance sensorielle (sons, odeurs, lumière…)",
  "playerNarration": "Texte d'ambiance À LIRE AUX JOUEURS, immersif, à la 2e personne.",
  "gmSecretNotes": "Secrets, vérités cachées, notes pour le MJ uniquement.",
  "choicesConsequences": "Choix offerts aux joueurs et leurs conséquences.",
  "combatDifficulty": "Difficulté du combat éventuel", "enemies": "Ennemis présents (effectifs, tactiques)"}}
```
```loremind-action
{{"type": "chapter", "name": "Nom",
  "description": "Résumé du chapitre (ou de la quête).",
  "playerObjectives": "Objectifs tels que les joueurs les perçoivent.",
  "narrativeStakes": "Enjeux narratifs (ce qui se joue vraiment).",
  "gmNotes": "Notes MJ : fils à tirer, points d'attention."}}
```
```loremind-action
{{"type": "arc", "name": "Nom", "description": "Résumé", "arcType": "LINEAR",
  "themes": "Thèmes de l'arc", "stakes": "Enjeux",
  "rewards": "Récompenses attendues", "resolution": "Issues possibles",
  "gmNotes": "Notes MJ."}}
```
(`arcType` : "LINEAR" pour des chapitres en séquence, "HUB" pour un recueil de
quêtes parallèles.)
```loremind-action
{{"type": "table", "name": "Nom", "diceFormula": "1d8", "entries": [{{"minRoll":1,"maxRoll":4,"label":"...","detail":"..."}}]}}
```

Réponds en {language_name}, de façon utile et concise. Mets le texte explicatif AVANT les blocs d'action."""


class NotebookChatUseCase:
    def __init__(
        self, rag: NotebookRagUseCase, llm: LLMChatProvider, rerank_enabled: bool = False
    ) -> None:
        self._rag = rag
        self._llm = llm
        # Reranking LLM d'un pool élargi avant injection (voir app.application.rerank).
        self._rerank_enabled = rerank_enabled

    async def stream(
        self,
        source_ids: list[str],
        messages: list[ChatMessage],
        context: str = "",
        top_k: int = 6,
        language: str = _DEFAULT_LANG,
    ) -> AsyncIterator[dict]:
        """Yield des évènements : {type:'sources', sources:[…]} (une fois, avant la
        réponse — transparence sur les passages utilisés), puis {type:'token', token}."""
        # Question AUTONOME pour la recherche : sur une relance (« et ses
        # faiblesses ? »), l'embedding du dernier message seul ne contient pas
        # le sujet → on le résout depuis l'historique (best-effort, 1 appel léger,
        # uniquement à partir du 2e tour). La réponse, elle, voit tout l'historique.
        search_query = await standalone_question(self._llm, messages)
        if self._rerank_enabled:
            # Pool élargi → notation LLM → top_k final (meilleure précision sur
            # les questions ambiguës, au prix d'un appel avant le premier token).
            pool = await self._rag.retrieve(
                source_ids, search_query, top_k=pool_size(top_k))
            passages = await rerank(self._llm, search_query, pool, top_k)
        else:
            passages = await self._rag.retrieve(source_ids, search_query, top_k=top_k)
        # Évènement 'sources' AVANT le premier token : l'UI peut afficher les
        # pages utilisées (« 📖 p. 12, 47 ») dès le début de la réponse.
        yield {"type": "sources", "sources": [
            {
                "source_id": p.get("source_id"),
                "page": p.get("page"),
                "score": round(float(p.get("score") or 0.0), 3),
            }
            for p in passages
        ]}
        sources_block = (
            "\n\n".join(self._format_passage(p) for p in passages)
            if passages else "(aucun passage pertinent trouvé dans les sources)"
        )
        context_block = (
            f"--- TA CAMPAGNE ---\n{context.strip()}\n--- FIN CAMPAGNE ---\n\n"
            if context.strip() else "--- TA CAMPAGNE ---\n(aucune donnée de campagne)\n--- FIN CAMPAGNE ---\n\n"
        )
        system_prompt = _SYSTEM_PROMPT.format(
            context_block=context_block, sources_block=sources_block,
            language_name=language_name(language))
        async for token in self._llm.stream_chat(messages, system_prompt=system_prompt):
            yield {"type": "token", "token": token}

    @staticmethod
    def _format_passage(p: dict) -> str:
        page = p.get("page")
        prefix = f"(p. {page}) " if page else ""
        return f"• {prefix}{p['text'].strip()}"
