"""Prompt de reranking LLM des passages RAG (cf. rerank.py).

Attend `.format(question=..., passages=..., count=...)`.
"""

RERANK_PROMPT = """Tu évalues la PERTINENCE d'extraits d'un document pour répondre à une question.
Note chaque extrait de 0 (sans rapport) à 10 (répond directement), indépendamment des autres.

QUESTION : {question}

{passages}

Réponds UNIQUEMENT par un objet JSON : {{"scores": [note_extrait_1, note_extrait_2, ...]}}
Le tableau doit contenir EXACTEMENT {count} notes, dans l'ordre des extraits."""
