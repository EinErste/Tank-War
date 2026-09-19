package game_objects.movables;

import game_objects.Sprite;

import java.awt.*;
import java.awt.image.BufferedImage;

public abstract class Movable extends Sprite {

	protected BufferedImage[] directions;
	protected int dx, dy;
	/**
	 * Directions images in such an order: west, east, north, south
	 */
	protected Direction currentDir;

	public Movable(int x, int y, Direction dir) {
		super(x, y);
		currentDir = dir;
	}

	/**
	 * Load images for different directions. String should be in such form: "path/image_name_%s.png", where %s is a place where direction goes
	 * @param imageName name of an image
	 */
	@Override
	protected void loadImage(String imageName) {
		directions = new BufferedImage[4];
		for (int i = 0; i < 4; i++) {
			String dir = Direction.values()[i].toString().toLowerCase();
			String str = String.format(imageName, dir);
			try {
				directions[i] = scale(readImage(str));
			} catch (IllegalStateException e) {
				throw new IllegalStateException("Cannot load the " + dir + " sprite of '" + imageName + "'. "
						+ e.getMessage(), e);
			}
		}
		image = directions[currentDir.ordinal()];
	}

	/**
	 * Method that moves the object on the board. Should be used only when check for collision was already made.
	 */
	public void move() {
		setX(dx + getX());
		setY(dy + getY());
	}

	/**
	 * Method we use to see if object can move. If it theoretically moved and collided with something, we don't move;
	 *
	 * @return Returns bounds that tank <u><b>would have</b></u> if it moved at this moment.
	 */
	public Rectangle getTheoreticalBounds() {
		Rectangle rect = getBounds();
		rect.x += dx;
		rect.y += dy;
		return rect;
	}

}
