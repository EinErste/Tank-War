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

## Portable Windows build (no Java needed by the player)

```
tools\get-deps.bat          (once: fetch the MP3 decoder)
tools\package.bat           (or: tools\package.bat 1.1 for another version)
```

This produces `dist/TankWar-<version>-windows-x64-portable.zip` (~82 MB). Unzip it and run
`TankWar/TankWar.exe` - the archive contains the game, all assets and a minimal Java runtime, so the
target machine does not need a JDK or JRE installed. `./tools/package.sh` is the same thing for Git Bash.

How it is built: a single self-contained `TankWar.jar` (game classes plus the MP3 decoder) is handed to
`jpackage --type app-image`, which bundles a `jlink` runtime (`java.desktop` only) and generates the
native launcher. The launcher icon is generated from `resources/sprites/window/icon.png` by
`tools/PngToIco.java`. Only the JDK is required (14+); no WiX, no 7-Zip. Bundled library licenses are in
`tools/THIRD-PARTY-LICENSES.txt`.

`dist/` is not committed - attach the zip to a GitHub release instead.

## How to play

Arrows move the tank, any other key fires. Destroy all 32 enemy tanks of a stage to advance,
do not let the flag (your base) get shot, and pick up the power-ups for an upgrade, an extra life
or a time stop. Ten stages are selectable from the menu.

## Tests

```
test\run-tests.bat          (or: ./test/run-tests.sh)
```

Builds the game, compiles `test/` into `test/out` and runs six tests:

* `LogicTest` - headless game rules: a tank must be blocked by a wall it is not standing in, but
  able to drive out of one it already overlaps (turning snaps a tank onto the grid), the same for
  tank against tank, the field edges, and the base/player/enemy spawn squares of all ten stages.
* `AudioClipTest` - the audio engine: streaming music keeps playing, sound effects overlap, `stop()`
  works, volume 0 is silent, a missing file is reported instead of thrown.
* `AudioDecodeTest` - decodes every file in `resources/music` and fails if one yields no sound.
* `EnemyAITest` - the enemy AI rules: it only re-decides on a tile boundary and on a 1 in 16 roll,
  rotates its goal between base/wander/player, takes the dominant axis towards its target, turns
  around on 1 in 4 blocked ticks, fires on a 1 in 32 roll without aiming, and the four types have
  their documented stats.
* `MenuBackgroundTest` - screenshots the menu and fails if it comes out blank (that is how the white
  menu of the packaged build was caught). Writes `test/out/menu-screenshot.png`.
* `SmokeTest` - drives the real UI the way a player does (menu, Play, level 1, stage transition,
  game over, Menu, stage 10) and asserts that the menu music stops when a level starts. It opens a
  window, so it is skipped on a headless machine.

The MP3 dependent checks are skipped with a notice when `lib/` is empty, since the JDK cannot read
MP3 on its own.

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

## Enemy AI

The enemies are modelled on the original Battle City (Famicom) rather than on a path finder, see
`src/game_objects/movables/EnemyTankBrain.java` for the mapping to the original's routines:

* an enemy only picks a new direction while it stands on a tile boundary and only on a 1 in 16 roll
  (`EntityMovementAI`), and it skips its move that tick - otherwise it just drives on,
* the goal rotates: chase the base, then wander randomly, then chase the player (`SpeedCtrlMove`),
  which is the original's "random detour, but eventually it goes for your base",
* a chase takes the axis with the larger distance to the target first (`CalcDirToTarget`),
* a blocked enemy turns around 1 time in 4 and otherwise bumps into the wall (`EntityMovementBlocked`),
* it fires on a 1 in 32 roll per tick, without aiming (`EnemyFireTick`).

Four enemy types with the original's stats (basic, fast, power and armour with four hits,
100/200/300/400 points) are spawned in a per stage mix. Since this game has a single enemy sprite set,
the types are told apart by recolouring it - swap in real sprites whenever you like.

## Project layout

```
src/game_content      window, menu, load screen, game field panel, end screen, game loop
src/game_objects      sprites, tanks, bullets, walls, water, cover, base, power-ups, explosions
src/map_tools         Level enum and the ten hard-coded stage maps
src/resources_classes audio, sound selection, image scaling, resource lookup
resources             sprites, music, fonts
test                  headless rule tests, audio tests and the UI smoke test
tools                 dependency download, portable build (jpackage) and the icon generator
```
