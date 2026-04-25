# LoreMindMJ — Installation rapide

Ces scripts installent Docker (si nécessaire), génèrent un `.env` sécurisé
et lancent la stack. Aucune configuration manuelle requise.

## Windows 10 / 11

Ouvrir **PowerShell** (clic droit → *Exécuter en tant qu'administrateur*) :

```powershell
iwr https://git.igmlcreation.fr/ietm64/loremind/raw/branch/main/installers/install.ps1 -OutFile $env:TEMP\loremind-install.ps1
powershell -ExecutionPolicy Bypass -File $env:TEMP\loremind-install.ps1
```

Le script :
1. Vérifie / installe **WSL2** (un reboot peut être nécessaire — relancer le script après).
2. Vérifie / installe **Docker Desktop** via `winget`.
3. Génère `%LOCALAPPDATA%\LoreMind\.env` avec mots de passe aléatoires.
4. Lance la stack et ouvre `http://localhost:8081`.

## Linux (Debian / Ubuntu / Fedora / Arch)

```bash
curl -fsSL https://git.igmlcreation.fr/ietm64/loremind/raw/branch/main/installers/install.sh | bash
```

Le script :
1. Installe **Docker** via le script officiel `get.docker.com` si absent.
2. Ajoute l'utilisateur courant au groupe `docker` (relogin nécessaire la 1ʳᵉ fois).
3. Installe dans `~/.local/share/loremind`.
4. Lance la stack et ouvre `http://localhost:8081`.

## Variables disponibles

| Variable          | Défaut                          | Effet                                  |
|-------------------|---------------------------------|----------------------------------------|
| `WEB_PORT`        | `8081`                          | Port HTTP de l'UI                      |
| `INSTALL_DIR`     | `~/.local/share/loremind` (Lin) | Dossier d'installation                 |
| `NON_INTERACTIVE` | `0`                             | `1` = aucune question, valeurs par défaut |

Exemple Linux non-interactif sur port 9000 :

```bash
WEB_PORT=9000 NON_INTERACTIVE=1 bash install.sh
```

## Mises à jour automatiques (Watchtower)

Si vous avez répondu **oui** à la question "Activer les mises à jour auto",
un container [Watchtower](https://containrrr.dev/watchtower/) est lancé en
parallèle. Il vérifie chaque nuit à 4h les nouvelles versions de
`core`, `brain` et `web` sur le registry, télécharge et redémarre les
conteneurs concernés. **Postgres et MinIO sont volontairement exclus**
(données persistantes — montée de version à valider manuellement).

### Activer / désactiver après coup

Éditer `.env` dans le dossier d'installation :

```env
COMPOSE_PROFILES=autoupdate    # active
COMPOSE_PROFILES=              # desactive
```

Puis :

```bash
docker compose up -d            # applique le changement
docker compose stop watchtower  # si on vient de le desactiver
```

### Changer l'horaire

`WATCHTOWER_SCHEDULE` dans `.env` accepte la syntaxe
[cron 6 champs](https://pkg.go.dev/github.com/robfig/cron) (sec min h jour mois j-sem).
Exemples : `0 0 4 * * *` (4h du matin, défaut), `0 30 3 * * 0` (dimanche 3h30).

### Mode "notification seulement" (sans auto-apply)

Si vous préférez être notifié *sans* que les conteneurs redémarrent
automatiquement la nuit, éditez `.env` :

```env
WATCHTOWER_MONITOR_ONLY=true
```

Puis `docker compose up -d watchtower`. Watchtower continuera à vérifier
le registry chaque nuit, le badge **MAJ** apparaîtra dans la sidebar de
l'UI, et un bouton **Mettre à jour maintenant** sera disponible dans
*Paramètres → Mises à jour*.

### Mise à jour manuelle (à tout moment)

Depuis l'interface : *Paramètres → Mises à jour → Mettre à jour maintenant*.

Ou en CLI :

```bash
docker compose pull && docker compose up -d
```

## Désinstallation

```bash
cd <dossier d'install>
docker compose down -v   # -v supprime aussi les volumes (données effacées !)
```

Puis supprimer le dossier d'installation.
