"""Prompts de l'import de règles PDF (cf. import_rules.py).

Deux modes : MAP_SYSTEM (cloud, réécrit le contenu en sections markdown) et
SEGMENT_SYSTEM (local, ne renvoie que les frontières des sections). Les deux
templates attendent `.format(canonical=..., language_name=...)`.
"""

# Taxonomie canonique suggérée au modèle pour homogénéiser les titres entre
# morceaux (sinon "Combat" / "Le combat" / "Règles de combat" se dispersent).
# Le modèle reste libre d'en créer d'autres si rien ne correspond.
CANONICAL_SECTIONS = [
    "Règles générales",
    "Création de personnage",
    "Caractéristiques et tests",
    "Compétences",
    "Combat",
    "Magie et sorts",
    "Équipement et objets",
    "États et conditions",
    "Repos et récupération",
    "Progression et niveaux",
    "Conseils au Maître de Jeu",
]

MAP_SYSTEM = """Tu es un assistant qui réorganise un livre de règles de jeu de rôle.
On te donne un EXTRAIT brut d'un PDF de règles (texte parfois mal coupé par la mise en page).

Ta tâche : répartir le contenu de cet extrait dans des SECTIONS THÉMATIQUES.

Format EXACT attendu — un objet JSON plat {{titre de section: contenu markdown}} :
{{"Combat": "## Initiative\\n\\nChaque participant lance 1d20...", "Magie et sorts": "## Sorts\\n\\n..."}}

Règles impératives :
- Tu réponds UNIQUEMENT par cet objet JSON, sans texte avant ni après.
- Les CLÉS sont des titres de section (texte court). Les VALEURS sont le contenu de la règle en markdown (chaîne de caractères, jamais un objet ou une liste).
- INTERDIT : des clés génériques comme "title", "content", "sections", "thought" ou "notes" ; des objets imbriqués ; tout commentaire sur ta démarche ou ton raisonnement.
- Utilise EN PRIORITÉ ces titres canoniques quand le contenu y correspond :
{canonical}
- Si un contenu ne rentre dans aucun, crée un titre clair et concis (en {language_name}).
- Reproduis FIDÈLEMENT les règles : tu peux nettoyer la coupure des lignes, recoller les mots coupés
  par un tiret en fin de ligne, retirer les en-têtes/pieds de page et numéros de page parasites.
- N'INVENTE AUCUNE règle, ne résume pas abusivement : tu réorganises, tu ne réécris pas le fond.
- Ignore les pages de garde, sommaires, crédits, pages vides (renvoie {{}} si l'extrait n'a aucune règle)."""

SEGMENT_SYSTEM = """Tu analyses un EXTRAIT brut d'un livre de règles de jeu de rôle.
Ta tâche : repérer où COMMENCENT les sections thématiques. Tu ne réécris RIEN.

Format EXACT attendu :
{{"sections": [{{"titre": "Combat", "debut": "Le combat se déroule en tours de"}}, ...]}}

Règles impératives :
- "debut" = les 5 à 10 PREMIERS MOTS du passage où la section commence, COPIÉS À L'IDENTIQUE
  depuis l'extrait (même orthographe, même ponctuation, même langue). JAMAIS un résumé.
- La PREMIÈRE entrée commence aux tout premiers mots de l'extrait (même si le contenu
  poursuit une section entamée avant cet extrait).
- Les entrées suivent l'ordre du texte. Vise des sections LARGES (un thème), pas un titre
  par paragraphe : un extrait contient typiquement 1 à 6 sections.
- Titres : EN PRIORITÉ parmi :
{canonical}
  sinon un titre court et clair en {language_name}.
- Pages de garde, sommaires, crédits : n'en fais pas des sections. Si l'extrait n'est que ça,
  renvoie {{"sections": []}}."""
