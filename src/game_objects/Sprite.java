package game_objects;

import game_content.GameField;
import resources_classes.ResourceFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class Sprite {
	private int x;
	private int y;
	private int width;
	private int height;
	private boolean visible;
	protected BufferedImage image;

	static {
		//Decode images in memory: ImageIO would otherwise copy every sprite into a temporary cache
		//file first, which is slower and fails if that cache is removed while the image is read
		ImageIO.setUseCache(false);
	}

	public Sprite(int x, int y) {
		this.x = x;
		this.y = y;
		visible = true;
	}

	protected void getImageDimensions() {
		width = image.getWidth(null);
		height = image.getHeight(null);
	}

	/**
	 * Load image that will serve as a sprite
	 *
	 * @param imageName name of an image
	 */
	protected void loadImage(String imageName) {
		image = readImage(imageName);
		image = scale(image);
	}

	/**
	 * Reads an image from the resources folder.
	 * <p>
	 * A missing or unreadable file used to end up as a {@link NullPointerException} deep inside the
	 * rendering code; now it fails immediately with the path that could not be resolved.
	 *
	 * @param imageName name (path) of an image
	 * @return the loaded image, never null
	 */
	protected static BufferedImage readImage(String imageName) {
		BufferedImage loaded;
		File file = ResourceFile.find(imageName);
		try {
			if (file != null) {
				//straight from disk: no temporary ImageIO cache file involved
				loaded = ImageIO.read(file);
			} else {
				try (InputStream stream = ResourceFile.open(imageName)) {
					loaded = ImageIO.read(stream);
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Cannot load image '" + imageName + "'. " + e.getMessage(), e);
		}
		if (loaded == null) {
			throw new IllegalStateException("Cannot decode image '" + imageName
					+ "': the file is not a readable image format");
		}
		return loaded;
	}

	/**
	 * scale image
	 *
	 * @param sbi image to scale
	 * @return scaled image
	 */
	protected static BufferedImage scale(BufferedImage sbi) {
		int sc = GameField.SCALE;
		BufferedImage dbi = null;
		if (sbi != null) {
			dbi = new BufferedImage(sbi.getWidth() * sc, sbi.getHeight() * sc, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = dbi.createGraphics();
			AffineTransform at = AffineTransform.getScaleInstance(sc, sc);
			g.drawRenderedImage(sbi, at);
			g.dispose();
		}
		return dbi;
	}

	public Image getImage() {
		return image;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	protected void setX(int x) {
		this.x = x;
	}

	protected void setY(int y) {
		this.y = y;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public Rectangle getBounds() {
		return new Rectangle(x, y, width, height);
	}
}
