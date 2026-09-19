#!/usr/bin/env bash
# Compiles the game into out/. Needs a JDK 8+ on the PATH, nothing else.
set -euo pipefail
cd "$(dirname "$0")"

if [ -d out ]; then
	find out -name '*.class' -type f -delete
fi
mkdir -p out

javac -encoding UTF-8 -d out $(find src -name '*.java')
echo "Compiled $(find src -name '*.java' | wc -l) source files into out/"
echo "Run the game with ./run.sh"
