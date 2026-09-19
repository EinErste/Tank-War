# Tank-War
Reproduction of classic game "Battle City".

## Requirements

* A JDK (**8 or newer**, developed and verified on 21) - `javac` and `java` on the `PATH`.
* Optional, for music and sound effects: the MP3 decoder jars in `lib/`, see [Audio](#audio) below.

## Build and run

Windows:

```
build.bat
tools\get-deps.bat
run.bat
```

Git Bash / Linux / macOS:

```
./build.sh
tools/get-deps.sh
./run.sh
```

The compiled classes go to `out/`. There is no compile-time dependency at all, `javac -d out $(find src -name '*.java')` is enough.
The project also still opens as an IntelliJ IDEA module (`.iml` + `.idea/`), output directory `out/`.

## How to play

Arrows move the tank, any other key fires. Destroy all 32 enemy tanks of a stage to advance,
do not let the flag (your base) get shot, and pick up the power-ups for an upgrade, an extra life
or a time stop. Ten stages are selectable from the menu.

## Audio

Java has **no MP3 support in the JDK** (`javax.sound.sampled` only handles WAV/AU/AIFF), and all the music
in this game is MP3. `resources/music/sounds/explosion.wav` therefore always plays, while everything else needs
an MP3 service provider on the class path:

```
lib/mp3spi-1.9.5.4.jar          registers the javax.sound service providers
lib/jlayer-1.0.1.4.jar          the MP3 decoder itself
lib/tritonus-share-0.3.7-2.jar  shared sampled-audio base classes used by mp3spi
```

`tools/get-deps.sh` (or `.bat`) downloads them (~400 KB total, from Maven Central). Without them the game
still builds and runs - it prints which files it cannot play instead of failing.

The audio engine is `src/resources_classes/AudioClip.java`, a small replacement for JavaFX's `AudioClip`
(this game used to need Oracle JDK 8, the last JDK that shipped JavaFX). Music is streamed from disk,
sound effects are decoded into memory once so several of them can overlap; volume is applied to the
samples, so it works on every mixer.

## Project layout

```
src/game_content      window, menu, load screen, game field panel, end screen, game loop
src/game_objects      sprites, tanks, bullets, walls, water, cover, base, power-ups, explosions
src/map_tools         Level enum and the ten hard-coded stage maps
src/resources_classes audio, sound selection, image scaling, resource lookup
resources             sprites, music, fonts
```
