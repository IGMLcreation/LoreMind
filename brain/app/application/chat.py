"""Use case : chat conversationnel LoreMind avec Structural Context.

Construit un system prompt riche à partir de 4 contextes possibles
(Lore, Page focalisée, Campagne, entité narrative focalisée) puis délègue
au port `LLMChatProvider` pour le streaming token par token.

Ne charge PAS le contenu détaillé des pages — l'IA doit savoir ce qui
existe, pas être noyée sous le texte. Pattern "Structural Context", plus
simple que le RAG sémantique tant que les univers restent de taille humaine.

Combinaisons supportées :
  - lore seul                              → chat Lore (page-edit / page-create)
  - lore + page_context                    → chat Lore focalisé page
  - campaign (+lore si liée) + optional narrative_entity → chat Campagne
"""
from typing import AsyncIterator

from app.domain.models import (
    ArcSummary,
    CampaignStructuralContext,
    ChatMessage,
    ChapterSummary,
    CharacterSummary,
    GameSystemContext,
    LoreStructuralContext,
    NarrativeEntityContext,
    PageContext,
    PageSummary,
)
from app.domain.ports import LLMChatProvider


# Température moyenne : chat conversationnel créatif mais cohérent.
# Plus élevée que le one-shot (0.4) car on veut de la variété d'idées,
# mais sans partir en délire halluciné (1.0+).
_DEFAULT_TEMPERATURE = 0.7


_BASE_SYSTEM = """Tu es un assistant d'écriture pour un Maître de Jeu de JDR.
Tu dialogues avec le MJ pour l'aider à enrichir son univers et ses campagnes.

Règles de ton :
- Réponds en français, ton chaleureux et créatif.
- Sois concis : listes à puces courtes plutôt que longs paragraphes.
- Propose des idées qui s'intègrent dans le contexte existant ci-dessous.

Règles de cohérence (IMPORTANT) :
- Tu PEUX et DOIS inventer des éléments originaux (personnages, lieux, objets, intrigues, créatures, scènes) — c'est ton rôle d'assistant créatif.
- Tu ne peux PAS faire référence à un élément du MJ (du Lore, des arcs, chapitres ou scènes) comme s'il existait déjà, SAUF s'il apparaît EXACTEMENT (même orthographe) dans l'une des sections de contexte ci-dessous.
- Si l'utilisateur mentionne un nom que tu ne vois pas dans le contexte, ne fais surtout pas semblant de le connaître : dis clairement "Je ne vois pas [nom] dans le contexte actuel, veux-tu qu'on le crée ?" plutôt que d'inventer des détails à son sujet.
- Évite les précisions inventées qu'on ne peut pas vérifier : dates exactes, chiffres de population, hiérarchies politiques complexes, généalogies détaillées. Préfère des formulations ouvertes que le MJ validera ("il y a longtemps", "de nombreux", "la haute noblesse")."""


class ChatUseCase:
    """Orchestre un tour de conversation avec le LLM + contextes structurels."""

    def __init__(self, llm: LLMChatProvider) -> None:
        self._llm = llm

    async def stream(
        self,
        messages: list[ChatMessage],
        *,
        lore_context: LoreStructuralContext | None = None,
        page_context: PageContext | None = None,
        campaign_context: CampaignStructuralContext | None = None,
        narrative_entity: NarrativeEntityContext | None = None,
        game_system_context: GameSystemContext | None = None,
    ) -> AsyncIterator[str]:
        """Streame les tokens de la réponse assistant pour le dernier message user.

        Les contextes sont tous optionnels, mais au moins l'un des deux
        "niveaux haut" (lore_context ou campaign_context) doit être fourni
        pour que le prompt ait du sens. Le controller (main.py) applique
        cette règle à la frontière HTTP.
        """
        system_prompt = self._build_system_prompt(
            lore_context, page_context, campaign_context, narrative_entity, game_system_context
        )
        async for token in self._llm.stream_chat(
            messages,
            system_prompt=system_prompt,
            temperature=_DEFAULT_TEMPERATURE,
        ):
            yield token

    def build_system_prompt(
        self,
        lore_context: LoreStructuralContext | None = None,
        page_context: PageContext | None = None,
        campaign_context: CampaignStructuralContext | None = None,
        narrative_entity: NarrativeEntityContext | None = None,
        game_system_context: GameSystemContext | None = None,
    ) -> str:
        """Version publique — utilisée par le controller HTTP pour compter
        les tokens du system prompt avant de streamer (jauge de contexte).
        """
        return self._build_system_prompt(
            lore_context, page_context, campaign_context, narrative_entity, game_system_context
        )

    # --- Construction du system prompt --------------------------------------

    def _build_system_prompt(
        self,
        lore: LoreStructuralContext | None,
        page: PageContext | None,
        campaign: CampaignStructuralContext | None,
        narrative: NarrativeEntityContext | None,
        game_system: GameSystemContext | None = None,
    ) -> str:
        sections = [_BASE_SYSTEM]
        if lore is not None:
            sections.append(self._format_lore(lore))
        if campaign is not None:
            sections.append(self._format_campaign(campaign, lore_present=lore is not None))
        if game_system is not None:
            sections.append(self._format_game_system(game_system))
        if page is not None:
            sections.append(self._format_page(page))
        if narrative is not None:
            sections.append(self._format_narrative_entity(narrative))
        return "\n\n".join(sections)

    # --- Blocs Lore ---------------------------------------------------------

    @staticmethod
    def _format_lore(ctx: LoreStructuralContext) -> str:
        desc = f"\nDescription : {ctx.lore_description}" if ctx.lore_description else ""
        folders_block = ChatUseCase._format_folders(ctx.folders)
        tags_line = ", ".join(ctx.tags) if ctx.tags else "(aucun)"
        return (
            "--- UNIVERS (Lore) ---\n"
            f"Nom : {ctx.lore_name}{desc}\n\n"
            f"Organisation :\n{folders_block}\n\n"
            f"Tags déjà utilisés : {tags_line}"
        )

    @staticmethod
    def _format_folders(folders: dict[str, list[PageSummary]]) -> str:
        """Rend chaque page avec son contenu exploitable par le LLM.

        Depuis b9 : affiche en plus des champs values/tags/pages liées sous
        forme d'une fiche indentée par page, et seulement si l'info existe
        (prompt compact quand une page est vierge).
        """
        if not folders:
            return "(Lore vide pour l'instant)"
        lines: list[str] = []
        for folder_name, pages in folders.items():
            lines.append(f"- {folder_name} (dossier)")
            if not pages:
                lines.append("    (vide)")
                continue
            for ps in pages:
                lines.append(f"    - {ps.title} [template: {ps.template_name}]")
                for field_name, value in ps.values.items():
                    lines.append(f"        · {field_name} : {value}")
                if ps.tags:
                    lines.append(f"        · tags : {', '.join(ps.tags)}")
                if ps.related_page_titles:
                    lines.append(
                        "        · liée à : " + ", ".join(ps.related_page_titles)
                    )
        return "\n".join(lines)

    @staticmethod
    def _format_page(pc: PageContext) -> str:
        """Bloc "PAGE EN COURS" — oriente l'IA vers la page précise éditée."""
        if pc.template_fields:
            fields_block = "\n".join(
                f'- "{f}" : {pc.values.get(f) or "(vide)"}'
                for f in pc.template_fields
            )
        else:
            fields_block = "(aucun champ défini dans ce template)"
        return (
            "--- PAGE EN COURS D'ÉDITION ---\n"
            f"Titre : {pc.title}\n"
            f"Template : {pc.template_name}\n"
            f"Champs et valeurs actuelles :\n{fields_block}\n\n"
            "IMPORTANT : concentre-toi EXCLUSIVEMENT sur cette page. "
            "Si l'utilisateur te demande de proposer des idées, elles doivent "
            "concerner UNIQUEMENT les champs listés ci-dessus. Ne déborde pas "
            "vers d'autres pages ou d'autres templates du Lore, même si ça te "
            "semblerait pertinent."
        )

    # --- Blocs Campagne -----------------------------------------------------

    @staticmethod
    def _format_campaign(ctx: CampaignStructuralContext, *, lore_present: bool) -> str:
        desc = f"\nDescription : {ctx.campaign_description}" if ctx.campaign_description else ""
        arcs_block = ChatUseCase._format_arcs(ctx.arcs)
        lore_note = (
            "\n(Cette campagne est liée à l'univers ci-dessus : tu peux t'appuyer dessus.)"
            if lore_present
            else "\n(Cette campagne n'est associée à aucun univers — tu peux proposer des éléments d'ambiance libres.)"
        )
        characters_block = ChatUseCase._format_characters(ctx.characters)
        return (
            "--- CAMPAGNE COURANTE ---\n"
            f"Nom : {ctx.campaign_name}{desc}{lore_note}\n"
            f"{characters_block}\n"
            "Structure narrative (les flèches → indiquent des transitions de scène "
            "déclenchées par un choix des joueurs) :\n"
            f"{arcs_block}"
        )

    @staticmethod
    def _format_characters(characters: list[CharacterSummary]) -> str:
        """Bloc PJ — liste nom + snippet. Rappel anti-hallucination IA.

        Si la campagne n'a aucun PJ, on le signale explicitement : l'IA ne
        doit pas inventer "les héros" ou leurs noms dans ses suggestions.
        """
        if not characters:
            return (
                "\nPersonnages joueurs : aucune fiche pour l'instant. Ne suppose "
                "ni noms ni classes pour les PJ tant que le MJ ne les a pas créés.\n"
            )
        lines = ["\nPersonnages joueurs (PJ) :"]
        for c in characters:
            if c.snippet:
                lines.append(f"- **{c.name}** — {c.snippet}")
            else:
                lines.append(f"- **{c.name}** (fiche vide)")
        lines.append(
            "Pour une fiche complète (stats, backstory), n'invente rien : "
            "demande au MJ d'ouvrir l'éditeur du PJ pour te donner les détails."
        )
        return "\n".join(lines) + "\n"

    @staticmethod
    def _format_arcs(arcs: list[ArcSummary]) -> str:
        if not arcs:
            return "(Aucun arc créé pour l'instant.)"
        lines: list[str] = []
        for arc in arcs:
            lines.append(f"- {arc.name} (arc){ChatUseCase._illustration_hint(arc.illustration_count)}")
            if arc.description:
                lines.append(f"  Synopsis : {arc.description}")
            if not arc.chapters:
                lines.append("    (aucun chapitre)")
                continue
            for chapter in arc.chapters:
                lines.extend(ChatUseCase._format_chapter_block(chapter))
        return "\n".join(lines)

    @staticmethod
    def _format_chapter_block(chapter: ChapterSummary) -> list[str]:
        hint = ChatUseCase._illustration_hint(chapter.illustration_count)
        block = [f"    - {chapter.name} (chapitre){hint}"]
        if chapter.description:
            block.append(f"      Synopsis : {chapter.description}")
        if not chapter.scenes:
            block.append("        (aucune scène)")
        else:
            for scene in chapter.scenes:
                sc_hint = ChatUseCase._illustration_hint(scene.illustration_count)
                block.append(f"        - {scene.name} (scène){sc_hint}")
                if scene.description:
                    block.append(f"          Description : {scene.description}")
                for br in scene.branches:
                    cond = f" (si : {br.condition})" if br.condition else ""
                    block.append(
                        f'          → "{br.label}" vers {br.target_scene_name}{cond}'
                    )
        return block

    @staticmethod
    def _illustration_hint(count: int) -> str:
        """Rend " [N illustrations]" si count > 0, sinon chaine vide.

        Informe l'IA que l'entite a deja un support visuel. Permet de prioriser
        les suggestions ecrites qui collent a l'existant visuel plutot que de
        diverger.
        """
        if count <= 0:
            return ""
        noun = "illustration" if count == 1 else "illustrations"
        return f" [{count} {noun}]"

    # --- Bloc Système de JDR ------------------------------------------------

    @staticmethod
    def _format_game_system(gs: GameSystemContext) -> str:
        """Bloc des règles du système de JDR de la campagne.

        Les sections ont été filtrées côté Core selon l'intent (combat,
        classes, lore...). Si aucune section n'a matché, on affiche juste
        le nom du système comme rappel de cadre.
        """
        desc = f"\nDescription : {gs.system_description}" if gs.system_description else ""
        if not gs.sections:
            return (
                "--- SYSTÈME DE JDR ---\n"
                f"Nom : {gs.system_name}{desc}\n"
                "(Aucune section de règles pertinente pour ce type de génération — "
                "reste cohérent avec l'univers et les conventions du système.)"
            )
        sections_block = "\n\n".join(
            f"### {title}\n{content}" for title, content in gs.sections.items()
        )
        return (
            "--- SYSTÈME DE JDR ---\n"
            f"Nom : {gs.system_name}{desc}\n\n"
            "Respecte scrupuleusement les règles et conventions ci-dessous quand "
            "tu proposes des stats, classes, rencontres, mécaniques ou éléments "
            "d'ambiance. Les noms propres (classes, sorts, monstres) doivent "
            "venir de ces règles — n'en invente pas d'autres.\n\n"
            f"{sections_block}"
        )

    @staticmethod
    def _format_narrative_entity(ne: NarrativeEntityContext) -> str:
        """Bloc équivalent à _format_page mais pour Arc/Chapter/Scene."""
        type_label = {
            "arc": "ARC",
            "chapter": "CHAPITRE",
            "scene": "SCÈNE",
            "character": "FICHE DE PERSONNAGE",
        }.get(ne.entity_type.lower(), ne.entity_type.upper())
        if ne.fields:
            fields_block = "\n".join(
                f'- "{key}" : {value or "(vide)"}'
                for key, value in ne.fields.items()
            )
        else:
            fields_block = "(aucun champ renseigné)"
        return (
            f"--- {type_label} EN COURS D'ÉDITION ---\n"
            f"Titre : {ne.title}\n"
            f"Champs et valeurs actuelles :\n{fields_block}\n\n"
            "IMPORTANT : concentre-toi EXCLUSIVEMENT sur cette entité narrative. "
            "Tes suggestions doivent enrichir UNIQUEMENT les champs listés ci-dessus. "
            "Ne déborde pas vers d'autres arcs, chapitres ou scènes de la campagne, "
            "même si ça te semblerait pertinent."
        )
