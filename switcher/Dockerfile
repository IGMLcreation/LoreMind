# LoreMind channel switcher — sidecar minimal qui orchestre les bascules
# stable <-> beta. Tourne en permanence en attente d'une commande deposee
# dans le volume partage par le Core.
#
# Image volontairement legere (Alpine + docker-cli + bash). Pas de port
# expose, pas de processus reseau : tout passe par fichiers + socket Docker.
FROM alpine:3.20

# docker-cli : pour parler au socket Docker du host
# docker-cli-compose : pour `docker compose pull/up`
# bash : pour les scripts (sh ne suffit pas, on utilise des features bash)
# jq : parsing JSON de la commande
# coreutils : pour `date -u --iso-8601=seconds`
RUN apk add --no-cache \
    docker-cli \
    docker-cli-compose \
    bash \
    jq \
    coreutils

WORKDIR /switcher
COPY watch.sh switch.sh ./
RUN chmod +x watch.sh switch.sh

# Tourne en permanence en mode polling.
ENTRYPOINT ["/switcher/watch.sh"]
