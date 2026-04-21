# Installation de LoreMindMJ

Ce document decrit la procedure d'installation de LoreMindMJ. Temps estime :
5 a 10 minutes selon la qualite de la connexion reseau.

## 1. Prerequis

- **Docker Desktop** ([Windows](https://www.docker.com/products/docker-desktop/) /
  [Mac](https://www.docker.com/products/docker-desktop/)) ou
  **Docker Engine + Compose v2** (Linux). Verification :
  ```
  docker --version
  docker compose version
  ```
  Compose v2 est requis : la commande est `docker compose`, non `docker-compose`.

- **Un fournisseur LLM**, au choix :
  - **[Ollama](https://ollama.com/)** installe sur la machine hote (gratuit,
    local, necessite environ 6 Go de RAM libre pour les modeles recommandes).
  - **Une cle API [1min.ai](https://1min.ai)** (hebergement cloud, facturation
    a l'usage, aucune installation supplementaire requise).

- Environ **2 Go d'espace disque** pour les images Docker, auxquels s'ajoute
  la taille des modeles Ollama si l'option locale est retenue.

## 2. Recuperation des fichiers

Telecharger les deux fichiers suivants depuis la
[derniere release](https://git.igmlcreation.fr/ietm64/LoreMindMJ/releases) et
les placer dans un dossier dedie (par exemple `~/loremind/` ou
`C:\Programs\loremind\`) :

- `docker-compose.yml`
- `.env.example`

Le code source n'est pas necessaire : les images sont pre-construites et
publiees sur le registry Gitea `git.igmlcreation.fr` (non Docker Hub). Le
premier `docker compose pull` les telechargera automatiquement.

## 3. Configuration du fichier `.env`

Renommer `.env.example` en `.env` et l'ouvrir dans un editeur de texte. **Trois
variables sont obligatoires** ; sans elles, `docker compose up` refusera de
demarrer. Ce comportement est volontaire afin d'eviter tout deploiement
non-securise par defaut.

### `POSTGRES_PASSWORD`

Mot de passe de la base de donnees PostgreSQL. Choisir une valeur robuste.
Seuls les conteneurs utilisent cette valeur : il n'est pas necessaire de la
memoriser au-dela du fichier `.env`.

### `ADMIN_PASSWORD`

Protege l'ecran **Parametres** de l'application via HTTP Basic. Cette valeur
sera demandee par le navigateur lors de toute modification de la configuration
(changement de modele LLM, saisie de cle API, etc.). Le nom d'utilisateur par
defaut est `admin`, modifiable via la variable `ADMIN_USERNAME`.

### `BRAIN_INTERNAL_SECRET`

Secret partage entre le service Java (`core`) et le service Python (`brain`).
Empeche toute requete externe d'atteindre directement le service Brain.
Generer une valeur aleatoire de 64 caracteres hexadecimaux :

```
openssl rand -hex 32
```

Sous Windows sans `openssl`, utiliser PowerShell :

```powershell
-join ((48..57) + (97..102) | Get-Random -Count 64 | % {[char]$_})
```

### Variables optionnelles

- `WEB_PORT` (defaut `8081`) : port d'ecoute de l'interface web.
- `ADMIN_USERNAME` (defaut `admin`) : identifiant de la popup Parametres.
- `LLM_PROVIDER` (defaut `ollama`) : choix du fournisseur LLM (voir
  section 5).

Les autres variables (`MINIO_USER`/`MINIO_PASSWORD`, `POSTGRES_DB`,
`POSTGRES_USER`) disposent de valeurs par defaut adaptees a un deploiement
personnel et peuvent etre conservees en l'etat.

## 4. Lancement de la stack

Depuis le dossier contenant `docker-compose.yml` et `.env` :

```
docker compose up -d
```

Le premier demarrage telecharge les images (environ 1 a 2 Go au total) et
initialise la base. Compter 2 a 5 minutes selon la qualite de la connexion.
La progression peut etre suivie via :

```
docker compose logs -f
```

(`Ctrl+C` pour quitter l'affichage ; les services continuent de fonctionner
en arriere-plan.)

Une fois les services en etat `healthy`, ouvrir **http://localhost:8081**
dans un navigateur.

### Verification du fonctionnement

```
docker compose ps
```

Cinq conteneurs doivent apparaitre en etat `Up` ou `healthy` :
`loremind-postgres`, `loremind-minio`, `loremind-core`, `loremind-brain`,
`loremind-web`. Le conteneur `loremind-minio-init` s'arrete automatiquement
apres creation du bucket d'images : ce comportement est normal.

## 5. Configuration du fournisseur LLM

### Ollama (local, gratuit)

Installer Ollama sur la machine hote (pas dans Docker), puis telecharger un
modele :

```
ollama pull gemma4:26b
```

Dans `.env` :

```
LLM_PROVIDER=ollama
LLM_MODEL=gemma4:26b
OLLAMA_BASE_URL=http://host.docker.internal:11434
```

L'adresse `host.docker.internal` permet au conteneur `brain` d'atteindre
Ollama sur la machine hote. Cette resolution est native sous Docker Desktop
(Mac / Windows). Sous Linux, le fichier `docker-compose.yml` declare un
`extra_hosts` equivalent.

### 1min.ai (cloud, paye)

Dans `.env` :

```
LLM_PROVIDER=onemin
ONEMIN_API_KEY=sk-...
ONEMIN_MODEL=gpt-4o-mini
```

### Modification a chaud

Le fournisseur, le modele et la cle API peuvent etre modifies a chaud depuis
l'ecran **Parametres** de l'application. Les modifications sont persistees
dans un volume Docker et survivent aux redemarrages. Les variables d'env du
fichier `.env` sont uniquement utilisees comme valeurs initiales au premier
demarrage.

## 6. Mise a jour

```
docker compose pull
docker compose up -d
```

Les donnees (base PostgreSQL, images MinIO, configuration Brain) sont
stockees dans des volumes Docker et survivent aux mises a jour.

## 7. Sauvegarde

Les donnees sont reparties dans trois volumes Docker :

- `loremindmj_postgres-data` — ensemble des donnees applicatives (lores,
  campagnes, pages, templates, branches narratives, etc.).
- `loremindmj_minio-data` — images uploadees.
- `loremindmj_brain-data` — parametres IA (fournisseur courant, cle API
  1min.ai).

### Export SQL de la base

```
docker compose exec postgres pg_dump -U loremind loremind > backup.sql
```

### Sauvegarde complete des volumes

Arreter la stack au prealable afin de garantir la coherence des donnees :

```
docker compose stop
docker run --rm -v loremindmj_postgres-data:/data -v $(pwd):/backup alpine tar czf /backup/postgres-data.tar.gz -C /data .
docker run --rm -v loremindmj_minio-data:/data -v $(pwd):/backup alpine tar czf /backup/minio-data.tar.gz -C /data .
docker compose start
```

Sous Windows PowerShell, remplacer `$(pwd)` par `${PWD}`.

## 8. Resolution des problemes

### Port 8081 deja utilise

Modifier `WEB_PORT=8082` (ou toute autre valeur libre) dans `.env`, puis
relancer :

```
docker compose up -d
```

### Erreur "set POSTGRES_PASSWORD in .env" (ou variable equivalente) au lancement

Une des trois variables obligatoires de l'etape 3 est manquante. Verifier le
contenu du fichier `.env`.

### Popup "Ce site vous demande de vous connecter" sur l'ecran Parametres

Comportement attendu : il s'agit de l'authentification HTTP Basic. Utiliser
la valeur de `ADMIN_USERNAME` (par defaut `admin`) et celle de
`ADMIN_PASSWORD`.

### Erreurs `password authentication failed` en boucle dans les logs Postgres

Si la variable `POSTGRES_PASSWORD` a ete modifiee apres un premier lancement,
le volume Postgres conserve l'ancien mot de passe (initialise une seule fois).
Deux options :

- **Redemarrer avec un volume vierge** (entraine la perte des donnees) :
  ```
  docker compose down -v
  docker compose up -d
  ```
- **Modifier le mot de passe en base** sans toucher au volume :
  ```
  docker compose exec postgres psql -U postgres
  ```
  Puis dans le prompt `psql` :
  ```sql
  ALTER USER loremind WITH PASSWORD 'valeur_exacte_du_env';
  \q
  ```
  Redemarrer ensuite le Core : `docker compose restart core`.

### Erreur "502 Bad Gateway" ou message d'erreur IA dans l'interface

Le service Brain ne parvient pas a contacter le fournisseur LLM. Verifier :

- **Ollama** : `ollama serve` est-il actif ? Le modele est-il telecharge
  (`ollama list`) ? La valeur de `LLM_MODEL` correspond-elle exactement au
  nom d'un modele liste ?
- **1min.ai** : la cle API est-elle valide ? Le modele existe-t-il ?
- Consulter les logs du Brain :
  ```
  docker compose logs brain
  ```

### Un service ne demarre pas ou reste en etat `unhealthy`

Consulter les logs du service concerne :

```
docker compose logs <service>
```

Services disponibles : `postgres`, `minio`, `core`, `brain`, `web`.

### Redemarrage d'un service apres modification du `.env`

```
docker compose up -d <service>
```

Redemarrage complet : `docker compose restart`.

### Remise a zero complete (PERTE DES DONNEES)

```
docker compose down -v
```

L'option `-v` supprime les volumes. L'ensemble des lores, campagnes, images
et parametres est perdu de maniere definitive.

### "No such image" ou "pull access denied" au premier lancement

Le registry Gitea peut necessiter une authentification selon la visibilite
configuree pour les images. Contacter l'editeur du projet.

## 9. Exposition reseau des services

- **Interface web** : http://localhost:8081 (port configurable via
  `WEB_PORT`).
- **PostgreSQL** : accessible uniquement via le reseau Docker interne, non
  expose vers l'hote.
- **MinIO** : accessible uniquement via le reseau Docker interne. Les images
  transitent par le reverse-proxy Java sur `/api/images/{id}/content`. Le
  binding `127.0.0.1:9000/9001` defini dans `docker-compose.override.yml`
  n'est actif qu'en developpement.
- **Brain Python** : accessible uniquement via le reseau Docker interne.
  Toute requete doit porter l'en-tete `X-Internal-Secret`, injectee
  automatiquement par le Core Java et jamais exposee au navigateur.

## 10. Desinstallation

```
docker compose down -v
docker image rm git.igmlcreation.fr/ietm64/core git.igmlcreation.fr/ietm64/brain git.igmlcreation.fr/ietm64/web
```

Supprimer ensuite le dossier contenant `docker-compose.yml` et `.env`.
