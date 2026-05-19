#!/bin/bash
# watch.sh — boucle principale du switcher.
#
# Surveille /data/command.json (depose par le Core via l'API HTTP) et lance
# switch.sh quand une nouvelle commande arrive. L'ID de la commande sert
# d'idempotence : on ne traite pas deux fois la meme requete.
#
# Le resultat est ecrit dans /data/result.json pour que le Core puisse le
# remonter a l'UI via son endpoint de status.

set -euo pipefail

DATA_DIR="/data"
COMMAND_FILE="${DATA_DIR}/command.json"
RESULT_FILE="${DATA_DIR}/result.json"
LAST_PROCESSED_FILE="${DATA_DIR}/.last-processed-id"

mkdir -p "${DATA_DIR}"

log() {
    echo "[$(date -u --iso-8601=seconds)] $*"
}

# Ecrit un resultat JSON dans result.json — atomique via tmp + mv.
write_result() {
    local status="$1"      # "in-progress" | "success" | "error"
    local channel="$2"     # "stable" | "beta" | ""
    local message="$3"
    local id="$4"

    local tmp
    tmp="$(mktemp -p "${DATA_DIR}" result.XXXXXX)"
    cat > "${tmp}" <<EOF
{
  "id": "${id}",
  "status": "${status}",
  "channel": "${channel}",
  "message": $(printf '%s' "${message}" | jq -Rs .),
  "completedAt": "$(date -u --iso-8601=seconds)"
}
EOF
    mv "${tmp}" "${RESULT_FILE}"
}

log "LoreMind channel switcher started — watching ${COMMAND_FILE}"

# Boucle de polling. Intervalle court (1s) — la charge est negligeable
# (un test de fichier) et l'utilisateur attend une reaction rapide.
while true; do
    if [[ -f "${COMMAND_FILE}" ]]; then
        # Parse la commande. Tolere les JSON malformes : on ignore et on attend.
        if ! id=$(jq -er '.id' "${COMMAND_FILE}" 2>/dev/null); then
            sleep 1
            continue
        fi

        # Idempotence : skip si on a deja traite cet ID.
        last_id=""
        [[ -f "${LAST_PROCESSED_FILE}" ]] && last_id=$(cat "${LAST_PROCESSED_FILE}")
        if [[ "${id}" == "${last_id}" ]]; then
            sleep 1
            continue
        fi

        channel=$(jq -er '.channel' "${COMMAND_FILE}" 2>/dev/null || echo "")

        log "New command received: id=${id} channel=${channel}"
        write_result "in-progress" "${channel}" "Switch en cours..." "${id}"

        # Lance le switch. On capture stdout+stderr et le code de sortie.
        if output=$(/switcher/switch.sh "${channel}" 2>&1); then
            log "Switch SUCCESS for id=${id} channel=${channel}"
            write_result "success" "${channel}" "${output}" "${id}"
        else
            rc=$?
            log "Switch FAILED for id=${id} channel=${channel} rc=${rc}"
            write_result "error" "${channel}" "${output}" "${id}"
        fi

        # Marque l'ID comme traite — empeche les replays.
        echo "${id}" > "${LAST_PROCESSED_FILE}"
    fi
    sleep 1
done
