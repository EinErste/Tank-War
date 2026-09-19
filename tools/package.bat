@echo off
rem Builds a portable Windows folder with TankWar.exe plus a bundled Java runtime, and zips it.
rem The result needs no Java installed on the target machine: unzip, run TankWar.exe.
rem
rem Needs a JDK 14+ (jpackage/jlink/jar ship with the JDK). Nothing else - no WiX, no 7-Zip.
rem Usage: tools\package.bat [version]     (version defaults to 1.0)
setlocal
cd /d "%~dp0.."

set APP_NAME=TankWar
set APP_VERSION=%~1
if "%APP_VERSION%"=="" set APP_VERSION=1.0
set DIST=dist
set STAGING=%DIST%\staging

for %%t in (javac jar jpackage) do (
    where %%t >nul 2>&1
    if errorlevel 1 (
        echo error: '%%t' not found. A JDK 14+ is required.
        exit /b 1
    )
)

dir /b lib\*.jar >nul 2>&1
if errorlevel 1 (
    echo error: lib\ has no jars. A packaged build must include the MP3 decoder,
    echo        run tools\get-deps.bat first.
    exit /b 1
)

echo == 1/6 compiling the game
call build.bat || exit /b 1

echo == 2/6 staging
if exist %DIST% rmdir /s /q %DIST%
mkdir %STAGING%\classes
xcopy out %STAGING%\classes /E /I /Q /Y >nul

rem one self contained jar: the game classes plus the MP3 decoder, so no class path juggling is needed
pushd %STAGING%\classes
for %%j in ("%~dp0..\lib\*.jar") do jar xf "%%j"
popd
if exist %STAGING%\classes\META-INF\MANIFEST.MF del %STAGING%\classes\META-INF\MANIFEST.MF
jar cfe %STAGING%\%APP_NAME%.jar game_content.GameWindow -C %STAGING%\classes . || exit /b 1
rmdir /s /q %STAGING%\classes

rem the assets stay next to the jar, streamed from disk instead of being packed into it
xcopy resources %STAGING%\resources /E /I /Q /Y >nul
copy /y tools\THIRD-PARTY-LICENSES.txt %STAGING% >nul

echo == 3/6 generating the icon
javac -d %DIST%\icotool tools\PngToIco.java || exit /b 1
java -cp %DIST%\icotool PngToIco resources\sprites\window\icon.png %DIST%\%APP_NAME%.ico || exit /b 1

echo == 4/6 packaging the app image (bundles a minimal Java runtime)
jpackage --type app-image --name %APP_NAME% --app-version %APP_VERSION% ^
    --vendor EinErste --description "Tank War - a Battle City reproduction" ^
    --input %STAGING% --main-jar %APP_NAME%.jar --main-class game_content.GameWindow ^
    --icon %DIST%\%APP_NAME%.ico --add-modules java.desktop --dest %DIST% || exit /b 1

echo == 5/6 zipping
rem keep only the app image in dist\, so the archive contains nothing but the game
rmdir /s /q %STAGING%
rmdir /s /q %DIST%\icotool
del %DIST%\%APP_NAME%.ico
set ARCHIVE=%DIST%\%APP_NAME%-%APP_VERSION%-windows-x64-portable.zip
if exist "%ARCHIVE%" del "%ARCHIVE%"
jar cfM "%ARCHIVE%" -C %DIST% %APP_NAME% || exit /b 1

echo == 6/6 done
echo.
echo Portable build: %ARCHIVE%
echo Unzip it and run %APP_NAME%\%APP_NAME%.exe (no Java needed on the target machine).
