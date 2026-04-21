# Installation de LoreMindMJ

## Prerequis

- **Docker Desktop** ([Windows](https://www.docker.com/products/docker-desktop/) / [Mac](https://www.docker.com/products/docker-desktop/))
  ou **Docker Engine + Compose v2** (Linux).
- (Optionnel) **[Ollama](https://ollama.com/)** si tu veux un LLM local.
  Sinon, une cle API [1min.ai](https://1min.ai) suffit.

## Installation (5 minutes)

1. Telecharge `docker-compose.yml` et `.env.example` depuis la [derniere release](https://git.igmlcreation.fr/ietm64/LoreMindMJ/releases) dans un dossier a toi.

2. Renomme `.env.example` en `.env` et ouvre-le dans un editeur texte. Trois variables sont **obligatoires** :
   - `POSTGRES_PASSWORD` : mot de passe de la base (choisis-en un).
   - `ADMIN_PASSWORD` : protege l'ecran Parametres de l'appli. Tu le taperas dans une popup du navigateur.
   - `BRAIN_INTERNAL_SECRET` : secret interne partage entre les services. Genere une valeur aleatoire :
     ```
     openssl rand -hex 32
     ```
     (Sous Windows sans openssl : utilise un generateur en ligne type "random hex string 64 chars".)

   Sans ces trois variables, `docker compose up` refusera de demarrer — c'est volontaire pour eviter un deploiement non-securise par defaut.

3. Dans un terminal, place-toi dans le dossier et lance :
   ```
   docker compose up -d
   ```
   Le premier demarrage telecharge les images (~500 Mo) et initialise la base. Compte 1-2 minutes.

4. Ouvre http://localhost:8081 dans ton navigateur. Bon jeu !

## Mise a jour

```
docker compose pull
docker compose up -d
```

Les donnees (base Postgres, images MinIO, settings Brain) sont dans des volumes Docker et survivent aux mises a jour.

## LLM : Ollama ou 1min.ai ?

**Ollama (local, gratuit)** — Edite `.env` :
```
LLM_PROVIDER=ollama
LLM_MODEL=gemma4:26b
```
Telecharge le modele au prealable : `ollama pull gemma4:26b`.

**1min.ai (cloud, paye)** — Edite `.env` :
```
LLM_PROVIDER=onemin
ONEMIN_API_KEY=sk-...
ONEMIN_MODEL=open-mistral-nemo
```

Tu peux aussi changer tout ca a chaud depuis l'ecran Parametres de l'appli.

## Problemes frequents

- **Port 8081 deja pris** : change `WEB_PORT=8082` (ou autre) dans `.env`.
- **Ollama injoignable** : verifie qu'Ollama tourne (`ollama serve`) et que le modele est bien telecharge.
- **"set ADMIN_PASSWORD in .env" / "set BRAIN_INTERNAL_SECRET in .env"** au lancement : tu as oublie une des variables obligatoires de l'etape 2.
- **Popup "Ce site vous demande de vous connecter" sur l'ecran Parametres** : c'est normal. Utilise `admin` (ou ce que tu as mis dans `ADMIN_USERNAME`) et ton `ADMIN_PASSWORD`.
- **Tout casser et repartir de zero** : `docker compose down -v` supprime les volumes (attention, perte de donnees).

## Sauvegarde

Les donnees sont dans les volumes Docker : `loremindmj_postgres-data`, `loremindmj_minio-data`, `loremindmj_brain-data`.

Sauvegarde rapide de la base :
```
docker compose exec postgres pg_dump -U loremind loremind > backup.sql
```
