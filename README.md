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

Arrows move the tank, any other key fires. Destroy every enemy tank of a stage to advance, do not let
the flag (your base) get shot, and pick up the power-ups for an upgrade, an extra life or a time stop.
The menu lets you pick a stage (all ten) and a difficulty.

## Difficulty

The menu has a difficulty chooser next to the stage chooser. It changes how many enemies come, how hard
they push and how many lives you get:

| | EASY | NORMAL | HARD |
|---|---|---|---|
| enemies on screen | 4 | 6 | 8 |
| enemies per stage | 20 | 32 | 40 |
| lives | 5 | 3 | 2 |
| enemy fire roll (1 in N per tick) | 48 | 32 | 22 |
| how long they push towards the base | short | medium | long |
| fast/power/armour share of the mix | halved | as designed | 1.6x |

All of it lives in `src/game_content/Difficulty.java` (counts and lives) and in
`EnemyTankBrain.Settings` (the AI knobs, explained there). Adding a fourth difficulty is one line in
the enum.

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
* `EnemyAITest` - the enemy AI rules and the difficulty settings: it only re-decides on a tile
  boundary and on a 1 in 16 roll,
  rotates its goal between base/wander/player, takes the dominant axis towards its target, fires on a
  1 in 32 roll without aiming, shoots through what it can break, goes around what it cannot, never
  reverses into its own tracks, and the four types have their documented stats.
* `MenuBackgroundTest` - screenshots the menu and fails if it comes out blank (that is how the white
  menu of the packaged build was caught). Writes `test/out/menu-screenshot.png`.
* `SmokeTest` - drives the real UI the way a player does (menu, difficulty and stage choosers, Play,
  level 1, stage transition, game over, Menu, stage 10 on hard) and asserts that the menu music stops
  when a level starts and that hard really gives two lives. It opens a window, so it is skipped on a
  headless machine.

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

The enemies start from the behaviour of the original Battle City (Famicom), see
`src/game_objects/movables/EnemyTankBrain.java` for the mapping to the original's routines:

* an enemy only re-decides while it stands on a tile boundary and only on a 1 in 16 roll
  (`EntityMovementAI`), and it skips its move that tick - otherwise it just drives on,
* the goal rotates: chase the base, then wander randomly, then chase the player (`SpeedCtrlMove`),
  which is the original's "random detour, but eventually it goes for your base",
* a chase takes the axis with the larger distance to the target first (`CalcDirToTarget`),
* it fires on a 1 in 32 roll per tick, without aiming (`EnemyFireTick`),
* it shoots the bricks in its way, which is how the original's tanks dig towards the base.

Four things are different, because the original's tanks drive through each other while ours collide,
and because its wall-bumping reads as jitter here:

* a blocked enemy does not reverse 25% of the time (`EntityMovementBlocked`) - it turns a free corner
  and only backs out when both sides are blocked, so it does not undo its own progress,
* it picks the first direction that is actually free, and only faces a wall when nothing else is open,
* it commits to a direction for half a second before it may re-decide,
* it goes around a friend in the way quickly, and `NO_PROGRESS_TICKS` without progress always forces a
  way out of a corner.

Measured on stage 2 over the same window, against the previous version: path efficiency (net progress
divided by distance walked) went from 0.20 to 0.47, direction reversals from 10 per tank to 3, and net
progress from 1175 px to 3069 px.

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
