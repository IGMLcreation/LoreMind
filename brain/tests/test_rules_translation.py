"""Règles en deux langues : détection de la langue, découpe des longs contenus, traduction."""
from __future__ import annotations

from app.application.rules_translation import (
    RulesTranslator, detect_language, other_languages, split_markdown, unique_titles,
)


class EchoLLM:
    """Traduit « en majuscules » et garde trace des prompts reçus."""

    def __init__(self, titles_json: str = '{"titles": {}}') -> None:
        self.prompts: list[str] = []
        self._titles_json = titles_json

    async def generate(self, prompt: str, *, output_format=None, temperature=None) -> str:
        self.prompts.append(prompt)
        if "Traduis ces titres" in prompt:
            return self._titles_json
        return prompt.split("Extrait :\n", 1)[1].upper()


def test_detect_language():
    assert detect_language("Le joueur lance les dés et la partie est terminée pour vous.") == "fr"
    assert detect_language("The player rolls the dice and the game is over for you.") == "en"
    assert detect_language("", default="en") == "en"
    assert other_languages("fr") == ["en"]
    assert other_languages("en") == ["fr"]


def test_split_markdown_keeps_short_text_and_cuts_at_paragraphs():
    assert split_markdown("court") == ["court"]
    text = "\n\n".join(["a" * 40] * 10)
    chunks = split_markdown(text, max_chars=100)
    assert all(len(c) <= 100 for c in chunks)
    assert "".join(chunks).replace("\n", "") == text.replace("\n", "")


def test_split_markdown_cuts_a_giant_paragraph():
    chunks = split_markdown("x" * 250, max_chars=100)
    assert [len(c) for c in chunks] == [100, 100, 50]


async def test_translate_content_keeps_names_instruction_and_joins_chunks():
    llm = EchoLLM()
    out = await RulesTranslator(llm).translate_content("## Combat\n\nChaque round.", "fr", "en")
    assert out == "## COMBAT\n\nCHAQUE ROUND."
    assert "noms propres" in llm.prompts[0]
    assert "de la langue « français » vers la langue « anglais »" in llm.prompts[0]


async def test_translate_titles_falls_back_to_the_original_title():
    llm = EchoLLM('{"titles": {"Combat": "Fighting"}}')
    out = await RulesTranslator(llm).translate_titles(["Combat", "Magie"], "fr", "en")
    assert out == {"Combat": "Fighting", "Magie": "Magie"}


def test_unique_titles_avoids_collisions():
    assert unique_titles({"A": "Combat", "B": "Combat", "C": "Magie"}) == {
        "A": "Combat", "B": "Combat (2)", "C": "Magie"}
