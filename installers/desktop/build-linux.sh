#!/usr/bin/env bash
#
# Construit l'application de BUREAU Linux de DM Loremind sous forme d'AppImage,
# sans Docker. Equivalent Linux de installers/desktop/build-windows.ps1.
#
# Pipeline complet "local-first" :
#   1. Build du front Angular              (web/  -> web/dist/web)
#   2. Prep du Brain (Python standalone)   (brain/ -> dist-embed-linux : python relocatable + deps + sources)
#   3. Build du Core en fat jar + front    (core/  -> target/*.jar, profil Maven "desktop")
#   4. Assemblage de la charge utile        (jar + brain) dans un dossier d'entree jpackage
#   5. jpackage --type app-image            -> image applicative + JRE embarque
#   6. appimagetool                          -> DM_Loremind-x86_64.AppImage (1 fichier, toutes distros)
#
# L'app resultante se lance d'un double-clic (chmod +x puis ./*.AppImage) : le Core
# demarre en profil Spring "local" (H2 fichier + stockage filesystem) et lance lui-meme
# le Brain en sidecar. Aucune dependance externe (ni Docker, ni Java, ni Python).
#
# PREREQUIS (machine de BUILD Linux uniquement, PAS chez l'utilisateur final) :
#   - JDK 21+ avec jpackage dans le PATH (Temurin OK).
#   - Node.js + npm (build Angular) ; Maven via le wrapper du repo.
#   - curl, tar, et FUSE *ou* (sur CI sans FUSE) on lance appimagetool en
#     --appimage-extract-and-run (gere automatiquement ci-dessous).
#   - Internet (telecharge python-build-standalone + appimagetool).
#
# IMPORTANT : jpackage NE cross-compile PAS. Ce script DOIT tourner sur Linux
# (machine, WSL, ou runner ubuntu-latest). Il ne produira rien d'utile sous Windows.
#
# Projet : DM Loremind — assistant pour Maitres de Jeu de JDR — Licence AGPL-3.0
set -euo pipefail

# --- Args ------------------------------------------------------------------
VERSION=""
SKIP_FRONT=0; SKIP_BRAIN=0; SKIP_JAR=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    --skip-front) SKIP_FRONT=1; shift ;;
    --skip-brain) SKIP_BRAIN=1; shift ;;
    --skip-jar) SKIP_JAR=1; shift ;;
    *) echo "Option inconnue : $1" >&2; exit 2 ;;
  esac
done

step() { printf '\033[36m==> %s\033[0m\n' "$1"; }
ok()   { printf '\033[32m  OK %s\033[0m\n' "$1"; }
err()  { printf '\033[31m  XX %s\033[0m\n' "$1" >&2; }

# --- Chemins ---------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WEB_DIR="$REPO_ROOT/web"
BRAIN_DIR="$REPO_ROOT/brain"
CORE_DIR="$REPO_ROOT/core"
STAGE_DIR="$CORE_DIR/target/dist-input"        # charge utile jpackage
OUT_DIR="$CORE_DIR/target/dist-out"            # image applicative + AppImage produits
BRAIN_EMBED="$BRAIN_DIR/dist-embed-linux"      # staging du brain empaquete (Linux)
ICON_PNG="$SCRIPT_DIR/app-icon.png"            # icone de l'app (jpackage Linux exige un PNG)
APP_NAME="DM Loremind"

# python-build-standalone (Astral) : equivalent Linux du Python embeddable de
# python.org (qui n'existe que sous Windows). Build "install_only" = Python complet
# RELOCATABLE (pip inclus), donc on installe les deps directement dedans.
PY_MINOR="3.12"                                # doit matcher python:3.12 du Docker

# --- Version (numerique) ---------------------------------------------------
if [[ -z "$VERSION" ]]; then
  # Viser la version DU PROJET (artifactId loremind-core), pas le <version> du parent.
  VERSION="$(grep -oPz '<artifactId>loremind-core</artifactId>\s*<version>\K[0-9]+\.[0-9]+\.[0-9]+' \
    "$CORE_DIR/pom.xml" | tr -d '\0' | head -1 || true)"
  [[ -z "$VERSION" ]] && VERSION="0.0.0"
fi

echo
printf '\033[35m============================================================\033[0m\n'
printf '\033[35m DM Loremind - Build AppImage Linux (v%s)\033[0m\n' "$VERSION"
printf '\033[35m============================================================\033[0m\n'

# --- Verif outils ----------------------------------------------------------
command -v jpackage >/dev/null 2>&1 || { err "jpackage introuvable dans le PATH. Installez un JDK 21+ (Temurin)."; exit 1; }

# --- 1. Front Angular ------------------------------------------------------
if [[ $SKIP_FRONT -eq 0 ]]; then
  step "Build du front Angular"
  ( cd "$WEB_DIR"
    [[ -d node_modules ]] || npm ci
    npm run build )
  ok "Front construit (web/dist/web)"
else step "Front : saute (--skip-front)"; fi

# --- 2. Brain (Python standalone relocatable, PAS d'exe gele) --------------
if [[ $SKIP_BRAIN -eq 0 ]]; then
  step "Preparation du Brain (python-build-standalone $PY_MINOR)"
  rm -rf "$BRAIN_EMBED"; mkdir -p "$BRAIN_EMBED"

  # a) Resoudre l'asset install_only linux-gnu pour $PY_MINOR dans la derniere
  #    release PBS (la date du tag change a chaque release -> on la decouvre).
  step "  Resolution de l'archive python-build-standalone"
  # URL EPINGLEE de secours (pas d'API) : version figee comme le build Windows,
  # utilisee si la resolution dynamique echoue (rate limit, API HS, regex sans match).
  PY_PIN='3.12.8'
  PBS_DATE='20241219'
  pbs_fallback="https://github.com/astral-sh/python-build-standalone/releases/download/${PBS_DATE}/cpython-${PY_PIN}+${PBS_DATE}-x86_64-unknown-linux-gnu-install_only.tar.gz"

  # Appel API AUTHENTIFIE si un token est dispo (le runner partage est rate-limite
  # a 60 req/h en anonyme -> 403 ; avec token : 1000/h). Tolerant : tout echec
  # bascule sur l'URL epinglee (pas d'abort silencieux du a set -e).
  auth=()
  [[ -n "${GITHUB_TOKEN:-}" ]] && auth=(-H "Authorization: Bearer ${GITHUB_TOKEN}")
  api_json="$(curl -fsSL "${auth[@]}" \
    https://api.github.com/repos/astral-sh/python-build-standalone/releases/latest 2>/dev/null || true)"
  # NB: dans browser_download_url, le '+' (version+date) est URL-encode en %2B
  # -> on accepte les deux. On vise le x86_64 BASELINE (pas _v2/_v3/_v4, qui
  # exigent un CPU plus recent) et install_only (pas _stripped).
  asset_url="$(printf '%s' "$api_json" \
    | grep -m1 -oE "https://[^\"]*cpython-${PY_MINOR//./\\.}\.[0-9]+(%2B|\+)[0-9]+-x86_64-unknown-linux-gnu-install_only\.tar\.gz" \
    || true)"
  if [[ -z "$asset_url" ]]; then
    echo "  (resolution API indisponible -> URL epinglee de secours)"
    asset_url="$pbs_fallback"
  fi
  echo "  Telechargement $asset_url"
  curl -fsSL "$asset_url" -o "$BRAIN_EMBED/python.tar.gz"
  # L'archive install_only s'extrait en un dossier 'python/' (bin/python3, lib/...).
  tar -xzf "$BRAIN_EMBED/python.tar.gz" -C "$BRAIN_EMBED"
  rm -f "$BRAIN_EMBED/python.tar.gz"
  PYBIN="$BRAIN_EMBED/python/bin/python3"
  [[ -x "$PYBIN" ]] || { err "python3 introuvable apres extraction ($PYBIN)."; exit 1; }

  # b) Installer les deps DANS le python standalone (site-packages interne).
  #    Python complet => pip fonctionne directement, pas de bricolage ._pth/--target.
  "$PYBIN" -m pip install --upgrade pip >/dev/null
  "$PYBIN" -m pip install -r "$BRAIN_DIR/requirements.txt"

  # c) Copier les sources du Brain + le point d'entree.
  cp -r "$BRAIN_DIR/app" "$BRAIN_EMBED/app"
  cp "$BRAIN_DIR/run_local.py" "$BRAIN_EMBED/run_local.py"

  # d) OCR (Tesseract) : pas bundle pour l'instant sous Linux (libs partagees
  #    lourdes a embarquer proprement). Degradation gracieuse : si l'utilisateur a
  #    'tesseract-ocr' (apt/dnf), pytesseract le trouve sur le PATH et l'OCR des
  #    scans marche ; sinon PDF born-digital OK, scans signales. Cf. run_local.py.
  ok "Brain prepare (brain/dist-embed-linux : python standalone + deps + sources)"
else step "Brain : saute (--skip-brain)"; fi

# --- 3. Core (fat jar avec front embarque) ---------------------------------
if [[ $SKIP_JAR -eq 0 ]]; then
  step "Build du Core (fat jar, profil desktop)"
  ( cd "$CORE_DIR"
    chmod +x ./mvnw
    # -Pdesktop : copie web/dist/web dans le jar (classpath:/static).
    ./mvnw -q -Pdesktop -DskipTests clean package )
  ok "Core construit (core/target)"
else step "Core : saute (--skip-jar)"; fi

# --- 4. Assemblage de la charge utile --------------------------------------
step "Assemblage de la charge utile jpackage"
rm -rf "$STAGE_DIR"; mkdir -p "$STAGE_DIR"

# Le jar repackage Spring Boot (executable) — on ignore le *.jar.original.
jar="$(find "$CORE_DIR/target" -maxdepth 1 -name 'loremind-core-*.jar' ! -name '*.original' | head -1)"
[[ -z "$jar" ]] && { err "Jar introuvable dans core/target. Relancez sans --skip-jar."; exit 1; }
cp "$jar" "$STAGE_DIR/loremind-core.jar"

# Le Brain (python standalone + deps + sources) -> stage/brain/
[[ -d "$BRAIN_EMBED/python" ]] || { err "Brain introuvable (brain/dist-embed-linux). Relancez sans --skip-brain."; exit 1; }
mkdir -p "$STAGE_DIR/brain"
cp -r "$BRAIN_EMBED/." "$STAGE_DIR/brain/"
ok "Charge utile prete ($STAGE_DIR)"

# --- 5. jpackage -> app-image ----------------------------------------------
step "Generation de l'image applicative (app-image) via jpackage"
[[ -f "$ICON_PNG" ]] || { err "Icone manquante : $ICON_PNG (PNG requis sous Linux)."; exit 1; }
rm -rf "$OUT_DIR"; mkdir -p "$OUT_DIR"

# $APPDIR : substitue par jpackage AU LANCEMENT par le dossier applicatif
# (lib/app), qui contient le jar ET le dossier brain copie depuis --input.
# Le Brain se lance via python3 + run_local.py. brain.sidecar.command est une
# LISTE : les deux chemins separes par une virgule (aucun chemin Linux n'en
# contient) sont bindes en List<String> par Spring. NB: $APPDIR doit rester
# LITTERAL ici (quote simple) — c'est jpackage, pas bash, qui le resout.
BRAIN_CMD='$APPDIR/brain/python/bin/python3,$APPDIR/brain/run_local.py'

jpackage \
  --type app-image \
  --name "$APP_NAME" \
  --app-version "$VERSION" \
  --vendor 'IGML Creation' \
  --icon "$ICON_PNG" \
  --input "$STAGE_DIR" \
  --main-jar loremind-core.jar \
  --main-class org.springframework.boot.loader.launch.JarLauncher \
  --dest "$OUT_DIR" \
  --java-options '-Dspring.profiles.active=local' \
  --java-options "-Dbrain.sidecar.command=$BRAIN_CMD"

APPIMG_SRC="$OUT_DIR/$APP_NAME"   # dossier produit par jpackage (avec espace)
[[ -d "$APPIMG_SRC" ]] || { err "app-image jpackage introuvable ($APPIMG_SRC)."; exit 1; }
ok "Image applicative prete ($APPIMG_SRC)"

# --- 6. AppImage via appimagetool ------------------------------------------
step "Empaquetage AppImage (toutes distros)"
APPDIR="$OUT_DIR/AppDir"
rm -rf "$APPDIR"; mkdir -p "$APPDIR/usr"

# a) L'app-image jpackage va sous AppDir/usr/ (le lanceur bin/* resout lib/ en
#    relatif via ../lib -> reste coherent).
cp -r "$APPIMG_SRC/." "$APPDIR/usr/"

# b) Icone a la racine de l'AppDir (nom = Icon= du .desktop, SANS extension).
cp "$ICON_PNG" "$APPDIR/dm-loremind.png"
mkdir -p "$APPDIR/usr/share/icons/hicolor/256x256/apps"
cp "$ICON_PNG" "$APPDIR/usr/share/icons/hicolor/256x256/apps/dm-loremind.png"

# c) Fichier .desktop (a la racine + copie standard).
cat > "$APPDIR/dm-loremind.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=DM Loremind
Comment=Assistant pour Maitres de Jeu de JDR
Exec=DM Loremind
Icon=dm-loremind
Categories=Game;Utility;
Terminal=false
EOF
mkdir -p "$APPDIR/usr/share/applications"
cp "$APPDIR/dm-loremind.desktop" "$APPDIR/usr/share/applications/dm-loremind.desktop"

# d) AppRun : point d'entree de l'AppImage -> lance le binaire jpackage.
cat > "$APPDIR/AppRun" <<'EOF'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/DM Loremind" "$@"
EOF
chmod +x "$APPDIR/AppRun"

# e) appimagetool (telecharge si absent). --appimage-extract-and-run : evite
#    d'exiger FUSE (absent des runners CI).
TOOL="$OUT_DIR/appimagetool-x86_64.AppImage"
if [[ ! -x "$TOOL" ]]; then
  curl -fsSL -o "$TOOL" \
    https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage
  chmod +x "$TOOL"
fi

# APPIMAGE_EXTRACT_AND_RUN : permet d'EXECUTER l'AppImage appimagetool SANS FUSE
# (absent des runners CI) — il se self-extrait au lieu de se monter via fuse.
OUT_FILE="$OUT_DIR/DM_Loremind-${VERSION}-x86_64.AppImage"
ARCH=x86_64 APPIMAGE_EXTRACT_AND_RUN=1 "$TOOL" --appimage-extract-and-run "$APPDIR" "$OUT_FILE"
chmod +x "$OUT_FILE"

echo
printf '\033[32m============================================================\033[0m\n'
printf '\033[32m AppImage genere !\033[0m\n'
printf '\033[32m   %s\033[0m\n' "$OUT_FILE"
printf '\033[32m============================================================\033[0m\n'
