# LoreMindMJ — Installation rapide

Ces scripts installent Docker (si nécessaire), génèrent un `.env` sécurisé
et lancent la stack. Aucune configuration manuelle requise.

## Windows 10 / 11

**Procédure recommandée :**

1. Téléchargez les trois fichiers suivants dans un même dossier
   (par ex. `Téléchargements\LoreMind\`) :
   - [`install.bat`](install.bat) — lanceur
   - [`install.ps1`](install.ps1) — script principal
   - [`secure-host-ollama.ps1`](secure-host-ollama.ps1) — *uniquement si vous avez déjà Ollama sur votre PC*
2. **Clic-droit** sur `install.bat` → **Exécuter en tant qu'administrateur**.
3. Acceptez le prompt UAC.

Le script :
1. Vérifie / installe **WSL2** (un reboot peut être nécessaire — relancer le script après).
2. Vérifie / installe **Docker Desktop** via `winget`.
3. Vous demande quelques choix (admin, fournisseur LLM, mode Ollama, mises à jour auto).
4. Génère `%LOCALAPPDATA%\LoreMind\.env` avec mots de passe aléatoires.
5. Lance la stack et ouvre `http://localhost:8081`.

Le `install.bat` sert juste à lancer `install.ps1` proprement (avec UAC + ExecutionPolicy
adaptée à la session, sans modifier les paramètres système). Il est purement
déclaratif et auditable en quelques lignes.

## Linux (Debian / Ubuntu / Fedora / Arch)

```bash
curl -fsSL https://raw.githubusercontent.com/IGMLcreation/LoreMind/main/installers/install.sh | bash
```

Le script :
1. Installe **Docker** via le script officiel `get.docker.com` si absent.
2. Ajoute l'utilisateur courant au groupe `docker` (relogin nécessaire la 1ʳᵉ fois).
3. Installe dans `~/.local/share/loremind`.
4. Lance la stack et ouvre `http://localhost:8081`.

## Mode Ollama (moteur LLM local)

Pendant l'installation, l'installeur pose deux questions successives pour
déterminer comment LoreMind utilisera Ollama :

### 1. *« Avez-vous déjà Ollama installé sur cette machine ? »*

#### Réponse : **Oui** → mode **hôte sécurisé**

L'installeur appelle automatiquement le helper `secure-host-ollama.{sh,ps1}`
qui configure votre Ollama existant pour qu'il soit joignable par le conteneur
Docker LoreMind **sans être exposé sur le réseau local ni Internet**.

- **Linux** : Ollama écoute sur l'IP de la passerelle Docker (`172.17.0.1`
  par défaut). Cette IP n'est jamais routée hors de la machine. Override
  systemd écrit dans `/etc/systemd/system/ollama.service.d/loremind-host.conf`.
- **Windows** : Ollama écoute sur `0.0.0.0` (techniquement nécessaire avec
  Docker Desktop) mais le pare-feu Windows est configuré pour ne **laisser
  passer que** le loopback et les sous-réseaux Docker Desktop. Règles
  ajoutées préfixées `LoreMind-Ollama-*`.

L'URL configurée dans `.env` est `OLLAMA_BASE_URL=http://host.docker.internal:11434`.

#### Réponse : **Non** → l'installeur pose la question 2.

### 2. *« Voulez-vous installer Ollama via Docker maintenant ? »*

#### Réponse : **Oui (défaut)** → mode **embarqué**

Un service `ollama` est ajouté à la stack via le profile Docker `local-ollama`.
Ollama tourne dans un conteneur dédié, sur le réseau interne Docker, **jamais
exposé au LAN ni à Internet**. Les modèles sont stockés dans le volume
Docker `ollama-data` (persistants entre redémarrages et mises à jour).

- URL : `OLLAMA_BASE_URL=http://ollama:11434` (DNS interne Docker).
- Aucune configuration réseau ou pare-feu requise.
- Support GPU NVIDIA automatique si disponible.

Pour télécharger un modèle :

```bash
docker exec -it loremind-ollama ollama pull gemma3:27b
docker exec -it loremind-ollama ollama list
```

#### Réponse : **Non** → mode **différé**

Aucune configuration Ollama n'est appliquée. L'installeur termine sans
Ollama. Vous configurez Ollama plus tard via la page **Paramètres** de LoreMind
en y indiquant l'URL de votre serveur Ollama.

### Lancer le helper de sécurisation manuellement

Si vous avez choisi le mode différé puis installé Ollama plus tard sur votre
poste, ou si vous voulez basculer du mode embarqué vers le mode hôte :

**Linux :**
```bash
bash secure-host-ollama.sh
# Puis dans .env du dossier d'installation :
#   OLLAMA_BASE_URL=http://host.docker.internal:11434
# Et : docker compose up -d
```

**Windows (PowerShell admin) :**
```powershell
.\secure-host-ollama.ps1
# Puis editez .env (dans %LOCALAPPDATA%\LoreMind\) :
#   OLLAMA_BASE_URL=http://host.docker.internal:11434
# Et : docker compose up -d
```

Les helpers sont **réexécutables sans risque** : ils suppriment leurs
anciennes règles avant de les recréer. Utile par exemple si vous avez
réinitialisé Docker Desktop et que les sous-réseaux ont changé.

### Annuler la configuration de sécurisation

**Linux :**
```bash
sudo rm /etc/systemd/system/ollama.service.d/loremind-host.conf
sudo systemctl daemon-reload && sudo systemctl restart ollama
```

**Windows (PowerShell admin) :**
```powershell
Get-NetFirewallRule -DisplayName "LoreMind-Ollama-*" | Remove-NetFirewallRule
[Environment]::SetEnvironmentVariable("OLLAMA_HOST", $null, "User")
```

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
