@echo off
rem Runs every test in test\. Builds the game first, then compiles and runs the tests.
rem Usage: test\run-tests.bat
setlocal
cd /d "%~dp0.."

echo == building the game
call build.bat >nul || exit /b 1

echo == compiling the tests
if not exist test\out mkdir test\out
dir /b /s test\*.java > "%TEMP%\tankwar-tests.txt"
javac -encoding UTF-8 -cp out -d test\out "@%TEMP%\tankwar-tests.txt"
set RESULT=%ERRORLEVEL%
del "%TEMP%\tankwar-tests.txt"
if not %RESULT%==0 (
    echo Compiling the tests failed.
    exit /b %RESULT%
)

set CP=out;test\out;resources
dir /b lib\*.jar >nul 2>&1
if errorlevel 1 (
    echo note: lib\ is empty, so MP3 checks are skipped ^(run tools\get-deps.bat^)
) else (
    for %%j in (lib\*.jar) do call set CP=%%CP%%;%%j
)

set STATUS=0
for %%t in (AudioDecodeTest AudioClipTest EnemyAITest LogicTest MenuBackgroundTest SmokeTest) do (
    echo.
    echo == %%t
    java -cp "%CP%" %%t
    if errorlevel 1 set STATUS=1
)

echo.
if %STATUS%==0 (
    echo ALL TESTS PASSED
) else (
    echo SOME TESTS FAILED
)
exit /b %STATUS%
