package game_content;

import game_objects.movables.EnemyTankBrain;

/**
 * Difficulty of a run: how many enemies come, how hard they push and how many lives the player has.
 * <p>
 * Everything that makes the enemies easier or harder is in one place here, and it is passed down to
 * {@code GameField} and to {@link EnemyTankBrain.Settings}, so a new difficulty is a single line in
 * this enum rather than a hunt through the code.
 * <p>
 * The enemy counts follow the original: it puts four tanks on screen at a time and sends twenty per
 * stage, which is what {@link #NORMAL} is roughly scaled around.
 */
public enum Difficulty {

	/**
	 * Few enemies, they wander more than they push, and they shoot about half as often.
	 */
	EASY(4, 20, 5, 0.5, new EnemyTankBrain.Settings(16, 48, 35, 100, 8, 75, 90, 300)),
	/**
	 * The game as it was balanced before this existed.
	 */
	NORMAL(6, 32, 3, 1.0, new EnemyTankBrain.Settings(16, 32, 25, 150, 6, 75, 150, 300)),
	/**
	 * More enemies on screen, longer pushes towards the base, quicker and more accurate shooting.
	 */
	HARD(8, 40, 2, 1.6, new EnemyTankBrain.Settings(16, 22, 18, 200, 5, 75, 200, 240));

	private final int enemiesOnScreen;
	private final int enemiesPerStage;
	private final int lives;
	private final double eliteBias;
	private final EnemyTankBrain.Settings brain;

	Difficulty(int enemiesOnScreen, int enemiesPerStage, int lives, double eliteBias,
			EnemyTankBrain.Settings brain) {
		this.enemiesOnScreen = enemiesOnScreen;
		this.enemiesPerStage = enemiesPerStage;
		this.lives = lives;
		this.eliteBias = eliteBias;
		this.brain = brain;
	}

	/**
	 * @return how many enemy tanks may be on the field at the same time
	 */
	public int getEnemiesOnScreen() {
		return enemiesOnScreen;
	}

	/**
	 * @return how many enemy tanks a stage sends before it is cleared
	 */
	public int getEnemiesPerStage() {
		return enemiesPerStage;
	}

	/**
	 * @return how many times the player may be destroyed before the game is lost
	 */
	public int getLives() {
		return lives;
	}

	/**
	 * @return multiplier for the weights of the fast, power and armour tanks in a stage's mix; basic
	 *         tanks are not affected, so easy stages stay full of them
	 */
	public double getEliteBias() {
		return eliteBias;
	}

	/**
	 * @return the knobs for the enemy AI
	 */
	public EnemyTankBrain.Settings getBrainSettings() {
		return brain;
	}
}
