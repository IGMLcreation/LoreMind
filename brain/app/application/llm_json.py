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


def load_json_object(raw: str) -> tuple[object | None, bool]:
    """Parse un objet JSON depuis une réponse LLM, avec récupération si tronqué.

    Renvoie (objet_parsé, récupéré_partiellement) :
      - d'abord on tente le 1er objet complet (extract_json_object) ;
      - sinon on tente une réparation du JSON tronqué (repair_truncated_json),
        auquel cas le second élément vaut True.
    (None, False) si rien d'exploitable.
    """
    obj = extract_json_object(raw)
    if obj is not None:
        try:
            return json.loads(obj), False
        except json.JSONDecodeError:
            pass
    repaired = repair_truncated_json(raw)
    if repaired is not None:
        try:
            return json.loads(repaired), True
        except json.JSONDecodeError:
            pass
    return None, False


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
