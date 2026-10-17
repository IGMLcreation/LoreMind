"""Méthodes guidées : prompt de propositions et nettoyage des réponses du modèle."""
from __future__ import annotations

from app.api.routers.generation import GuidedFieldDTO, GuidedOptionDTO, _clean_guided_answers
from app.application.prompts.guided_answers import guided_answers_prompt


def _fields() -> dict[str, GuidedFieldDTO]:
    return {
        "title": GuidedFieldDTO(key="title", label="Nom de l'enquête"),
        "node1.nature": GuidedFieldDTO(key="node1.nature", label="Genre de scène", options=[
            GuidedOptionDTO(value="location", label="Un lieu"), GuidedOptionDTO(value="npc", label="Une personne")]),
    }


def test_prompt_contains_idea_questions_choices_existing_answers_and_language():
    prompt = guided_answers_prompt(
        "Enquête en nœuds", "Trois pistes qui se recoupent.", "Une tour qui se rallume", "Campagne : Valfroide",
        [{"key": "title", "label": "Nom de l'enquête", "hint": "Ex. : L'héritage", "step": "Le mystère"},
         {"key": "node1.nature", "label": "Genre", "options": [{"value": "location", "label": "Un lieu"}]}],
        {"truth": "Le notaire est coupable", "empty": "  "}, "en")

    assert "Une tour qui se rallume" in prompt
    assert '"title" [Le mystère] : Nom de l\'enquête — Ex. : L\'héritage' in prompt
    assert 'UNE valeur parmi : "location" (Un lieu)' in prompt
    assert "Le notaire est coupable" in prompt
    assert '"empty"' not in prompt, "une réponse vide n'est pas rappelée"
    assert "Campagne : Valfroide" in prompt
    assert "anglais" in prompt


def test_prompt_without_idea_asks_for_an_original_adventure():
    prompt = guided_answers_prompt("Plan du méchant", "", "  ", "", [{"key": "villain", "label": "Adversaire"}], {}, "fr")
    assert "propose une aventure originale" in prompt
    assert "français" in prompt


def test_clean_keeps_requested_keys_and_valid_choices_only():
    out = _clean_guided_answers({
        "title": "  L'héritage empoisonné ",
        "node1.nature": "npc",
        "unknown": "ignorée",
        "empty": "",
    }, _fields())
    assert out == {"title": "L'héritage empoisonné", "node1.nature": "npc"}


def test_clean_rejects_an_invalid_choice_and_non_text_values():
    out = _clean_guided_answers({"node1.nature": "dragon", "title": ["liste"]}, _fields())
    assert out == {}
    assert _clean_guided_answers("pas un objet", _fields()) == {}
