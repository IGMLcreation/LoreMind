"""Langue de sortie de l'IA, pilotée par l'utilisateur (et non plus figée en FR).

Le Core relaie la langue choisie dans l'UI via l'entête HTTP `X-User-Language`
(`fr`/`en`). Ce module centralise :
  - la normalisation du code reçu (tolérante : `en-US`, `EN`, un `Accept-Language`
    brut… → `en`) avec repli sur le français ;
  - la fabrique de la directive de langue injectée dans les prompts ;
  - la dépendance FastAPI qui lit l'entête côté router.

Ajouter une langue = une entrée dans `NAMES`. Aucun autre branchement n'est requis.
"""
from typing import Annotated

from fastapi import Header

# Nom (en français, langue de travail des prompts) de chaque langue supportée.
# La clé est le code court ISO 639-1 utilisé par l'UI (cf. LanguageService Angular).
NAMES: dict[str, str] = {
    "fr": "français",
    "en": "anglais",
}

DEFAULT = "fr"


def normalize(raw: str | None) -> str:
    """Réduit un code/entête langue arbitraire à un code supporté (`fr`/`en`).

    Tolère les variantes régionales (`en-GB`), la casse, et un `Accept-Language`
    complet (`fr-FR,fr;q=0.9,en;q=0.8`) dont on ne garde que la 1re préférence.
    Repli systématique sur `DEFAULT` si rien ne matche.
    """
    if not raw:
        return DEFAULT
    # 1re préférence d'un éventuel Accept-Language, puis base avant le tiret régional.
    primary = raw.split(",")[0].split(";")[0].strip().lower()
    base = primary.split("-")[0]
    return base if base in NAMES else DEFAULT


def language_name(lang: str) -> str:
    """Nom de la langue (pour insertion inline dans un prompt)."""
    return NAMES.get(lang, NAMES[DEFAULT])


def instruction(lang: str) -> str:
    """Directive forte à injecter dans un prompt pour imposer la langue de sortie."""
    return (
        f"IMPORTANT : rédige l'INTÉGRALITÉ de ta réponse en {language_name(lang)}, "
        "quelle que soit la langue du contexte ou des documents fournis."
    )


def get_user_language(
    x_user_language: Annotated[str | None, Header()] = None,
) -> str:
    """Dépendance FastAPI : langue de l'utilisateur lue depuis l'entête `X-User-Language`.

    Absente (appel direct, vieux client) → français par défaut.
    """
    return normalize(x_user_language)
