"""Point d'entree LOCAL du Brain (hors Docker).

Lance le serveur uvicorn sur 127.0.0.1:8000 — l'equivalent autonome de la
commande Docker `uvicorn app.main:app --host 0.0.0.0 --port 8000`, mais en
n'ecoutant QUE sur la boucle locale (mono-utilisateur, jamais expose au reseau).

Empaquete avec le Python *embeddable* officiel (signe par la PSF) dans
l'application de bureau : on evite ainsi tout executable "gele" type PyInstaller
que les antivirus prennent souvent pour un trojan (bootloader packe).
Le Core le lance via :  python\\python.exe run_local.py

On insere le dossier de CE fichier dans sys.path pour que le package `app`
soit importable quel que soit le repertoire de travail (le Core fixe le cwd
ailleurs, sous ~/.loremind/brain, pour y ecrire le dossier data/).
"""
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)

# OCR : si un Tesseract est bundlé à côté (mode desktop), on y pointe pytesseract
# AVANT que l'app n'importe le pdf_extractor (qui détecte la version au chargement).
# tessdata (fra+eng) est embarqué dans tesseract/tessdata. Sans ce bloc, l'OCR
# reste désactivé en dégradation gracieuse (PDF born-digital OK, scans signalés).
_TESS = os.path.join(_HERE, "tesseract", "tesseract.exe")
if os.path.exists(_TESS):
    os.environ.setdefault("TESSDATA_PREFIX", os.path.join(_HERE, "tesseract"))
    try:
        import pytesseract
        pytesseract.pytesseract.tesseract_cmd = _TESS
    except ImportError:
        pass

import uvicorn  # noqa: E402

from app.main import app  # noqa: E402

if __name__ == "__main__":
    # host 127.0.0.1 : accessible uniquement depuis le Core sur la meme machine.
    uvicorn.run(app, host="127.0.0.1", port=8000, log_level="info")
