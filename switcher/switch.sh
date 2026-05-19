#!/bin/bash
# switch.sh — execute le switch de canal pour LoreMind.
#
# Usage interne (appele par watch.sh) :
#   ./switch.sh stable
#   ./switch.sh beta
#
# Ce que ca fait, dans l'ordre :
#   1. Valide l'argument (stable|beta uniquement, rien d'autre — defense
#      contre command injection si le Core etait compromis)
#   2. Sed la ligne IMAGE_NAMESPACE= du .env du host pour basculer le prefixe
#   3. docker compose pull (recupere les nouvelles images du canal cible)
#   4. docker compose up -d (recree core/brain/web avec les nouvelles images)
#
# Le switcher LUI-MEME n'est PAS dans IMAGE_NAMESPACE — il survit au switch
# sans interruption (cf. docker-compose.yml).

set -euo pipefail

CHANNEL="${1:-}"

# --- Validation stricte -----------------------------------------------------
# Aucune autre valeur acceptee. Pas d'echappement, pas de slash, rien.
# C'est le filet de securite si le JSON depose dans /data/command.json
# contenait un payload exotique (Core compromis = on ne laisse PAS
# executer du code arbitraire sur l'hote).
case "${CHANNEL}" in
    stable|beta) ;;
    *)
        echo "Channel invalide: '${CHANNEL}' (attendu: stable|beta)" >&2
        exit 2
        ;;
esac

# --- Configuration ---------------------------------------------------------
# Repertoire monte depuis l'hote contenant docker-compose.yml + .env
COMPOSE_DIR="${COMPOSE_DIR:-/compose}"
ENV_FILE="${COMPOSE_DIR}/.env"

if [[ ! -f "${ENV_FILE}" ]]; then
    echo "Fichier .env introuvable dans ${COMPOSE_DIR}" >&2
    exit 3
fi
if [[ ! -f "${COMPOSE_DIR}/docker-compose.yml" ]]; then
    echo "docker-compose.yml introuvable dans ${COMPOSE_DIR}" >&2
    exit 3
fi

# --- Detection du nom de projet compose ------------------------------------
# Pour eviter que le switcher cree un projet PARALLELE (cas ou COMPOSE_PROJECT_NAME
# ne correspond pas au nom du projet sous lequel les containers tournent),
# on lit le label compose du container core en cours d'execution.
# Ce label est ecrit par docker compose au moment du `up -d` initial — c'est
# la source de verite.
PROJECT_NAME=$(docker inspect loremind-core \
    --format '{{ index .Config.Labels "com.docker.compose.project" }}' \
    2>/dev/null || echo "")
if [[ -z "${PROJECT_NAME}" ]]; then
    # Fallback : env var ou defaut. Ne devrait pas arriver en prod
    # (loremind-core tourne forcement quand l'UI declenche un switch).
    PROJECT_NAME="${COMPOSE_PROJECT_NAME:-loremind}"
    echo "Warning: nom de projet auto-detecte impossible, fallback sur '${PROJECT_NAME}'" >&2
fi
export COMPOSE_PROJECT_NAME="${PROJECT_NAME}"
echo "→ Projet compose cible: ${PROJECT_NAME}"

# --- Mapping canal -> namespace --------------------------------------------
# Le slash final est important : il est concatene avec le suffixe image
# (core/brain/web) dans le docker-compose.yml.
case "${CHANNEL}" in
    stable) NAMESPACE="igmlcreation/loremind-" ;;
    beta)   NAMESPACE="igmlcreation/loremind-beta-" ;;
esac

# --- Etape 1 : sed le .env -------------------------------------------------
# On veut REMPLACER une ligne existante IMAGE_NAMESPACE=... ou AJOUTER
# si absente. Cas typique : .env utilisateur peut avoir cette ligne ou non.
#
# Sed -i avec un pattern qui matche la ligne entiere. Si pas de match,
# on append.
echo "→ Mise a jour de IMAGE_NAMESPACE dans .env (canal: ${CHANNEL})"
if grep -q '^IMAGE_NAMESPACE=' "${ENV_FILE}"; then
    # Sur Alpine, sed -i sans backup. Le pattern d'echappement '/' dans
    # le namespace impose un delimiter alternatif (|).
    sed -i "s|^IMAGE_NAMESPACE=.*|IMAGE_NAMESPACE=${NAMESPACE}|" "${ENV_FILE}"
else
    # Ligne absente → on l'ajoute en fin de fichier avec un commentaire.
    {
        echo ""
        echo "# Ajoute automatiquement par le switcher de canal LoreMind."
        echo "IMAGE_NAMESPACE=${NAMESPACE}"
    } >> "${ENV_FILE}"
fi

# --- Etape 2 : docker compose pull -----------------------------------------
echo "→ Pull des nouvelles images (${NAMESPACE}*)"
# --no-deps inutile ici : pull n'a pas de notion de deps.
# --policy missing eviterait de re-puller si deja la, mais on VEUT puller
# pour avoir la derniere version disponible — c'est le but du switch.
cd "${COMPOSE_DIR}"
docker compose pull core brain web

# --- Etape 3 : recreate les containers avec les nouvelles images -----------
# On cible explicitement core/brain/web — pas le switcher (qui s'auto-tuerait
# au milieu de la commande), pas postgres/minio (pas de changement d'image).
# --no-deps : ne pas re-recreer postgres/minio comme effet de bord.
echo "→ Recreation des containers avec les nouvelles images"
docker compose up -d --no-deps core brain web

echo ""
echo "Switch vers le canal ${CHANNEL} termine avec succes."
echo "Containers core/brain/web recrees avec ${NAMESPACE}*."
