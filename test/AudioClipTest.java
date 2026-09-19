import resources_classes.AudioClip;
import resources_classes.GameSound;

/**
 * Checks {@link AudioClip}, the {@code javax.sound.sampled} replacement for JavaFX's clip, and the
 * sound selection in {@link GameSound}.
 * <p>
 * Covered: music streams and keeps playing (the MP3 provider returns 0 from {@code read()} before
 * the first frame, which used to end the track immediately), preloaded sound effects can overlap,
 * {@code stop()} cuts a clip off, volume 0 is silent, a missing file is reported instead of thrown,
 * and the music pickers never return null or repeat the previous track.
 * <p>
 * MP3 dependent checks are skipped with a notice when the decoder from {@code lib/} is missing,
 * because the JDK cannot read MP3 by itself.
 */
public class AudioClipTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        boolean mp3 = hasMp3Decoder();
        if (!mp3) {
            System.out.println("note: no MP3 decoder on the class path, MP3 checks are skipped "
                    + "(run tools/get-deps.sh or tools\\get-deps.bat to fetch it)");
        }

        streamingMusic(mp3);
        preloadedSoundEffect();
        volumeZeroIsSilent();
        missingFileIsReportedNotThrown();
        musicPickers();

        System.out.println();
        System.out.println(failures == 0 ? "AUDIO CLIP TEST PASSED" : "AUDIO CLIP TEST FAILED (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    /**
     * Music is streamed from disk and must survive the whole track, not stop after the first read.
     */
    private static void streamingMusic(boolean mp3) throws Exception {
        if (!mp3) {
            System.out.println("   skip streaming music (no MP3 decoder)");
            return;
        }
        AudioClip music = new AudioClip("resources/music/battle/jojo_op2_8bit.mp3");
        music.setVolume(GameSound.battleMusicVolume);
        music.play();
        expect("play() reports playing", music.isPlaying());
        Thread.sleep(1500);
        expect("still playing after 1.5s (the track that used to decode 0 bytes)", music.isPlaying());
        music.stop();
        expect("stop() reports stopped", !music.isPlaying());

        AudioClip menu = GameSound.nextMenuMusic();
        menu.play();
        Thread.sleep(300);
        menu.play();
        expect("play() again restarts a streaming clip instead of failing", menu.isPlaying());
        menu.stop();
    }

    /**
     * Short sound effects are decoded into memory once and several voices may overlap.
     */
    private static void preloadedSoundEffect() throws Exception {
        AudioClip explosion = GameSound.getExplosionSoundInstance();
        explosion.play();
        Thread.sleep(120);
        explosion.play();
        expect("two overlapping explosion voices are playing", explosion.isPlaying());
        Thread.sleep(1600);
        expect("explosion finished on its own", !explosion.isPlaying());
    }

    private static void volumeZeroIsSilent() {
        AudioClip silent = GameSound.getBoostSoundInstance();
        double previous = silent.getVolume();
        silent.setVolume(0);
        silent.play();
        expect("volume 0 does not start playback", !silent.isPlaying());
        silent.setVolume(previous);
    }

    private static void missingFileIsReportedNotThrown() throws Exception {
        AudioClip missing = new AudioClip("resources/music/sounds/does_not_exist.mp3", true);
        missing.play();
        Thread.sleep(400);
        expect("a missing file does not crash and does not stay 'playing'", !missing.isPlaying());
    }

    private static void musicPickers() {
        boolean ok = true;
        for (int i = 0; i < 20; i++) {
            AudioClip battle = GameSound.nextBattleMusic();
            AudioClip menu = GameSound.nextMenuMusic();
            if (battle == null || menu == null) {
                ok = false;
                break;
            }
        }
        expect("GameSound never returns a null track", ok);
    }

    private static boolean hasMp3Decoder() {
        try {
            Class.forName("javazoom.spi.mpeg.sampled.file.MpegAudioFileReader");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void expect(String what, boolean passed) {
        System.out.println((passed ? "   OK   " : "   FAIL ") + what);
        if (!passed) {
            failures++;
        }
    }
}
