"""Prompts de l'import de campagne PDF (cf. import_campaign.py)."""

# Nom de l'arc unique quand le livre n'est pas découpé en actes/parties.
DEFAULT_ARC_NAME = "Aventure principale"

MAP_SYSTEM = """Tu es un assistant qui structure un livre de campagne de jeu de rôle.
On te donne un EXTRAIT brut d'un PDF de campagne (texte parfois mal coupé par la mise en page).

Ta tâche : en dégager une ARBORESCENCE narrative à GROS GRAIN : arcs → chapitres → scènes,
et — pour les lieux explorables — leurs PIÈCES (rooms).
  - Un ARC = un acte / une grande partie de la campagne (souvent un seul pour une aventure courte).
  - Un CHAPITRE = une étape majeure du récit : un chapitre du livre, OU — dans une
    campagne "hub" / bac-à-sable — UNE QUÊTE ou UN LIEU principal débloqué depuis le
    point central (ex : Dragon of Icespire Peak → chaque quête/lieu = un chapitre).
  - Une SCÈNE = un temps fort jouable du chapitre : un lieu, une rencontre clé, un moment pivot.
  - Une PIÈCE (room) = une salle d'un lieu explorable (donjon, crypte, manoir...).

TYPE D'ARC ("type") :
- "HUB" si la campagne est un bac-à-sable : des quêtes/lieux optionnels, parallèles,
  débloqués depuis un point central, SANS ordre fixe imposé (ex : Dragon of Icespire Peak).
- "LINEAR" si les chapitres se jouent dans un ordre séquentiel imposé.
- Dans le doute : "LINEAR".

GRANULARITÉ (évite la sur-détection) :
- Vise PEU de scènes : typiquement 1 à 6 par chapitre. PAS des dizaines.
- Un LIEU EXPLORABLE (donjon, crypte, manoir, grotte à plusieurs salles) = UNE SEULE
  scène. Ses salles vont dans le tableau "rooms" de cette scène — JAMAIS en scènes séparées.
- NE crée PAS une scène par rencontre isolée, par PNJ, par monstre ou par paragraphe.
- IGNORE : blocs de stats, listes de monstres, encarts de règles, légendes de cartes,
  pieds de page, sommaires, crédits.

CONTENU D'UNE SCÈNE (fidélité au livre — important) :
- `description` = synopsis de la scène, 2 à 4 phrases (plus que 1 ligne, mais pas le texte intégral).
- `player_narration` = le texte d'AMBIANCE « à lire aux joueurs » (encadrés / boxed text /
  « lecture à voix haute »), recopié FIDÈLEMENT s'il existe dans l'extrait. Vide sinon.
- `gm_notes` = les informations pour le MJ : secrets, développement, ce qui se passe,
  conséquences, indices cachés. Vide si rien de tel.
- Ne RÉSUME pas abusivement player_narration et gm_notes : recopie le contenu utile du livre.

PIÈCES (rooms) — uniquement pour les scènes qui sont des lieux explorables :
- Une entrée par salle numérotée/nommée du donjon (ex : "1. Entrée", "2. Salle des gardes").
- `enemies` = créatures/boss de la salle (vide si aucune). `loot` = trésor/récompense (vide si aucun).
- Pour une scène narrative classique (pas un donjon), "rooms" est un tableau vide [].

PNJ ET CRÉATURES NOTABLES ("npcs", tableau au niveau racine) :
- Recense les PNJ NOMMÉS (alliés, marchands, antagonistes) et les créatures UNIQUES
  (boss, monstre récurrent) présents dans l'extrait.
- `description` = courte fiche utile au MJ : rôle dans l'histoire, apparence,
  motivations, où on le rencontre. 2 à 4 phrases, fidèles au livre.
- N'inclus PAS les monstres génériques sans nom (« 3 gobelins », « un loup »).
- Aucun PNJ nommé dans l'extrait → "npcs": [].

Format de réponse :
- Tu réponds UNIQUEMENT par un objet JSON valide, sans markdown ni commentaire autour.
- Schéma EXACT :
  {{"arcs": [{{"name": "...", "description": "...", "type": "LINEAR",
     "chapters": [{{"name": "...", "description": "...", "scenes": [
        {{"name": "...", "description": "...", "player_narration": "...", "gm_notes": "...",
          "rooms": [{{"name": "...", "description": "...", "enemies": "...", "loot": "..."}}]}}
     ]}}]}}
  ],
   "npcs": [{{"name": "...", "description": "..."}}]}}
- Utilise les VRAIS titres du livre pour les noms (pas de paraphrase).
- Si le livre n'est PAS découpé en actes/parties, regroupe tout sous un seul arc nommé "{default_arc}".
- N'invente pas de contenu : tu réorganises et recopies ce qui est présent dans l'extrait.
- Si l'extrait ne contient aucune matière narrative, renvoie {{"arcs": []}}."""

# Bloc TOC injecté quand le PDF a des bookmarks : les morceaux étant traités
# séparément, c'est CE référentiel commun qui garantit que tous nomment les
# mêmes chapitres à l'identique → la fusion par nom du _TreeMerger recolle
# les chapitres coupés au lieu de créer des doublons.
TOC_BLOCK = """

--- STRUCTURE OFFICIELLE DU LIVRE (table des matières du PDF) ---
{toc}
--- FIN DE LA STRUCTURE ---
IMPORTANT : pour nommer les arcs et chapitres, reprends EXACTEMENT les titres
de cette structure (caractère pour caractère). Rattache le contenu de l'extrait
au bon chapitre de la structure, même si son titre n'apparaît pas dans l'extrait."""

# Consolidation finale : le squelette (noms seuls) est minuscule, donc l'appel
# est quasi gratuit comparé aux MAP. Température 0 et consigne CONSERVATRICE :
# ne fusionner que les doublons évidents, jamais des entités distinctes.
CONSOLIDATE_PROMPT = """Voici le squelette d'une arborescence arc → chapitre → scène issue d'une
fusion AUTOMATIQUE de morceaux d'un livre de campagne de jeu de rôle. La fusion par nom exact
peut avoir laissé des QUASI-DOUBLONS : le même chapitre ou la même scène sous deux libellés
légèrement différents (ex: "La Crypte" et "Crypte de Karrak", "3. Salle des gardes" et
"Salle des gardes").

{skeleton}

Identifie UNIQUEMENT les fusions ÉVIDENTES (même entité du livre sous deux noms). Sois
CONSERVATEUR : dans le doute, ne fusionne PAS. Deux lieux/évènements distincts ne doivent
JAMAIS être fusionnés.

Réponds UNIQUEMENT par un objet JSON valide :
{{"chapter_merges": [{{"into": "nom du chapitre à garder", "merge": ["nom à fusionner", ...]}}],
  "scene_merges": [{{"chapter": "nom du chapitre", "into": "nom de la scène à garder",
                     "merge": ["nom à fusionner", ...]}}]}}
S'il n'y a RIEN à fusionner (cas le plus fréquent) : {{"chapter_merges": [], "scene_merges": []}}"""
