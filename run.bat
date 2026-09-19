@echo off
rem Starts the game. Build it first with build.bat
setlocal
cd /d "%~dp0"

if not exist out (
    echo Nothing built yet: run build.bat first.
    exit /b 1
)

set CP=out;resources
dir /b lib\*.jar >nul 2>&1
if errorlevel 1 (
    echo note: lib\ has no jars, so music and most sound effects stay silent.
    echo       run tools\get-deps.bat to download the MP3 decoder ^(once^).
) else (
    for %%j in (lib\*.jar) do call set CP=%%CP%%;%%j
)

java -cp "%CP%" game_content.GameWindow
