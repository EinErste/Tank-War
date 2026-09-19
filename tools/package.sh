#!/usr/bin/env bash
# Builds a portable Windows folder with TankWar.exe plus a bundled Java runtime, and zips it.
# The result needs no Java installed on the target machine: unzip, run TankWar.exe.
#
# Needs a JDK 14+ (jpackage/jlink/jar ship with the JDK). Nothing else - no WiX, no 7-Zip.
# Usage: tools/package.sh [version]     (version defaults to 1.0)
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"

APP_NAME=TankWar
APP_VERSION="${1:-1.0}"
VENDOR="EinErste"
DESCRIPTION="Tank War - a Battle City reproduction"
DIST=dist
STAGING="$DIST/staging"

for tool in javac jar jpackage; do
	command -v "$tool" >/dev/null || { echo "error: '$tool' not found. A JDK 14+ is required." >&2; exit 1; }
done

shopt -s nullglob
jars=(lib/*.jar)
shopt -u nullglob
if [ ${#jars[@]} -eq 0 ]; then
	echo "error: lib/ has no jars. A packaged build must include the MP3 decoder, run tools/get-deps.sh first." >&2
	exit 1
fi

echo "== 1/6 compiling the game"
bash build.sh >/dev/null

echo "== 2/6 staging"
if [ -d "$DIST" ]; then
	find "$DIST" -mindepth 1 -delete
fi
mkdir -p "$STAGING/classes"

# one self contained jar: the game classes plus the MP3 decoder, so no class path juggling is needed
cp -r out/. "$STAGING/classes/"
for jar in "${jars[@]}"; do
	(cd "$STAGING/classes" && jar xf "$ROOT/$jar")
done
rm -f "$STAGING/classes/META-INF/MANIFEST.MF"
jar cfe "$STAGING/$APP_NAME.jar" game_content.GameWindow -C "$STAGING/classes" .
find "$STAGING/classes" -depth -delete

# the assets stay next to the jar, streamed from disk instead of being packed into it
cp -r resources "$STAGING/resources"
cp tools/THIRD-PARTY-LICENSES.txt "$STAGING/" 2>/dev/null || true

echo "== 3/6 generating the icon"
javac -d "$DIST/icotool" tools/PngToIco.java
java -cp "$DIST/icotool" PngToIco resources/sprites/window/icon.png "$DIST/$APP_NAME.ico"

echo "== 4/6 packaging the app image (bundles a minimal Java runtime)"
jpackage \
	--type app-image \
	--name "$APP_NAME" \
	--app-version "$APP_VERSION" \
	--vendor "$VENDOR" \
	--description "$DESCRIPTION" \
	--input "$STAGING" \
	--main-jar "$APP_NAME.jar" \
	--main-class game_content.GameWindow \
	--icon "$DIST/$APP_NAME.ico" \
	--add-modules java.desktop \
	--dest "$DIST"

echo "== 5/6 zipping"
# keep only the app image in dist/, so the archive contains nothing but the game
find "$STAGING" -depth -delete 2>/dev/null || true
find "$DIST/icotool" -depth -delete 2>/dev/null || true
rm -f "$DIST/$APP_NAME.ico"
ARCHIVE="$DIST/$APP_NAME-$APP_VERSION-windows-x64-portable.zip"
rm -f "$ARCHIVE"
jar cfM "$ARCHIVE" -C "$DIST" "$APP_NAME"

echo "== 6/6 done"
du -sh "$DIST/$APP_NAME" "$ARCHIVE" 2>/dev/null || true
echo
echo "Portable build: $ARCHIVE"
echo "Unzip it and run $APP_NAME/$APP_NAME.exe (no Java needed on the target machine)."
