#!/usr/bin/env bash
# Starts the game. Build it first with ./build.sh
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -d out ] || [ -z "$(find out -name '*.class' -print -quit)" ]; then
	echo "Nothing built yet: run ./build.sh first." >&2
	exit 1
fi

case "$(uname -s)" in
	MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
	*) SEP=':' ;;
esac

CP="out${SEP}resources"
if ls lib/*.jar >/dev/null 2>&1; then
	for jar in lib/*.jar; do
		CP="$CP${SEP}$jar"
	done
else
	echo "note: lib/ has no jars, so music and most sound effects stay silent."
	echo "      run tools/get-deps.sh to download the MP3 decoder (once)."
fi

exec java -cp "$CP" game_content.GameWindow
