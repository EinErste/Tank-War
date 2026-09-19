package game_objects.movables;

import java.util.Random;

public class EnemyTank extends Tank {

	/**
	 * The original lets an enemy shoot on a 1 in 32 chance per frame and limits it to one bullet in
	 * the air, which is what the delay between bullets in this game stands in for: a small delay here
	 * means "as soon as the previous bullet is gone", instead of the four seconds it used to be.
	 */
	private final static int FIRE_DELAY = 250;

	private final EnemyType type;
	private final EnemyTankBrain brain;
	/**
	 * The untouched sprites: recolouring always starts from these, so armour tiers do not stack.
	 */
	private java.awt.image.BufferedImage[] plainSprites;

	public EnemyTank(int x, int y, Direction dir) {
		this(x, y, dir, EnemyType.BASIC, EnemyTankBrain.Settings.DEFAULT);
	}

	public EnemyTank(int x, int y, Direction dir, EnemyType type) {
		this(x, y, dir, type, EnemyTankBrain.Settings.DEFAULT);
	}

	/**
	 * @param type     enemy type, which sets speed, bullet speed and how many hits it takes
	 * @param settings the knobs for the AI, which is what the difficulty changes
	 */
	public EnemyTank(int x, int y, Direction dir, EnemyType type, EnemyTankBrain.Settings settings) {
		this(x, y, dir, type, new EnemyTankBrain(new Random(), EnemyTankBrain.Goal.EAGLE, settings));
	}

	/**
	 * @param type  enemy type, which sets speed, bullet speed and how many hits it takes
	 * @param brain decision making of this tank, so a test can hand in a seeded one
	 */
	public EnemyTank(int x, int y, Direction dir, EnemyType type, EnemyTankBrain brain) {
		super(x, y, dir, FIRE_DELAY, type.getSpeed());
		this.type = type;
		this.brain = brain;
		init();
	}

	private void init() {
		loadImage("resources/sprites/enemy_tank/tank_%s.png");
		//keep the untouched sprites before any colour is applied, recolouring always starts from these
		plainSprites = directions.clone();
		changeDirection(Direction.SOUTH);
		getImageDimensions();
		setHealth(type.getHealth());
		setBulletSpeed(type.getBulletSpeed());
		recolour();
	}

	public EnemyType getType() {
		return type;
	}

	public EnemyTankBrain getBrain() {
		return brain;
	}

	/**
	 * Armour tanks change colour with every hit, like the original's four armour levels.
	 */
	@Override
	public void hit() {
		super.hit();
		if (isVisible()) {
			recolour();
		}
	}

	/**
	 * Applies the colour of this type to the four direction sprites. The game has a single enemy
	 * sprite set, so the types are told apart by recolouring it.
	 */
	private void recolour() {
		for (int i = 0; i < directions.length; i++) {
			directions[i] = type.recolour(plainSprites[i], getHealth());
		}
		image = directions[currentDir.ordinal()];
	}
}
