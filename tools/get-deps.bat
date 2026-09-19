@echo off
rem Downloads the optional MP3 decoder into lib\.
rem The game compiles and runs without it, but Java has no MP3 support built in,
rem so music and most sound effects would stay silent.
setlocal
cd /d "%~dp0.."
if not exist lib mkdir lib

set BASE=https://repo1.maven.org/maven2

call :download mp3spi-1.9.5.4.jar         com/googlecode/soundlibs/mp3spi/1.9.5.4/mp3spi-1.9.5.4.jar
if errorlevel 1 exit /b 1
call :download jlayer-1.0.1.4.jar         com/googlecode/soundlibs/jlayer/1.0.1.4/jlayer-1.0.1.4.jar
if errorlevel 1 exit /b 1
call :download tritonus-share-0.3.7-2.jar com/googlecode/soundlibs/tritonus-share/0.3.7-2/tritonus-share-0.3.7-2.jar
if errorlevel 1 exit /b 1

echo Done. Start the game with run.bat
goto :eof

:download
if exist lib\%~1 (
    echo lib\%~1 already present
    goto :eof
)
echo Downloading %~1 ...
curl -fL -o lib\%~1 %BASE%/%~2
exit /b %ERRORLEVEL%
