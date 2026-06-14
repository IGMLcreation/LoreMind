"""Extraction robuste d'un objet JSON depuis une réponse LLM.

Les LLM enrobent souvent leur JSON : fences markdown ```json … ```, texte
d'introduction, commentaire de fin, voire un 2e objet. Un simple
`json.loads(raw)` ou un `raw[first_brace:last_brace]` échoue dans ces cas
("Extra data", accolade parasite dans une string, etc.).

Cette fonction scanne depuis la PREMIÈRE `{` et renvoie exactement le premier
objet `{…}` ÉQUILIBRÉ, en ignorant les accolades à l'intérieur des chaînes JSON
et tout ce qui suit. Renvoie None si aucun objet complet n'est trouvé
(sortie tronquée / accolades non refermées).
"""
from __future__ import annotations

import json
import re

# Blocs de "réflexion" des modèles raisonneurs (Nemotron, DeepSeek-R1, QwQ…).
# Leur contenu est de la prose truffée d'accolades qui piège le détecteur de JSON
# (et n'est jamais la réponse) → on le retire avant toute analyse.
_REASONING_RE = re.compile(r"<think(?:ing)?>.*?</think(?:ing)?>", re.DOTALL | re.IGNORECASE)


def _strip_reasoning(raw: str) -> str:
    return _REASONING_RE.sub("", raw)


def load_json_object(raw: str) -> tuple[object | None, bool]:
    """Parse un objet JSON depuis une réponse LLM, avec récupération si tronqué.

    Renvoie (objet_parsé, récupéré_partiellement) :
      - d'abord on tente le 1er objet complet (extract_json_object) ;
      - sinon on tente une réparation du JSON tronqué (repair_truncated_json),
        auquel cas le second élément vaut True.
    (None, False) si rien d'exploitable.
    """
    raw = _strip_reasoning(raw)
    obj = extract_json_object(raw)
    if obj is not None:
        try:
            # strict=False : tolère les caractères de contrôle BRUTS (retours à la
            # ligne non échappés…) dans les chaînes — erreur fréquente des LLM hors
            # mode JSON natif, qui invalidait toute la réponse.
            return json.loads(obj, strict=False), False
        except json.JSONDecodeError:
            pass
    repaired = repair_truncated_json(raw)
    if repaired is not None:
        try:
            return json.loads(repaired, strict=False), True
        except json.JSONDecodeError:
            pass
    return None, False


def looks_like_truncated_json(raw: str) -> bool:
    """La sortie ressemble-t-elle à un JSON COUPÉ (accolades/crochets non refermés)
    plutôt qu'à de la prose ? Sert à déclencher un re-découpage même quand RIEN n'a
    pu être récupéré (cas où le 1er contenu est si long qu'il est coupé avant toute
    sous-structure complète).

    Une réponse qui COMMENCE par `{` est jugée sur le seul équilibre des accolades,
    même très courte : en mode JSON un `{"` de 2 caractères est une génération
    interrompue net (contexte plein, plafond de sortie), pas de la prose — c'est le
    signal de re-découpage. Pour le reste (prose contenant des accolades), on exige
    un contenu substantiel pour éviter les faux positifs."""
    s = _strip_reasoning(raw or "").strip()
    if "{" not in s:
        return False
    unbalanced = s.count("{") > s.count("}") or s.count("[") > s.count("]")
    if s.startswith("{"):
        return unbalanced
    return len(s) >= 100 and unbalanced


def extract_json_object(raw: str) -> str | None:
    if not raw:
        return None
    text = raw.strip()
    start = text.find("{")
    if start == -1:
        return None

    depth = 0
    in_string = False
    escape = False
    for i in range(start, len(text)):
        c = text[i]
        if in_string:
            if escape:
                escape = False
            elif c == "\\":
                escape = True
            elif c == '"':
                in_string = False
        else:
            if c == '"':
                in_string = True
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    return text[start : i + 1]
    return None  # accolades non refermées (réponse probablement tronquée)


# Fermeture correspondante de chaque ouvrant, pour reconstituer un JSON tronqué.
_CLOSE_OF = {"{": "}", "[": "]"}


def repair_truncated_json(raw: str) -> str | None:
    """Répare un JSON COUPÉ (sortie LLM tronquée) en gardant les éléments complets.

    On scanne depuis la première `{` et on retient le DERNIER point où un conteneur
    (`}` ou `]`) vient de se fermer — donc juste après une sous-structure complète
    (un arc / chapitre / scène / pièce / section entièrement écrit). On coupe là et
    on referme les conteneurs encore ouverts. L'élément en cours d'écriture au moment
    de la troncature est abandonné, mais tous les précédents sont sauvés.

    Renvoie une chaîne JSON équilibrée (à valider par json.loads) ou None.
    """
    if not raw:
        return None
    text = raw.strip()
    start = text.find("{")
    if start == -1:
        return None

    stack: list[str] = []
    in_string = False
    escape = False
    best_cut = -1            # index (exclusif) où couper
    best_closing = ""        # fermetures à ajouter pour rééquilibrer

    for i in range(start, len(text)):
        c = text[i]
        if in_string:
            if escape:
                escape = False
            elif c == "\\":
                escape = True
            elif c == '"':
                in_string = False
        else:
            if c == '"':
                in_string = True
            elif c in "{[":
                stack.append(c)
            elif c in "}]":
                if stack:
                    stack.pop()
                # Point de coupe sûr : on vient de fermer une sous-structure complète.
                best_cut = i + 1
                best_closing = "".join(_CLOSE_OF[b] for b in reversed(stack))

    if best_cut == -1:
        return None  # rien de complet à sauver
    head = text[start:best_cut].rstrip().rstrip(",")
    return head + best_closing
