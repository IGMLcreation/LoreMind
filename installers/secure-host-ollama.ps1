#Requires -Version 5.1
<#
.SYNOPSIS
  Configuration securisee d'Ollama hote pour LoreMindMJ (Windows).

.DESCRIPTION
  But : permettre au conteneur Docker LoreMind d'atteindre l'Ollama installe
        sur l'hote, SANS exposer Ollama sur le LAN ni Internet.

  Strategie (specifique a Docker Desktop / WSL2 sur Windows) :
    1. Ollama doit ecouter sur 0.0.0.0 (techniquement necessaire car Docker
       Desktop sur Windows utilise un reseau Hyper-V / WSL2 separe).
    2. On compense en ajoutant des regles Windows Firewall qui :
       - BLOQUENT le port 11434 entrant par defaut sur tout profil
       - AUTORISENT 11434 uniquement depuis les sous-reseaux Docker Desktop
         (detectes dynamiquement) et depuis le loopback.

  Resultat : Ollama est joignable par les conteneurs Docker mais
             inaccessible depuis le reseau local ou Internet.

.NOTES
  Ce script doit etre execute en tant qu'administrateur.
  Les regles ajoutees sont prefixees par "LoreMind-Ollama-" pour
  faciliter leur identification et suppression ulterieure.

.LINK
  https://github.com/IGMLcreation/LoreMind
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Write-Step($msg)  { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)    { Write-Host "  OK $msg" -ForegroundColor Green }
function Write-Warn2($msg) { Write-Host "  !! $msg" -ForegroundColor Yellow }
function Write-Err($msg)   { Write-Host "  XX $msg" -ForegroundColor Red }

# --- 1. Verification admin -------------------------------------------------
$current = [Security.Principal.WindowsIdentity]::GetCurrent()
$isAdmin = ([Security.Principal.WindowsPrincipal]$current).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Err "Ce script doit etre execute en tant qu'administrateur."
    Write-Host ""
    Write-Host "Procedure : clic-droit sur PowerShell > 'Executer en tant qu'administrateur',"
    Write-Host "puis relancez ce script."
    Read-Host "Appuyez sur Entree pour quitter"
    exit 1
}

# --- 2. Detection des sous-reseaux Docker Desktop --------------------------
Write-Step "Detection des sous-reseaux utilises par Docker Desktop..."

$dockerSubnets = @()

# Methode 1 : interroger Docker pour les bridges actifs.
try {
    $networks = docker network ls --filter driver=bridge --format "{{.Name}}" 2>$null
    foreach ($net in $networks) {
        if ([string]::IsNullOrWhiteSpace($net)) { continue }
        $subnet = docker network inspect $net -f "{{range .IPAM.Config}}{{.Subnet}}{{end}}" 2>$null
        if (-not [string]::IsNullOrWhiteSpace($subnet)) {
            $dockerSubnets += $subnet.Trim()
        }
    }
} catch {
    Write-Warn2 "Impossible d'interroger Docker pour les sous-reseaux. Utilisation des plages par defaut."
}

# Methode 2 : interfaces vEthernet (WSL/DockerNAT) detectees par Windows.
try {
    $wslInterfaces = Get-NetIPConfiguration -ErrorAction SilentlyContinue |
        Where-Object { $_.InterfaceAlias -match 'vEthernet \(WSL|vEthernet \(Default Switch|vEthernet \(Docker' }
    foreach ($iface in $wslInterfaces) {
        $ipv4 = $iface.IPv4Address
        if ($ipv4 -and $ipv4.IPAddress) {
            # On deduit un /24 a partir de l'adresse de l'interface (approximation safe).
            $octets = $ipv4.IPAddress.Split('.')
            $subnet = "{0}.{1}.{2}.0/24" -f $octets[0], $octets[1], $octets[2]
            $dockerSubnets += $subnet
        }
    }
} catch { }

# Methode 3 : fallback sur les plages connues de Docker Desktop si rien detecte.
if ($dockerSubnets.Count -eq 0) {
    Write-Warn2 "Aucun sous-reseau Docker detecte. Utilisation des plages par defaut Docker Desktop."
    $dockerSubnets = @(
        "172.16.0.0/12",      # Plage standard des reseaux bridge Docker
        "192.168.65.0/24"     # Plage WSL2 / Docker Desktop frequente
    )
}

# Deduplication et nettoyage.
$dockerSubnets = $dockerSubnets | Where-Object { $_ -match '^\d+\.\d+\.\d+\.\d+/\d+$' } | Select-Object -Unique
Write-Ok "Sous-reseaux autorises : $($dockerSubnets -join ', ')"

# --- 3. Variable d'environnement OLLAMA_HOST -------------------------------
Write-Step "Configuration de la variable OLLAMA_HOST..."
[Environment]::SetEnvironmentVariable('OLLAMA_HOST','0.0.0.0:11434','User')
Write-Ok "OLLAMA_HOST=0.0.0.0:11434 definie au niveau utilisateur"

# --- 4. Suppression des anciennes regles LoreMind --------------------------
Write-Step "Nettoyage des anciennes regles Windows Firewall LoreMind..."
$oldRules = Get-NetFirewallRule -DisplayName "LoreMind-Ollama-*" -ErrorAction SilentlyContinue
if ($oldRules) {
    $oldRules | Remove-NetFirewallRule
    Write-Ok "$($oldRules.Count) ancienne(s) regle(s) supprimee(s)"
} else {
    Write-Ok "Aucune ancienne regle a supprimer"
}

# --- 5. Creation des regles --------------------------------------------------
Write-Step "Creation des regles Windows Firewall..."

# 5a. Regle de blocage par defaut (priorite la plus basse en cas de conflit :
#     les regles Allow ont priorite sur les Block dans Windows Firewall, donc
#     ce Block sert de filet final pour tout ce qui n'est pas explicitement
#     autorise par les regles ci-dessous).
New-NetFirewallRule `
    -DisplayName "LoreMind-Ollama-Block-All" `
    -Description "LoreMind: bloque toute connexion entrante Ollama par defaut" `
    -Direction Inbound `
    -Action Block `
    -Protocol TCP `
    -LocalPort 11434 `
    -Profile Any `
    -RemoteAddress Any | Out-Null
Write-Ok "Regle Block-All (port 11434) creee"

# 5b. Regle d'autorisation : loopback uniquement.
New-NetFirewallRule `
    -DisplayName "LoreMind-Ollama-Allow-Loopback" `
    -Description "LoreMind: autorise Ollama depuis 127.0.0.1" `
    -Direction Inbound `
    -Action Allow `
    -Protocol TCP `
    -LocalPort 11434 `
    -Profile Any `
    -RemoteAddress "127.0.0.1" | Out-Null
Write-Ok "Regle Allow-Loopback creee"

# 5c. Regles d'autorisation : sous-reseaux Docker Desktop.
foreach ($subnet in $dockerSubnets) {
    $safeName = "LoreMind-Ollama-Allow-Docker-$($subnet -replace '[\./]','_')"
    New-NetFirewallRule `
        -DisplayName $safeName `
        -Description "LoreMind: autorise Ollama depuis le sous-reseau Docker $subnet" `
        -Direction Inbound `
        -Action Allow `
        -Protocol TCP `
        -LocalPort 11434 `
        -Profile Any `
        -RemoteAddress $subnet | Out-Null
    Write-Ok "Regle Allow-Docker creee pour $subnet"
}

# --- 6. Redemarrage Ollama -------------------------------------------------
Write-Step "Redemarrage d'Ollama pour appliquer OLLAMA_HOST..."
Write-Host ""
Write-Host "  Pour que la variable d'environnement prenne effet, vous devez :" -ForegroundColor Yellow
Write-Host "    1. Quitter completement Ollama (icone systray > Quit Ollama)"
Write-Host "    2. Le relancer depuis le menu Demarrer"
Write-Host ""

# --- 7. Recap --------------------------------------------------------------
Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host " Ollama hote configure de maniere securisee" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host " Adresse d'ecoute : 0.0.0.0:11434 (toutes interfaces)"
Write-Host " Pare-feu Windows : bloque par defaut, autorise loopback + Docker"
Write-Host " Inaccessible depuis : LAN, WiFi public, Internet"
Write-Host ""
Write-Host " Pour LoreMind, definissez dans le fichier .env :"
Write-Host "   OLLAMA_BASE_URL=http://host.docker.internal:11434"
Write-Host ""
Write-Host " Pour annuler cette configuration :"
Write-Host '   Get-NetFirewallRule -DisplayName "LoreMind-Ollama-*" | Remove-NetFirewallRule'
Write-Host '   [Environment]::SetEnvironmentVariable("OLLAMA_HOST",$null,"User")'
Write-Host ""
