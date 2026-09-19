package resources_classes;

import java.util.Random;

/**
 * Sounds and music of the game.
 * <p>
 * Every clip is created once and cached, so playing a sound never touches the disk twice.
 * Music is streamed, sound effects are preloaded (they are short and may overlap).
 */
public class GameSound {

    public static final Random random = new Random();
    /**
     * double music volume from 0 to 1
     */
    public static final double battleMusicVolume = 0.7;
    public static final double menuMusicVolume = 1;
    public static final double explosionSoundVolume = 0.2;
    public static final double boostSoundVolume = 1;
    public static final double stopTimeSoundVolume = 1;

    private static final String[] BATTLE_MUSIC_PATHS = {
            "resources/music/battle/jojo_op1_8bit.mp3",
            "resources/music/battle/jojo_op2_8bit.mp3",
            "resources/music/battle/jojo_op3_8bit.mp3",
            "resources/music/battle/octopath_traveler_8bit.mp3",
            "resources/music/battle/megalovania_8bit.mp3",
            "resources/music/battle/tank!.mp3",
            "resources/music/battle/aot.mp3"
    };

    private static final String[] MENU_MUSIC_PATHS = {
            "resources/music/menu/six_days_war.mp3",
            "resources/music/menu/paint_it_black.mp3",
            "resources/music/menu/i'm nuclear.mp3",
            "resources/music/menu/the_real_folk_blues.mp3",
            "resources/music/menu/2+2.mp3"
    };

    private static final AudioClip[] battleMusic = createMusic(BATTLE_MUSIC_PATHS);
    private static final AudioClip[] menuMusic = createMusic(MENU_MUSIC_PATHS);

    /**
     * Selected once and cached: the "ZA WARUDO" time stop sound and its countdown.
     */
    public static final AudioClip[] stopTimeSound = {
            loadSound("resources/music/sounds/ZA_WARUDO.mp3", stopTimeSoundVolume),
            loadSound("resources/music/sounds/ZA_WARUDO_COUNT.mp3", stopTimeSoundVolume)
    };

    private static final AudioClip explosionSound =
            loadSound("resources/music/sounds/explosion.wav", explosionSoundVolume);
    private static final AudioClip boostSound =
            loadSound("resources/music/sounds/boost.mp3", boostSoundVolume);
    private static final AudioClip winMusic =
            loadSound("resources/music/game_end/we_are_the_champions.mp3", 1);
    private static final AudioClip defeatMusic =
            loadSound("resources/music/game_end/waste_land.mp3", 1);

    private static AudioClip lastBattleMusic;
    private static AudioClip lastMenuMusic;

    /**
     * @return the next battle music track (never the one that just played)
     */
    public static AudioClip nextBattleMusic() {
        lastBattleMusic = pickDifferent(battleMusic, lastBattleMusic);
        lastBattleMusic.setVolume(battleMusicVolume);
        return lastBattleMusic;
    }

    /**
     * @return the next menu music track (never the one that just played)
     */
    public static AudioClip nextMenuMusic() {
        lastMenuMusic = pickDifferent(menuMusic, lastMenuMusic);
        lastMenuMusic.setVolume(menuMusicVolume);
        return lastMenuMusic;
    }

    /**
     * @return the shared explosion clip; it may play several voices at the same time
     */
    public static AudioClip getExplosionSoundInstance() {
        return explosionSound;
    }

    public static AudioClip getBoostSoundInstance() {
        return boostSound;
    }

    public static AudioClip getWinMusicInstance() {
        return winMusic;
    }

    public static AudioClip getDefeatMusicInstance() {
        return defeatMusic;
    }

    private static AudioClip[] createMusic(String[] paths) {
        AudioClip[] tracks = new AudioClip[paths.length];
        for (int i = 0; i < paths.length; i++) {
            tracks[i] = new AudioClip(paths[i]);
        }
        return tracks;
    }

    /**
     * @param path   resource path
     * @param volume initial volume, 0 to 1
     * @return a preloaded clip, cached for the whole run of the game
     */
    private static AudioClip loadSound(String path, double volume) {
        AudioClip clip = new AudioClip(path, true);
        clip.setVolume(volume);
        return clip;
    }

    /**
     * @return a random track that is not the previously played one
     */
    private static AudioClip pickDifferent(AudioClip[] tracks, AudioClip previous) {
        AudioClip nextMusic;
        do {
            nextMusic = tracks[random.nextInt(tracks.length)];
        } while (tracks.length > 1 && nextMusic == previous);
        return nextMusic;
    }
}
