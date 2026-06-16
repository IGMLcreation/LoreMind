"""Prompt de réécriture en question autonome (cf. query_rewrite.py).

Attend `.format(conversation=...)`.
"""

REWRITE_PROMPT = """Voici la fin d'une conversation entre un Maître de Jeu et son assistant.
Réécris le DERNIER message de l'utilisateur en une question AUTONOME et complète :
remplace les pronoms et références implicites (« il », « ses », « ce lieu », « et pour
les autres ? ») par ce qu'ils désignent dans la conversation.

Règles :
- Réponds UNIQUEMENT par la question réécrite, sans guillemets ni préfixe.
- Conserve la langue et l'intention d'origine. N'ajoute RIEN qui n'est pas demandé.
- Si le dernier message est déjà autonome, recopie-le tel quel.

--- CONVERSATION ---
{conversation}
--- FIN ---

Question autonome :"""
