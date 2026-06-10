"""Adapter d'extraction de texte PDF — implémente le port PdfTextExtractor.

Stratégie HYBRIDE auto :
  1. On tente d'abord l'extraction de la couche texte (PyMuPDF). Les PDF
     "born-digital" (livres de règles officiels type Nimble, faits dans
     InDesign/Affinity : très graphiques mais avec une vraie couche texte)
     passent par là → rapide, fidèle, AUCUN OCR.
  2. Si une page ne rend (quasi) aucun texte → c'est probablement une image
     (scan ou page 100% illustrée). On rasterise la page et on la passe à
     Tesseract (OCR). Gère donc aussi les scans purs et les PDF mixtes.

Tesseract est un binaire SYSTÈME (installé dans l'image Docker du Brain). S'il
est absent (ex: exécution locale Windows sans install), l'OCR est désactivé
proprement : les pages-images ressortent vides mais l'extraction ne plante pas,
et le diagnostic le signale (used_ocr reste False, texte vide).
"""
from __future__ import annotations

import logging

import pymupdf as fitz  # PyMuPDF — on importe par le nom canonique `pymupdf`
# (et NON `import fitz`) pour éviter la collision avec le faux paquet PyPI "fitz"
# qui échoue sur `from frontend import *`.

from app.domain.models import ExtractedDocument, ExtractedPage, TocEntry
from app.domain.ports import PdfExtractionError

logger = logging.getLogger(__name__)

# En dessous de ce nombre de caractères "significatifs" sur une page, on
# considère qu'il n'y a pas de couche texte exploitable → repli OCR.
_MIN_TEXT_CHARS = 20

# DPI de rasterisation avant OCR. 300 = bon compromis qualité/vitesse pour du
# texte de livre. Plus haut = plus lent et plus gourmand en mémoire.
_OCR_DPI = 300

# Langues Tesseract : français + anglais (la plupart des règles de JDR FR ont
# des termes anglais résiduels). Doivent être installées dans l'image Docker
# (tesseract-ocr-fra, tesseract-ocr-eng).
_OCR_LANGS = "fra+eng"


class PyMuPdfTextExtractor:
    """Extracteur PDF basé sur PyMuPDF, avec repli OCR Tesseract optionnel."""

    def __init__(self) -> None:
        # On détecte la disponibilité de l'OCR une seule fois (le binaire
        # Tesseract ne va pas apparaître/disparaître en cours d'exécution).
        self._ocr_available = self._detect_ocr()

    @staticmethod
    def _detect_ocr() -> bool:
        """True si pytesseract + le binaire Tesseract sont disponibles."""
        try:
            import pytesseract

            pytesseract.get_tesseract_version()
            return True
        except Exception as exc:  # ImportError, TesseractNotFoundError, etc.
            logger.warning(
                "OCR indisponible (Tesseract non installé ?) : %s. "
                "Les pages sans couche texte ressortiront vides.",
                exc,
            )
            return False

    def extract(self, pdf_bytes: bytes) -> ExtractedDocument:
        try:
            doc = fitz.open(stream=pdf_bytes, filetype="pdf")
        except Exception as exc:
            raise PdfExtractionError(f"PDF illisible ou corrompu : {exc}") from exc

        pages: list[ExtractedPage] = []
        toc: list[TocEntry] = []
        try:
            # Bookmarks/outline du PDF : structure officielle du livre, gratuite
            # (pas d'appel LLM). Sert de squelette de référence aux imports.
            try:
                for level, title, page_no in doc.get_toc(simple=True) or []:
                    title = str(title or "").strip()
                    if title:
                        toc.append(TocEntry(level=int(level), title=title, page=int(page_no)))
            except Exception as exc:  # noqa: BLE001 — TOC best-effort, jamais bloquante
                logger.warning("Lecture de la table des matières impossible : %s", exc)

            for index, page in enumerate(doc):
                text = (page.get_text() or "").strip()
                used_ocr = False
                if len(text) < _MIN_TEXT_CHARS and self._ocr_available:
                    ocr_text = self._ocr_page(page)
                    if ocr_text.strip():
                        text = ocr_text.strip()
                        used_ocr = True
                pages.append(ExtractedPage(index=index, text=text, used_ocr=used_ocr))
        finally:
            doc.close()

        return ExtractedDocument(pages=pages, toc=toc)

    @staticmethod
    def _ocr_page(page: "fitz.Page") -> str:
        """Rasterise une page et lui applique l'OCR Tesseract."""
        import pytesseract
        from PIL import Image

        pix = page.get_pixmap(dpi=_OCR_DPI)
        img = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
        try:
            return pytesseract.image_to_string(img, lang=_OCR_LANGS)
        except Exception as exc:
            logger.warning("Échec OCR sur la page %s : %s", page.number, exc)
            return ""
