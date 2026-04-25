#Requires -Version 5.1
<#
.SYNOPSIS
  Installeur officiel de LoreMindMJ pour Windows 10/11.

.DESCRIPTION
  Script d'installation pas-a-pas qui :
    - Verifie la presence de WSL2 et Docker Desktop ; les installe via winget si absents
    - Telecharge le fichier docker-compose.yml officiel depuis le depot du projet
    - Genere un fichier .env contenant des secrets aleatoires (RNG cryptographique)
    - Configure le mode Ollama (embarque dans Docker ou Ollama deja installe sur l'hote)
    - Demarre la stack Docker et ouvre l'application dans le navigateur

  Aucune connexion sortante n'est etablie en dehors :
    - du depot officiel du projet (fichier docker-compose.yml)
    - du Docker Hub / registry Docker pour les images

  Le code source de ce script est public et auditable a l'adresse indiquee dans .LINK.

.PARAMETER InstallDir
  Dossier d'installation. Defaut : %LOCALAPPDATA%\LoreMind

.PARAMETER ComposeUrl
  URL du fichier docker-compose.yml a recuperer. Defaut : version officielle du depot.

.PARAMETER WebPort
  Port HTTP local sur lequel l'application sera exposee. Defaut : 8081.

.PARAMETER NonInteractive
  Mode automatique pour CI / re-installation. Utilise les valeurs par defaut.

.EXAMPLE
  Procedure recommandee :
    1. Telechargez install.ps1 dans un dossier (clic droit -> Enregistrer la cible sous).
    2. Ouvrez PowerShell en tant qu'administrateur (clic droit sur PowerShell).
    3. Naviguez vers le dossier : cd C:\Chemin\Vers\Le\Dossier
    4. Lancez : .\install.ps1

.NOTES
  Auteur       : ietm64
  Licence      : AGPL-3.0
  Projet       : LoreMindMJ - assistant pour Maitres de Jeu de JDR
  Version      : 0.6.6

.LINK
  https://git.igmlcreation.fr/ietm64/loremind
#>

[CmdletBinding()]
param(
    [string]$InstallDir = "$env:LOCALAPPDATA\LoreMind",
    [string]$ComposeUrl = "https://git.igmlcreation.fr/ietm64/loremind/raw/branch/main/docker-compose.yml",
    [int]$WebPort      = 8081,
    [switch]$NonInteractive
)

$ErrorActionPreference = 'Stop'

function Write-Step($msg)    { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)      { Write-Host "  OK $msg" -ForegroundColor Green }
function Write-Warn2($msg)   { Write-Host "  !! $msg" -ForegroundColor Yellow }
function Write-Err($msg)     { Write-Host "  XX $msg" -ForegroundColor Red }

function Test-Admin {
    # Verifie si la session courante a les droits administrateur Windows.
    $current = [Security.Principal.WindowsIdentity]::GetCurrent()
    return ([Security.Principal.WindowsPrincipal]$current).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)
}

function New-RandomSecret([int]$Length = 32) {
    # Genere un secret aleatoire imprimable (hex) via le RNG cryptographique
    # de .NET. Utilise pour les mots de passe Postgres / MinIO / tokens internes
    # afin que chaque installation ait des credentials uniques.
    $bytes = New-Object byte[] $Length
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return ([BitConverter]::ToString($bytes) -replace '-','').ToLower().Substring(0, $Length)
}

function Test-Wsl2 {
    try {
        $out = wsl.exe --status 2>$null
        return ($LASTEXITCODE -eq 0)
    } catch { return $false }
}

function Test-Docker {
    $cmd = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $cmd) { return $false }
    docker info *>$null
    return ($LASTEXITCODE -eq 0)
}

function Wait-Docker([int]$TimeoutSec = 180) {
    Write-Step "Attente du demarrage de Docker Desktop (max ${TimeoutSec}s)..."
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        docker info *>$null
        if ($LASTEXITCODE -eq 0) { Write-Ok "Docker repond"; return $true }
        Start-Sleep -Seconds 3
    }
    return $false
}

# ---------------------------------------------------------------------------
# 0. Verification des droits administrateur
# ---------------------------------------------------------------------------
# On NE force PAS l'elevation automatique : on demande a l'utilisateur de
# relancer le script lui-meme avec les droits admin. C'est plus transparent
# et evite les avertissements antivirus liees a l'elevation silencieuse.
if (-not (Test-Admin)) {
    Write-Host ""
    Write-Host "Ce script doit etre execute en tant qu'administrateur." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Procedure :"
    Write-Host "  1. Fermez cette fenetre PowerShell."
    Write-Host "  2. Cliquez-droit sur l'icone PowerShell > 'Executer en tant qu'administrateur'."
    Write-Host "  3. Naviguez a nouveau vers ce dossier et relancez : .\install.ps1"
    Write-Host ""
    Read-Host "Appuyez sur Entree pour quitter"
    exit 1
}

Write-Host ""
Write-Host "============================================================"
Write-Host " LoreMindMJ - Installeur Windows" -ForegroundColor Magenta
Write-Host "============================================================"
Write-Host ""

# ---------------------------------------------------------------------------
# 1. WSL2
# ---------------------------------------------------------------------------
Write-Step "Verification de WSL2..."
if (Test-Wsl2) {
    Write-Ok "WSL2 deja installe"
} else {
    Write-Warn2 "WSL2 absent - installation en cours"
    wsl.exe --install --no-launch
    Write-Warn2 "REDEMARRAGE REQUIS. Relancez ce script apres reboot."
    Read-Host "Appuyez sur Entree pour quitter"
    exit 1
}

# ---------------------------------------------------------------------------
# 2. Docker Desktop
# ---------------------------------------------------------------------------
Write-Step "Verification de Docker Desktop..."
if (Test-Docker) {
    Write-Ok "Docker fonctionnel"
} else {
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        Write-Err "winget introuvable. Installez Docker Desktop manuellement : https://www.docker.com/products/docker-desktop/"
        exit 1
    }
    Write-Warn2 "Installation de Docker Desktop via winget (gestionnaire de paquets officiel Microsoft)..."
    # On invoque winget en mode interactif (l'utilisateur voit la progression).
    # Les flags --accept-* sont necessaires pour ne pas bloquer sur les CGU
    # (Docker Desktop a des conditions d'utilisation a accepter).
    winget install --id Docker.DockerDesktop -e --accept-package-agreements --accept-source-agreements
    if ($LASTEXITCODE -ne 0) { Write-Err "Echec de l'installation Docker Desktop via winget"; exit 1 }

    Write-Step "Lancement de Docker Desktop..."
    $dd = "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
    if (Test-Path $dd) { Start-Process $dd }

    if (-not (Wait-Docker 240)) {
        Write-Err "Docker n'a pas demarre. Lancez-le manuellement puis relancez ce script."
        exit 1
    }
}

# ---------------------------------------------------------------------------
# 3. Dossier d'installation + docker-compose.yml
# ---------------------------------------------------------------------------
Write-Step "Preparation du dossier $InstallDir"
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
Set-Location $InstallDir

$composePath = Join-Path $InstallDir 'docker-compose.yml'
Write-Step "Telechargement de docker-compose.yml depuis le depot officiel"
Write-Host "  Source : $ComposeUrl"
# Seul telechargement reseau effectue par ce script. Aucune execution de code
# distant : le fichier est uniquement enregistre sur le disque puis passe a
# 'docker compose' pour interpretation locale.
Invoke-WebRequest -Uri $ComposeUrl -OutFile $composePath -UseBasicParsing
Write-Ok "docker-compose.yml recupere ($composePath)"

# ---------------------------------------------------------------------------
# 4. Generation du .env
# ---------------------------------------------------------------------------
$envPath = Join-Path $InstallDir '.env'
if (Test-Path $envPath) {
    Write-Warn2 ".env deja present - sauvegarde en .env.bak"
    Copy-Item $envPath "$envPath.bak" -Force
}

Write-Step "Configuration"

$adminUser = if ($NonInteractive) { 'admin' } else {
    $r = Read-Host "  Nom d'utilisateur admin [admin]"; if ([string]::IsNullOrWhiteSpace($r)) { 'admin' } else { $r }
}
$adminPass = if ($NonInteractive) { New-RandomSecret 16 } else {
    $r = Read-Host "  Mot de passe admin (vide = genere automatiquement)"
    if ([string]::IsNullOrWhiteSpace($r)) { New-RandomSecret 16 } else { $r }
}

$llmProvider = if ($NonInteractive) { 'ollama' } else {
    $r = Read-Host "  Provider LLM : [ollama] / onemin"
    if ($r -eq 'onemin') { 'onemin' } else { 'ollama' }
}
$onemKey = ''
if ($llmProvider -eq 'onemin' -and -not $NonInteractive) {
    $onemKey = Read-Host "  Cle API 1min.ai"
}

# --- Mode Ollama : embarque (defaut) vs hote -------------------------------
# Embarque  : service 'ollama' du compose (profile local-ollama). Zero config reseau.
# Hote      : Ollama deja installe sur la machine. Necessite OLLAMA_HOST=0.0.0.0
#             pour que Docker Desktop puisse l'atteindre via host.docker.internal.
$useEmbeddedOllama = $true
$ollamaBaseUrl = 'http://ollama:11434'
if ($llmProvider -eq 'ollama') {
    $useEmbeddedOllama = if ($NonInteractive) { $true } else {
        $r = Read-Host "  Avez-vous deja Ollama installe sur cette machine ? [o/N]"
        -not ($r -match '^(o|O|y|Y|oui|yes)$')
    }
    if (-not $useEmbeddedOllama) {
        $ollamaBaseUrl = 'http://host.docker.internal:11434'
        Write-Step "Configuration d'Ollama hote..."
        # Pour que le conteneur Docker puisse atteindre Ollama via host.docker.internal,
        # Ollama doit ecouter sur 0.0.0.0 (et non 127.0.0.1 par defaut). On positionne
        # la variable d'environnement utilisateur OLLAMA_HOST en consequence.
        try {
            [Environment]::SetEnvironmentVariable('OLLAMA_HOST','0.0.0.0:11434','User')
            Write-Ok "Variable d'environnement utilisateur OLLAMA_HOST=0.0.0.0:11434 definie"
            Write-Host ""
            Write-Host "  Pour que ce changement prenne effet, vous devez :" -ForegroundColor Yellow
            Write-Host "    1. Quitter completement Ollama (icone systray > Quit Ollama)"
            Write-Host "    2. Relancer Ollama"
            Write-Host ""
            Read-Host "  Appuyez sur Entree une fois Ollama redemarre"
        } catch {
            Write-Warn2 "Impossible de definir OLLAMA_HOST automatiquement. Definissez-la manuellement (Parametres systeme > Variables d'environnement) puis redemarrez Ollama."
        }
    } else {
        Write-Ok "Ollama sera lance dans Docker (modeles dans un volume Docker dedie)"
    }
}

$llmModel = 'gemma4:26b'

$autoUpdate = if ($NonInteractive) { $true } else {
    $r = Read-Host "  Activer les mises a jour auto (chaque nuit a 4h) ? [O/n]"
    -not ($r -match '^(n|N|no|non)$')
}
# Combinaison de profiles : autoupdate et/ou local-ollama (separes par virgule).
$profilesList = @()
if ($autoUpdate)              { $profilesList += 'autoupdate' }
if ($useEmbeddedOllama -and $llmProvider -eq 'ollama') { $profilesList += 'local-ollama' }
$composeProfiles = $profilesList -join ','

$envContent = @"
# Genere par install.ps1 le $(Get-Date -Format 'yyyy-MM-dd HH:mm')
REGISTRY=git.igmlcreation.fr
TAG=latest

WEB_PORT=$WebPort

POSTGRES_DB=loremind
POSTGRES_USER=loremind
POSTGRES_PASSWORD=$(New-RandomSecret 24)

ADMIN_USERNAME=$adminUser
ADMIN_PASSWORD=$adminPass

BRAIN_INTERNAL_SECRET=$(New-RandomSecret 32)

MINIO_USER=minioadmin
MINIO_PASSWORD=$(New-RandomSecret 24)

LLM_PROVIDER=$llmProvider
OLLAMA_BASE_URL=$ollamaBaseUrl
LLM_MODEL=$llmModel
ONEMIN_API_KEY=$onemKey
ONEMIN_MODEL=gpt-4o-mini

COMPOSE_PROFILES=$composeProfiles
WATCHTOWER_TOKEN=$(New-RandomSecret 32)
WATCHTOWER_MONITOR_ONLY=false
WATCHTOWER_SCHEDULE=0 0 4 * * *
TZ=Europe/Paris
"@

Set-Content -Path $envPath -Value $envContent -Encoding UTF8
Write-Ok ".env genere ($envPath)"

# ---------------------------------------------------------------------------
# 5. Pull + up
# ---------------------------------------------------------------------------
Write-Step "Telechargement des images Docker (peut prendre quelques minutes)"
docker compose pull
if ($LASTEXITCODE -ne 0) { Write-Err "Echec docker compose pull"; exit 1 }

Write-Step "Demarrage de la stack"
docker compose up -d
if ($LASTEXITCODE -ne 0) { Write-Err "Echec docker compose up"; exit 1 }

# ---------------------------------------------------------------------------
# 6. Recap
# ---------------------------------------------------------------------------
$url = "http://localhost:$WebPort"
Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host " LoreMindMJ est lance !" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host " URL          : $url"
Write-Host " Identifiant  : $adminUser"
Write-Host " Mot de passe : $adminPass"
Write-Host " Dossier      : $InstallDir"
if ($autoUpdate) {
    Write-Host " Auto-update  : active (chaque nuit a 4h via Watchtower)" -ForegroundColor Green
} else {
    Write-Host " Auto-update  : desactive (mise a jour manuelle uniquement)"
}
if ($llmProvider -eq 'ollama') {
    if ($useEmbeddedOllama) {
        Write-Host " Ollama       : embarque (service Docker 'ollama')" -ForegroundColor Green
        Write-Host ""
        Write-Host " IMPORTANT : telechargez un modele avant utilisation :"
        Write-Host "   docker exec -it loremind-ollama ollama pull $llmModel"
    } else {
        Write-Host " Ollama       : hote (http://host.docker.internal:11434)"
    }
}
Write-Host ""
Write-Host " Commandes utiles (depuis $InstallDir) :"
Write-Host "   docker compose ps         # etat"
Write-Host "   docker compose logs -f    # logs"
Write-Host "   docker compose down       # arret"
Write-Host "   docker compose pull && docker compose up -d   # mise a jour"
Write-Host ""

Start-Process $url
