"""Adapter web (architecture hexagonale) : routers FastAPI, DTOs et factories DI.

C'est la FRONTIÈRE HTTP du Brain : validation Pydantic, mapping DTO ↔ domaine,
traduction des erreurs domaine → HTTP. Aucune logique métier ici.
"""
