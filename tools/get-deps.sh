#!/usr/bin/env bash
# Downloads the optional MP3 decoder into lib/.
# The game compiles and runs without it, but Java has no MP3 support built in,
# so music and most sound effects would stay silent.
set -euo pipefail
cd "$(dirname "$0")/.."

BASE=https://repo1.maven.org/maven2
mkdir -p lib

# mp3spi registers the javax.sound service providers, jlayer does the decoding,
# tritonus-share provides the shared sampled-file base classes mp3spi builds on.
download() {
	local path="$1" url="$2"
	if [ -f "lib/$path" ]; then
		echo "lib/$path already present"
		return
	fi
	echo "Downloading $path ..."
	curl -fL -o "lib/$path" "$BASE/$url"
}

download mp3spi-1.9.5.4.jar        com/googlecode/soundlibs/mp3spi/1.9.5.4/mp3spi-1.9.5.4.jar
download jlayer-1.0.1.4.jar        com/googlecode/soundlibs/jlayer/1.0.1.4/jlayer-1.0.1.4.jar
download tritonus-share-0.3.7-2.jar com/googlecode/soundlibs/tritonus-share/0.3.7-2/tritonus-share-0.3.7-2.jar

echo "Done. Start the game with ./run.sh"
