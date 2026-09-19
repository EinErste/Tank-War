package game_objects.movables;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * The four enemy tank types of the original Battle City, with their stats.
 * <p>
 * In the original the type is stored in the high nibble of the entity byte
 * ({@code EntityTypeTable}, 4 entries per stage) and the score per type comes from
 * {@code EnemyScoreTable}: 100 / 200 / 300 / 400 points. Armour tanks carry an armour level
 * ({@code $E0-$E3}) and take four hits, changing colour with every hit.
 * <p>
 * This game has one enemy sprite set, so the types are told apart by recolouring that sprite
 * (see {@link #recolour}) - replace it with real sprites whenever you feel like drawing them.
 */
public enum EnemyType {

	/**
	 * Slow tank, slow bullet, one hit.
	 */
	BASIC(2, 2, 1, 100, 0.25, 1.00, 0.25),
	/**
	 * Fast tank, normal bullet, one hit.
	 */
	FAST(4, 2, 1, 200, 0.85, 1.00, 0.30),
	/**
	 * Normal speed, fast bullet, one hit.
	 */
	POWER(3, 3, 1, 300, 0.30, 0.80, 1.00),
	/**
	 * Slow tank, normal bullet, four hits - it changes shade with every hit.
	 */
	ARMOR(2, 2, 4, 400, 1.00, 0.25, 0.25);

	/**
	 * Enemy mix per stage, modelled on the composition tables of the original (which has thirty five
	 * stages, this game has ten): early stages are mostly basic tanks, armour shows up later.
	 * Order: BASIC, FAST, POWER, ARMOR.
	 */
	private static final int[][] STAGE_MIX = {
			{16, 10, 6, 0},
			{14, 12, 8, 2},
			{12, 12, 10, 4},
			{10, 12, 12, 6},
			{8, 12, 14, 8},
			{8, 10, 14, 10},
			{6, 10, 14, 12},
			{6, 8, 14, 14},
			{4, 8, 14, 16},
			{4, 6, 14, 18}
	};

	private final int speed;
	private final int bulletSpeed;
	private final int health;
	private final int points;
	private final double red;
	private final double green;
	private final double blue;

	EnemyType(int speed, int bulletSpeed, int health, int points, double red, double green, double blue) {
		this.speed = speed;
		this.bulletSpeed = bulletSpeed;
		this.health = health;
		this.points = points;
		this.red = red;
		this.green = green;
		this.blue = blue;
	}

	/**
	 * The speeds have to divide {@link game_content.GameField#BYTE}, the size of a tile: an enemy may
	 * only change its mind while it stands on a tile boundary, and a tank that is stopped by a wall
	 * has to be standing on one. The original moves one pixel per step, so its tanks always are; a
	 * speed of 5 on a 24 pixel tile, for example, leaves a tank stuck half a tile short of the wall
	 * with no way to ever re-decide.
	 *
	 * @return pixels per tick, in the same scale the player tank uses (3)
	 */
	public int getSpeed() {
		return speed;
	}

	/**
	 * @return bullet speed multiplier, the same scale {@code Bullet} uses for the player
	 */
	public int getBulletSpeed() {
		return bulletSpeed;
	}

	/**
	 * @return how many hits the tank takes before it is destroyed
	 */
	public int getHealth() {
		return health;
	}

	public int getPoints() {
		return points;
	}

	/**
	 * Picks the type of the next enemy to spawn for a stage.
	 *
	 * @param stageNumber 1 based stage number
	 * @param random      source of randomness
	 * @return the type to spawn
	 */
	public static EnemyType pickForStage(int stageNumber, Random random) {
		int[] mix = STAGE_MIX[Math.min(Math.max(stageNumber, 1), STAGE_MIX.length) - 1];
		int total = 0;
		for (int weight : mix) {
			total += weight;
		}
		int roll = random.nextInt(total);
		for (EnemyType type : values()) {
			roll -= mix[type.ordinal()];
			if (roll < 0) {
				return type;
			}
		}
		return BASIC;
	}

	/**
	 * Recolours a sprite of this type: the enemy sprite is green, so its green channel is the
	 * brightness of the body and drives all three channels of the result.
	 *
	 * @param source sprite to recolour, not modified
	 * @param health hits left, used to shade the armour tiers
	 * @return a recoloured copy
	 */
	public BufferedImage recolour(BufferedImage source, int health) {
		double shade = this == ARMOR ? 0.55 + 0.15 * health : 1;
		BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				int pixel = source.getRGB(x, y);
				int alpha = pixel >>> 24;
				if (alpha == 0) {
					result.setRGB(x, y, 0);
					continue;
				}
				int brightness = (pixel >> 8) & 0xFF;
				int r = channel(brightness * red * shade);
				int g = channel(brightness * green * shade);
				int b = channel(brightness * blue * shade);
				result.setRGB(x, y, alpha << 24 | r << 16 | g << 8 | b);
			}
		}
		return result;
	}

	private static int channel(double value) {
		return (int) Math.max(0, Math.min(255, Math.round(value)));
	}
}
