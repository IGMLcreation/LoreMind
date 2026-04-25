@echo off
REM ============================================================================
REM  LoreMindMJ - Lanceur Windows pour install.ps1
REM ----------------------------------------------------------------------------
REM  Pourquoi ce fichier ?
REM    - Le clic-droit "Executer avec PowerShell" sur un .ps1 echoue souvent
REM      (ExecutionPolicy, fenetre qui se ferme avant qu'on lise l'erreur).
REM    - Ce .bat fait clic-droit "Executer en tant qu'administrateur" -> UAC ->
REM      lance install.ps1 dans une fenetre qui reste ouverte en cas d'erreur.
REM
REM  Usage : double-cliquer ce fichier, accepter le prompt UAC.
REM ============================================================================

setlocal

REM --- Etape 1 : auto-elevation via UAC --------------------------------------
REM Si on n'est pas admin, on relance le .bat en demandant l'elevation.
net session >nul 2>&1
if %errorlevel% NEQ 0 (
    echo Demande d'elevation (UAC)...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

REM --- Etape 2 : lancement du script PowerShell ------------------------------
REM -ExecutionPolicy Bypass : uniquement pour cette session, sans modifier le
REM                            parametre systeme.
REM -NoExit                  : laisse la fenetre ouverte a la fin pour lire le recap.
cd /d "%~dp0"

powershell.exe -NoProfile -ExecutionPolicy Bypass -NoExit -File "%~dp0install.ps1" %*

endlocal
