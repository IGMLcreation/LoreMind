"""Prompts LLM, regroupés hors de la logique des use cases.

Un prompt est du code (couplé à son schéma de sortie et à son parsing), mais
le mêler à la logique d'orchestration rend les use cases illisibles. Ce package
isole le TEXTE des prompts : un module par domaine fonctionnel, miroir des
modules de `app.application` / des routers.

Convention : les use cases importent depuis ici et gardent la logique (chunking,
parsing, fusion, schémas de sortie JSON, températures, sentinelles). Les prompts
restent en français (langue de travail) — seule la langue de SORTIE est
paramétrée, cf. `app.core.language`.
"""
