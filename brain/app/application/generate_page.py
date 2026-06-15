"""Use case : génération d'une page LoreMind à partir d'un contexte métier.

Couche APPLICATION — au-dessus du domaine, en-dessous de l'infra web.
Orchestre le flux : contexte → prompt → appel LLM → parsing JSON → résultat.

Ne dépend que des abstractions du domaine (port `LLMProvider`). C'est ce qui
permet de tester ce use case avec un FakeLLMProvider, sans Ollama qui tourne.
"""
import json

from app.application.prompts import generate_page as prompts
from app.domain.models import PageGenerationContext, PageGenerationResult
from app.domain.ports import LLMProvider, LLMProviderError

# Langue de repli quand le router n'en fournit pas (appel direct / vieux client).
from app.core.language import DEFAULT as _DEFAULT_LANG


# Température basse : remplissage de champs = tâche factuelle, peu créative.
# Une valeur trop haute (par défaut Ollama = 0.8) encourage l'IA à broder
# et à inventer des références à des PNJ/lieux/événements inexistants.
_DEFAULT_TEMPERATURE = 0.4


class GeneratePageUseCase:
    """Orchestre la génération d'une page LoreMind via un LLM."""

    def __init__(self, llm: LLMProvider) -> None:
        self._llm = llm

    async def execute(
        self,
        context: PageGenerationContext,
        language: str = _DEFAULT_LANG,
    ) -> PageGenerationResult:
        prompt = self._build_prompt(context, language)
        raw = await self._llm.generate(
            prompt,
            output_format="json",
            temperature=_DEFAULT_TEMPERATURE,
        )
        values = self._parse_values(raw, context.template_fields)
        return PageGenerationResult(values=values)

    @staticmethod
    def _build_prompt(context: PageGenerationContext, language: str = _DEFAULT_LANG) -> str:
        fields_block = "\n".join(f'- "{field}"' for field in context.template_fields)
        lore_desc_line = (
            f"\nDescription de l'univers : {context.lore_description}"
            if context.lore_description
            else ""
        )

        return (
            f"{prompts.system_instructions(language)}\n\n"
            f"Univers : {context.lore_name}"
            f"{lore_desc_line}\n"
            f"Catégorie (dossier) : {context.folder_name}\n"
            f"Gabarit : {context.template_name}\n"
            f"Titre de la page à créer : {context.page_title}\n\n"
            f"Champs à remplir (clés JSON attendues) :\n"
            f"{fields_block}\n\n"
            f"Génère maintenant le JSON."
        )

    @staticmethod
    def _parse_values(
            raw: str,
        expected_fields: list[str],
    ) -> dict[str, str]:
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise LLMProviderError(
                f"Réponse du LLM non parseable en JSON : {exc}"
            ) from exc

        if not isinstance(parsed, dict):
            raise LLMProviderError(
                f"Le LLM a renvoyé un {type(parsed).__name__}, pas un objet JSON."
            )

        # Filtrage défensif : on ne garde que les champs demandés, cast en str,
        # jamais None. Les champs absents de la réponse deviennent des chaînes vides
        # (l'utilisateur les complètera manuellement dans page-edit).
        return {
            field: str(parsed.get(field, "")).strip()
            for field in expected_fields
        }
