#!/usr/bin/env bash
# Runs every test in test/. Builds the game first, then compiles and runs the tests.
# Usage: test/run-tests.sh
set -euo pipefail
cd "$(dirname "$0")/.."

case "$(uname -s)" in
	MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
	*) SEP=':' ;;
esac

echo "== building the game"
bash build.sh >/dev/null

echo "== compiling the tests"
mkdir -p test/out
javac -encoding UTF-8 -cp out -d test/out $(find test -maxdepth 1 -name '*.java')

CP="out${SEP}test/out${SEP}resources"
if ls lib/*.jar >/dev/null 2>&1; then
	for jar in lib/*.jar; do
		CP="$CP${SEP}$jar"
	done
else
	echo "note: lib/ is empty, so MP3 checks are skipped (run tools/get-deps.sh)"
fi

status=0
for test in AudioDecodeTest AudioClipTest EnemyAITest LogicTest MenuBackgroundTest SmokeTest; do
	echo
	echo "== $test"
	if java -cp "$CP" "$test"; then
		:
	else
		status=1
	fi
done

echo
if [ $status -eq 0 ]; then
	echo "ALL TESTS PASSED"
else
	echo "SOME TESTS FAILED"
fi
exit $status
