# syntax=docker/dockerfile:1.6
# Build context attendu : racine du repo LoreMind.
# Appele depuis demo/docker-compose.infra.yml avec context: ../

# --- Etage 1 : build Angular statique ---
FROM node:20-alpine AS web-build
WORKDIR /build
COPY web/package*.json ./
RUN npm ci
COPY web/ .
RUN npm run build -- --configuration production

# --- Etage 2 : build orchestrateur Go ---
FROM golang:1.22-alpine AS go-build
WORKDIR /src
COPY demo/orchestrator/ ./
# go mod tidy resout le go.sum au build pour eviter d'avoir a le committer.
RUN go mod tidy && CGO_ENABLED=0 go build -o /orchestrator .

# --- Etage final : runtime minimal ---
FROM alpine:3.20
RUN apk add --no-cache ca-certificates
WORKDIR /app
COPY --from=go-build /orchestrator /app/orchestrator
COPY --from=web-build /build/dist/web /app/static
COPY demo/orchestrator/preparing.html /app/preparing.html
EXPOSE 80
ENV STATIC_DIR=/app/static \
    PREPARING_PAGE=/app/preparing.html
ENTRYPOINT ["/app/orchestrator"]
