# Demo publique LoreMind

Deploiement d'une instance de demo ephemere pour `loremind-demo.igmlcreation.fr`.

## Principe

Chaque visiteur recoit un environnement isole spawne a la volee, detruit apres
un court delai d'inactivite. Les donnees ne sont jamais persistees.

Le mode demo (variable d'env `DEMO_MODE=true` sur le core) masque les ecrans
de configuration qui n'ont pas de sens en vitrine.

## Deploiement

Prerequis :
- Reseau Traefik existant cote host
- Images `core` et `brain` pushees au registre

```bash
cp .env.example .env
# Ajuster .env
docker compose -f docker-compose.infra.yml up -d --build
```

Premier build : 5-10 min. Suivants : incrementaux.

## Mise a jour

```bash
docker compose -f docker-compose.infra.yml pull
docker compose -f docker-compose.infra.yml up -d --build
```

Les sessions en cours sont tuees au redemarrage.

## Observations

- `docker logs loremind-demo-orchestrator -f`
- `docker ps --filter "name=demo-"`

## Desactiver

```bash
docker compose -f docker-compose.infra.yml down
docker ps -q --filter "name=demo-" | xargs -r docker rm -f
```
