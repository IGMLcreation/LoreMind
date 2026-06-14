"""CLI de test pour l'import de règles PDF — boucle de feedback rapide.

But : tester l'extraction + la structuration en sections sur un VRAI PDF
(ex: livre Nimble), SANS passer par HTTP, le Core ou l'UI.

Usage (depuis le dossier brain/, venv activé) :
    python scripts/test_import_rules.py "chemin/vers/regles.pdf"

Le provider LLM utilisé est celui configuré (.env + overrides de l'écran
Paramètres) — donc 1min.ai si tu l'as sélectionné. Le script :
  1. extrait le texte (et dit, page par page, si l'OCR s'est déclenché),
  2. découpe + structure via le LLM,
  3. affiche un résumé des sections,
  4. écrit le markdown complet dans "<pdf>.rules.md" à côté du PDF.
"""
from __future__ import annotations

import asyncio
import logging
import sys
from pathlib import Path

# Permet `import app...` quel que soit le cwd : on ajoute la racine brain/.
_BRAIN_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(_BRAIN_ROOT))

from app.application.import_rules import ImportRulesUseCase  # noqa: E402
from app.core.config import get_settings  # noqa: E402
from app.domain.ports import LLMProvider, LLMProviderError, PdfExtractionError  # noqa: E402
from app.infrastructure.ollama_adapter import OllamaLLMProvider  # noqa: E402
from app.infrastructure.onemin_adapter import OneMinAiLLMProvider  # noqa: E402
from app.infrastructure.pdf_extractor import PyMuPdfTextExtractor  # noqa: E402


def _build_provider() -> LLMProvider:
    """Réplique la factory de main.py (choix provider selon les settings)."""
    settings = get_settings()
    print(f"→ Provider LLM : {settings.llm_provider} "
          f"(modèle : {settings.onemin_model if settings.llm_provider == 'onemin' else settings.llm_model})")
    if settings.llm_provider == "onemin":
        return OneMinAiLLMProvider(settings)
    return OllamaLLMProvider(settings)


async def _run(pdf_path: Path) -> None:
    pdf_bytes = pdf_path.read_bytes()
    print(f"→ PDF chargé : {pdf_path.name} ({len(pdf_bytes) // 1024} Ko)\n")

    extractor = PyMuPdfTextExtractor()

    # 1. Extraction seule d'abord, pour le diagnostic page/OCR avant tout LLM.
    try:
        doc = extractor.extract(pdf_bytes)
    except PdfExtractionError as exc:
        print(f"✗ Extraction impossible : {exc}")
        return

    print(f"=== Extraction : {doc.page_count} page(s), "
          f"{doc.ocr_page_count} via OCR, "
          f"{len(doc.full_text)} caractères ===")
    if doc.ocr_page_count == 0:
        print("  → PDF born-digital détecté (couche texte présente, OCR non nécessaire).")
    else:
        print(f"  → {doc.ocr_page_count} page(s) sans couche texte : OCR déclenché.")
    if not doc.full_text.strip():
        print("\n✗ Aucun texte extrait. Si le PDF est un scan, vérifie que Tesseract "
              "est installé (sinon l'OCR est désactivé).")
        return

    # 2. Structuration via le LLM (réutilise l'extraction déjà faite indirectement :
    #    le use case ré-extrait, coût négligeable vs l'appel LLM).
    print("\n=== Structuration via LLM (peut prendre un moment selon le nombre de morceaux)… ===")
    use_case = ImportRulesUseCase(llm=_build_provider(), extractor=extractor)
    try:
        result = await use_case.execute(pdf_bytes)
    except LLMProviderError as exc:
        print(f"✗ Échec LLM : {exc}")
        return

    if not result.sections:
        print("\n✗ Aucune section proposée (le modèle n'a rien renvoyé d'exploitable).")
        return

    print(f"\n=== {len(result.sections)} section(s) proposée(s) ===")
    for title, content in result.sections.items():
        preview = content.strip().replace("\n", " ")[:90]
        print(f"  • {title}  ({len(content)} car.)  — {preview}…")

    out_path = pdf_path.with_suffix(".rules.md")
    out_path.write_text(result.to_markdown(), encoding="utf-8")
    print(f"\n✓ Markdown complet écrit dans : {out_path}")


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s — %(message)s")
    if len(sys.argv) != 2:
        print("Usage : python scripts/test_import_rules.py \"chemin/vers/regles.pdf\"")
        sys.exit(1)
    pdf_path = Path(sys.argv[1]).expanduser()
    if not pdf_path.is_file():
        print(f"✗ Fichier introuvable : {pdf_path}")
        sys.exit(1)
    asyncio.run(_run(pdf_path))


if __name__ == "__main__":
    main()
