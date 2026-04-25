@echo off
REM ============================================================================
REM  LoreMindMJ - Lanceur Windows pour install.ps1
REM ----------------------------------------------------------------------------
REM  Procedure :
REM    1. Clic-DROIT sur ce fichier (install.bat)
REM    2. Choisir "Executer en tant qu'administrateur"
REM    3. Accepter le prompt UAC
REM ============================================================================

setlocal
title LoreMindMJ - Installeur

echo.
echo ============================================================
echo  LoreMindMJ - Installeur Windows
echo ============================================================
echo.

REM --- Verification des droits administrateur --------------------------------
net session >nul 2>&1
if %errorlevel% NEQ 0 (
    echo [ERREUR] Ce script doit etre execute en tant qu'administrateur.
    echo.
    echo Procedure :
    echo   1. Fermez cette fenetre.
    echo   2. Clic-DROIT sur install.bat ^> "Executer en tant qu'administrateur".
    echo   3. Acceptez le prompt UAC.
    echo.
    pause
    exit /b 1
)

REM --- Verification de la presence d'install.ps1 -----------------------------
if not exist "%~dp0install.ps1" (
    echo [ERREUR] install.ps1 introuvable dans le meme dossier que ce .bat.
    echo Dossier attendu : %~dp0
    echo.
    pause
    exit /b 1
)

REM --- Lancement du script PowerShell ----------------------------------------
REM -ExecutionPolicy Bypass : uniquement pour cette session, ne modifie pas
REM                            les parametres systeme.
cd /d "%~dp0"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" %*
set "PS_EXIT=%errorlevel%"

echo.
if %PS_EXIT% EQU 0 (
    echo Installation terminee avec succes.
) else (
    echo [ATTENTION] Le script PowerShell s'est termine avec le code %PS_EXIT%.
)
echo.
pause
endlocal
