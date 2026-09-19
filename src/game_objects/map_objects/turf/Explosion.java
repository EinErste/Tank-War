package game_objects.map_objects.turf;

import game_content.GameField;
import game_objects.Sprite;
import resources_classes.GameSound;

import java.awt.image.BufferedImage;

public class Explosion extends Sprite {

	private final static int DELAY = GameField.TENTH_OF_SECOND;

	/**
	 * The three animation frames, loaded and scaled exactly once for the whole run of the game.
	 * Every explosion used to call {@code GameSound.getExplosionSoundInstance()} on every frame and
	 * re-read the images from disk while animating, which decoded an audio file and three PNGs per
	 * explosion and per frame.
	 */
	private final static BufferedImage[] FRAMES = {
			scale(readImage("resources/sprites/map/explosion1.png")),
			scale(readImage("resources/sprites/map/explosion2.png")),
			scale(readImage("resources/sprites/map/explosion3.png"))
	};

	private int i = 0;

	public Explosion(int x, int y) {
		super(x, y);

		init();
	}

	private void init() {
		image = FRAMES[0];
		getImageDimensions();
	}

	public void cycle() {
		++i;
		if (i == 1) {
			GameSound.getExplosionSoundInstance().play();
		} else if (i == DELAY) {
			image = FRAMES[1];
		} else if (i == DELAY * 2) {
			image = FRAMES[2];
		} else if (i >= DELAY * 3) {
			setVisible(false);
		}
	}
}
