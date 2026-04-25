#Requires -Version 5.1
<#
.SYNOPSIS
  Installeur LoreMindMJ pour Windows 10/11.
.DESCRIPTION
  - Verifie / installe WSL2 et Docker Desktop (via winget)
  - Genere un .env avec mots de passe aleatoires
  - Recupere le docker-compose.yml officiel
  - Lance la stack et ouvre le navigateur
.EXAMPLE
  iwr https://git.igmlcreation.fr/ietm64/loremind/raw/branch/main/installers/install.ps1 | iex
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
    $current = [Security.Principal.WindowsIdentity]::GetCurrent()
    return ([Security.Principal.WindowsPrincipal]$current).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Invoke-Elevated {
    Write-Step "Relance en mode administrateur..."
    $args = @('-NoProfile','-ExecutionPolicy','Bypass','-File',$PSCommandPath)
    Start-Process powershell -Verb RunAs -ArgumentList $args
    exit
}

function New-RandomSecret([int]$Length = 32) {
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
# 0. Pre-requis admin
# ---------------------------------------------------------------------------
if (-not (Test-Admin)) { Invoke-Elevated }

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
    Write-Warn2 "Installation de Docker Desktop via winget..."
    winget install --id Docker.DockerDesktop -e --accept-package-agreements --accept-source-agreements
    if ($LASTEXITCODE -ne 0) { Write-Err "Echec winget"; exit 1 }

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
Write-Step "Telechargement de docker-compose.yml"
Invoke-WebRequest -Uri $ComposeUrl -OutFile $composePath -UseBasicParsing
Write-Ok "docker-compose.yml recupere"

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

$autoUpdate = if ($NonInteractive) { $true } else {
    $r = Read-Host "  Activer les mises a jour auto (chaque nuit a 4h) ? [O/n]"
    -not ($r -match '^(n|N|no|non)$')
}
$composeProfiles = if ($autoUpdate) { 'autoupdate' } else { '' }

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
OLLAMA_BASE_URL=http://host.docker.internal:11434
LLM_MODEL=gemma4:26b
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
Write-Host ""
Write-Host " Commandes utiles (depuis $InstallDir) :"
Write-Host "   docker compose ps         # etat"
Write-Host "   docker compose logs -f    # logs"
Write-Host "   docker compose down       # arret"
Write-Host "   docker compose pull && docker compose up -d   # mise a jour"
Write-Host ""

Start-Process $url
