@echo off
rem Compiles the game into out\. Needs a JDK 8+ on the PATH, nothing else.
setlocal
cd /d "%~dp0"

if exist out rmdir /s /q out
mkdir out

dir /b /s src\*.java > "%TEMP%\tankwar-sources.txt"
javac -encoding UTF-8 -d out "@%TEMP%\tankwar-sources.txt"
set RESULT=%ERRORLEVEL%
del "%TEMP%\tankwar-sources.txt"
if not %RESULT%==0 (
    echo Compilation failed.
    exit /b %RESULT%
)
echo Compiled into out\
echo Run the game with run.bat
