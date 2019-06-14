package resources_classes;

import javafx.scene.media.AudioClip;
import java.nio.file.Paths;
import java.util.Random;

public class GameSound{

    /**
     * double music volume from 0 to 1
     */
    public static final double battleMusicVolume = 0.7;
    public static final double menuMusicVolume = 1;
    public static final double explosionSoundVolume = 0.2;
    public static final double boostSoundVolume = 1;

    public static final String[] battleMusicPath = {
            "resources/music/battle/jojo_op1_8bit.mp3",
            "resources/music/battle/jojo_op2_8bit.mp3",
            "resources/music/battle/jojo_op3_8bit.mp3",
            "resources/music/battle/octopath_traveler_8bit.mp3",
            "resources/music/battle/megalovania_8bit.mp3"

    };

    public static final String[] menuMusicPath = {
            "resources/music/menu/six_days_war.mp3",
            "resources/music/menu/paint_it_black.mp3",
            "resources/music/menu/i'm nuclear.mp3",
            "resources/music/menu/waste_land.mp3",
            "resources/music/menu/2+2.mp3"
    };

    public static final Random random = new Random();
    private static String lastBattleMusic;
    private static String lastMenuMusic;

    public static AudioClip getMenuMusicInstance(){
        String nextMusic = "";
        do {
            nextMusic = menuMusicPath[random.nextInt(menuMusicPath.length)];
        } while (nextMusic.equals(lastMenuMusic));
        lastMenuMusic = nextMusic;
        return new AudioClip(Paths.get(nextMusic).toUri().toString());
    }

    public static AudioClip getBattleMusicInstance(){
        String nextMusic = "";
        do {
            nextMusic = battleMusicPath[random.nextInt(battleMusicPath.length)];
        } while (nextMusic.equals(lastBattleMusic));
        lastBattleMusic = nextMusic;
        return new AudioClip(Paths.get(nextMusic).toUri().toString());
    }

    public static AudioClip getStopTimeSoundInstance(){
        return new AudioClip(Paths.get("resources/music/sounds/ZA_WARUDO.mp3").toUri().toString());
    }

    public static AudioClip getExplosionSoundInstance(){
        return new AudioClip(Paths.get("resources/music/sounds/explosion.wav").toUri().toString());
    }
    public static AudioClip getBoostSoundInstance(){
        AudioClip audioClip = new AudioClip(Paths.get("resources/music/sounds/boost.mp3").toUri().toString());
        audioClip.setVolume(boostSoundVolume);
        return audioClip;
    }


}
