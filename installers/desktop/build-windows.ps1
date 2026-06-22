#Requires -Version 5.1
<#
.SYNOPSIS
  Construit l'installeur de BUREAU Windows de LoreMind (.msi), sans Docker.

.DESCRIPTION
  Pipeline complet "local-first" :
    1. Build du front Angular            (web/  -> web/dist/web)
    2. Prep du Brain (Python embeddable) (brain/ -> dist-embed : python.exe signe PSF + deps + sources)
    3. Build du Core en fat jar + front  (core/  -> target/*.jar, profil Maven "desktop")
    4. Assemblage de la charge utile      (jar + brain) dans un dossier d'entree jpackage
    5. jpackage                           -> installeur .msi avec JRE embarque

  L'app resultante se lance d'un double-clic : le Core demarre en profil Spring
  "local" (H2 fichier + stockage filesystem) et lance lui-meme le Brain en sidecar.
  Aucune dependance externe a installer cote utilisateur (ni Docker, ni Java, ni Python).
  L'IA fonctionne via le cloud (1min.ai / Gemini / Mistral...) ou via Ollama si present.

.PARAMETER Version
  Version de l'installeur (X.Y.Z, numerique). Defaut : derivee de core/pom.xml
  (le suffixe -beta est retire car les MSI n'acceptent qu'une version numerique).

.PARAMETER SkipFront / SkipBrain / SkipJar
  Sauter une etape (build incrementaux pendant la mise au point).

.PREREQUIS (sur la machine de build uniquement, PAS chez l'utilisateur final)
  - JDK 21+ avec jpackage dans le PATH (Temurin OK).
  - WiX Toolset v3 (https://github.com/wixtoolset/wix3/releases) — requis par
    jpackage pour produire un .msi sur Windows.
  - Node.js + npm (build Angular).
  - Python + pip (telecharge les wheels cp312 du Brain ; toute version 3.x convient,
    on cible explicitement 3.12 via --python-version) + acces Internet (python.org).

.NOTES
  Projet : LoreMind — assistant pour Maitres de Jeu de JDR
  Licence : AGPL-3.0
#>

[CmdletBinding()]
param(
    [string]$Version,
    [switch]$SkipFront,
    [switch]$SkipBrain,
    [switch]$SkipJar
)

$ErrorActionPreference = 'Stop'

function Write-Step($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Write-Ok($m)   { Write-Host "  OK $m" -ForegroundColor Green }
function Write-Err($m)  { Write-Host "  XX $m" -ForegroundColor Red }

# --- Chemins ---------------------------------------------------------------
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$WebDir   = Join-Path $RepoRoot 'web'
$BrainDir = Join-Path $RepoRoot 'brain'
$CoreDir  = Join-Path $RepoRoot 'core'
$StageDir = Join-Path $CoreDir 'target\dist-input'   # charge utile jpackage
$OutDir   = Join-Path $CoreDir 'target\dist-out'     # .msi produit
$IconFile = Join-Path $PSScriptRoot 'app-icon.ico'   # icone de l'app (.msi + raccourcis)
$WixDir   = Join-Path $PSScriptRoot 'wix'            # main.wxs surcharge (case "Lancer" en fin d'install)
$SplashFile = Join-Path $PSScriptRoot 'splash.png'   # ecran de demarrage (affiche par la JVM via -splash)

# --- Version (numerique pour MSI) ------------------------------------------
if (-not $Version) {
    $pom = Get-Content (Join-Path $CoreDir 'pom.xml') -Raw
    # IMPORTANT : viser la version DU PROJET, pas le premier <version> du fichier
    # (qui est celui du parent spring-boot-starter-parent). On l'ancre sur
    # l'artifactId du projet, puis on garde la partie numerique (MSI = X.Y.Z).
    if ($pom -match '<artifactId>loremind-core</artifactId>\s*<version>([0-9]+\.[0-9]+\.[0-9]+)') {
        $Version = $Matches[1]
    } else {
        $Version = '0.0.0'
    }
}
Write-Host ""
Write-Host "============================================================" -ForegroundColor Magenta
Write-Host " LoreMind - Build installeur bureau Windows (v$Version)"     -ForegroundColor Magenta
Write-Host "============================================================" -ForegroundColor Magenta

# --- Verif outils ----------------------------------------------------------
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    Write-Err "jpackage introuvable dans le PATH. Installez un JDK 21+ (Temurin)."
    exit 1
}

# --- 1. Front Angular ------------------------------------------------------
if (-not $SkipFront) {
    Write-Step "Build du front Angular"
    Push-Location $WebDir
    try {
        if (-not (Test-Path 'node_modules')) { npm ci }
        npm run build
        if ($LASTEXITCODE -ne 0) { throw "Echec du build Angular" }
    } finally { Pop-Location }
    Write-Ok "Front construit (web/dist/web)"
} else { Write-Step "Front : saute (--SkipFront)" }

# --- 2. Brain (Python embeddable officiel, PAS d'exe gele) -----------------
# On embarque le Python *embeddable* de python.org (python.exe SIGNE par la PSF)
# + les dependances + les sources .py. Aucun executable "gele" type PyInstaller
# -> plus de faux positif antivirus (le bootloader packe etait pris pour un
# trojan). Le Core lancera :  brain\python\python.exe brain\run_local.py
$PyVersion = '3.12.8'                          # doit matcher python:3.12 du Docker
$PyTag     = 'python312'                        # prefixe du fichier ._pth
$BrainEmbed = Join-Path $BrainDir 'dist-embed'  # staging du brain empaquete
if (-not $SkipBrain) {
    Write-Step "Preparation du Brain (Python embeddable $PyVersion)"
    if (Test-Path $BrainEmbed) { Remove-Item $BrainEmbed -Recurse -Force }
    $PyDir = Join-Path $BrainEmbed 'python'
    New-Item -ItemType Directory -Force -Path $PyDir | Out-Null

    # a) Telecharger + extraire le Python embeddable (zip officiel).
    $zip = Join-Path $BrainEmbed 'python-embed.zip'
    $url = "https://www.python.org/ftp/python/$PyVersion/python-$PyVersion-embed-amd64.zip"
    Write-Host "  Telechargement $url"
    Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    Expand-Archive -Path $zip -DestinationPath $PyDir -Force
    Remove-Item $zip -Force

    # b) Activer site-packages dans le ._pth (sinon les deps ne sont pas importees).
    $pth = Join-Path $PyDir "$PyTag._pth"
    Set-Content -Path $pth -Encoding ascii -Value @(
        "$PyTag.zip", '.', 'Lib\site-packages', 'import site'
    )

    # c) Installer les deps en wheels cp312 dans python\Lib\site-packages.
    #    --python-version 3.12 + --only-binary : on telecharge les roues 3.12
    #    meme si le pip courant tourne sous une autre version de Python.
    $site = Join-Path $PyDir 'Lib\site-packages'
    New-Item -ItemType Directory -Force -Path $site | Out-Null
    Push-Location $BrainDir
    try {
        python -m pip install --upgrade pip *> $null
        python -m pip install --target $site --only-binary=:all: --python-version 3.12 -r requirements.txt
        if ($LASTEXITCODE -ne 0) { throw "Echec pip install (deps Brain)" }
    } finally { Pop-Location }

    # d) Copier les sources du Brain + le point d'entree.
    Copy-Item (Join-Path $BrainDir 'app')          (Join-Path $BrainEmbed 'app') -Recurse
    Copy-Item (Join-Path $BrainDir 'run_local.py') (Join-Path $BrainEmbed 'run_local.py')

    # e) Tesseract (OCR des PDF scannes). Pas de zip portable officiel : on COPIE
    #    l'install UB-Mannheim (tesseract.exe + DLL), prerequis a installer une
    #    fois via `choco install tesseract`. Langues fra+eng tirees de tessdata_fast
    #    (leger, coherent). Si Tesseract n'est pas installe -> skip gracieux :
    #    l'app reste fonctionnelle, seul l'OCR des scans manquera (cf. run_local.py
    #    + pdf_extractor.py qui detectent l'absence et degradent proprement).
    $tessSrc = Join-Path $env:ProgramFiles 'Tesseract-OCR'
    if (Test-Path (Join-Path $tessSrc 'tesseract.exe')) {
        $tessDst = Join-Path $BrainEmbed 'tesseract'
        Copy-Item $tessSrc $tessDst -Recurse
        New-Item -ItemType Directory -Force -Path (Join-Path $tessDst 'tessdata') | Out-Null
        foreach ($lang in @('fra','eng')) {
            Invoke-WebRequest -UseBasicParsing `
                -Uri "https://github.com/tesseract-ocr/tessdata_fast/raw/main/$lang.traineddata" `
                -OutFile (Join-Path $tessDst "tessdata\$lang.traineddata")
        }
        Write-Ok "Tesseract bundle (OCR des scans actif)"
    } else {
        Write-Host "  !! Tesseract introuvable ($tessSrc) -> OCR des scans NON bundle (degradation gracieuse). Pour l'activer : choco install tesseract" -ForegroundColor Yellow
    }

    Write-Ok "Brain prepare (brain/dist-embed : python embeddable + deps + sources)"
} else { Write-Step "Brain : saute (--SkipBrain)" }

# --- 3. Core (fat jar avec front embarque) ---------------------------------
if (-not $SkipJar) {
    Write-Step "Build du Core (fat jar, profil desktop)"
    Push-Location $CoreDir
    try {
        # -Pdesktop : copie web/dist/web dans le jar (classpath:/static).
        cmd /c "mvn -q -Pdesktop -DskipTests clean package"
        if ($LASTEXITCODE -ne 0) { throw "Echec du build Maven" }
    } finally { Pop-Location }
    Write-Ok "Core construit (core/target)"
} else { Write-Step "Core : saute (--SkipJar)" }

# --- 4. Assemblage de la charge utile --------------------------------------
Write-Step "Assemblage de la charge utile jpackage"
if (Test-Path $StageDir) { Remove-Item $StageDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $StageDir | Out-Null

# Le jar repackage Spring Boot (executable) — on ignore le *.jar.original.
$jar = Get-ChildItem (Join-Path $CoreDir 'target') -Filter 'loremind-core-*.jar' |
       Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
if (-not $jar) { Write-Err "Jar introuvable dans core/target. Lancez sans --SkipJar."; exit 1 }
Copy-Item $jar.FullName (Join-Path $StageDir 'loremind-core.jar')

# Le Brain (python embeddable + deps + sources) -> stage/brain/
#   stage/brain/python/python.exe , stage/brain/app , stage/brain/run_local.py
if (-not (Test-Path $BrainEmbed)) { Write-Err "Brain introuvable (brain/dist-embed). Lancez sans --SkipBrain."; exit 1 }
$stageBrain = Join-Path $StageDir 'brain'
New-Item -ItemType Directory -Force -Path $stageBrain | Out-Null
Copy-Item (Join-Path $BrainEmbed '*') $stageBrain -Recurse

# Splash : copie dans la charge utile -> atterrit dans $APPDIR\splash.png (cf. -splash plus bas).
if (Test-Path $SplashFile) { Copy-Item $SplashFile (Join-Path $StageDir 'splash.png') }
Write-Ok "Charge utile prete ($StageDir)"

# --- 5. jpackage -> .msi ---------------------------------------------------
Write-Step "Generation de l'installeur (.msi) via jpackage"
if (Test-Path $OutDir) { Remove-Item $OutDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# $APPDIR est substitue par jpackage au LANCEMENT par le dossier 'app' de
# l'install (qui contient le jar ET le dossier brain copie depuis --input).
# Le Brain se lance via le python embeddable + run_local.py. brain.sidecar.command
# est une liste : les deux chemins separes par une virgule (aucun chemin Windows
# ne contient de virgule) sont bindes en List<String> par Spring.
$brainCmd = '$APPDIR\brain\python\python.exe,$APPDIR\brain\run_local.py'

# --win-upgrade-uuid : UUID STABLE du produit. Permet a Windows Installer de
# reconnaitre qu'une nouvelle version est une MISE A JOUR du meme produit -> upgrade
# en place propre (pas besoin de desinstaller avant, pas de doublon). Les donnees
# (base H2, images) vivent de toute facon dans ~/.loremind, HORS du dossier
# d'installation, donc jamais touchees par l'installeur.
# /!\ NE JAMAIS CHANGER cet UUID : le modifier casserait la chaine d'upgrade.
#
# Note : pas de --win-console (app de bureau). Les logs Spring/Brain peuvent
# etre rediriges vers un fichier via une option ulterieure si besoin de debug.
# Icone de l'application (.msi, raccourcis bureau/menu). Doit etre un .ico Windows
# multi-resolution (16/32/48/256). Absente -> jpackage retomberait sur l'icone Java
# par defaut : on echoue tot avec un message clair plutot que de livrer ca.
if (-not (Test-Path $IconFile)) {
    Write-Err "Icone manquante : $IconFile"
    Write-Err "Depose l'icone de l'app (image DM, .ico multi-resolution) a cet emplacement."
    exit 1
}

jpackage `
    --type msi `
    --name 'DM Loremind' `
    --app-version $Version `
    --vendor 'IGML Creation' `
    --icon $IconFile `
    --resource-dir $WixDir `
    --input $StageDir `
    --main-jar loremind-core.jar `
    --main-class org.springframework.boot.loader.launch.JarLauncher `
    --dest $OutDir `
    --java-options '-Dspring.profiles.active=local' `
    --java-options "-Dbrain.sidecar.command=$brainCmd" `
    --java-options '-splash:$APPDIR\splash.png' `
    --win-per-user-install `
    --win-upgrade-uuid 'a7c4e1d2-9b3f-4e6a-8d05-1f2c3b4a5e6d' `
    --win-menu --win-menu-group 'DM Loremind' `
    --win-shortcut --win-dir-chooser

if ($LASTEXITCODE -ne 0) {
    Write-Err "Echec jpackage. Cause probable : WiX Toolset v3 absent."
    Write-Err "Installez-le : https://github.com/wixtoolset/wix3/releases"
    exit 1
}

$msi = Get-ChildItem $OutDir -Filter '*.msi' | Select-Object -First 1
Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host " Installeur genere !" -ForegroundColor Green
Write-Host "   $($msi.FullName)" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
