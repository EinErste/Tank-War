package game_objects.movables;

import game_content.GameField;
import game_objects.Destructible;

import java.util.ArrayList;

public abstract class Tank extends Movable implements Destructible {

	private final static int SPEED = GameField.SCALE;
	/**
	 * Pixels the tank moves per tick. The player uses {@link #SPEED}, enemy tanks use the speed of
	 * their type, like the original where a fast tank moves more often than a basic one.
	 */
	private final int speed;
	private int bulletSpeed = 2;
	/**
	 * Hits left before the tank is destroyed; only armour tanks have more than one.
	 */
	private int health = 1;
	/**
	 * Delay between bullets (in milliseconds)
	 */
	private int delay;
	private ArrayList<Bullet> bullets;
	private long bulletTimer;

	public Tank(int x, int y, Direction dir, int delay) {
		this(x, y, dir, delay, SPEED);
	}

	/**
	 * @param speed pixels to move per tick
	 */
	public Tank(int x, int y, Direction dir, int delay, int speed) {
		super(x, y, dir);
		this.delay = delay;
		this.speed = speed;
		init();
	}

	private void init() {
		bullets = new ArrayList<>();
	}

	@Override
	public void destroy() {
		setVisible(false);
	}

	/**
	 * A bullet hit the tank: armour tanks take a few hits, everything else dies right away.
	 */
	public void hit() {
		if (--health <= 0) {
			destroy();
		}
	}

	/**
	 * @return hits this tank takes before it is destroyed
	 */
	public int getHealth() {
		return health;
	}

	/**
	 * @param health hits this tank takes before it is destroyed
	 */
	protected void setHealth(int health) {
		this.health = health;
	}

	public void changeDirection(Direction dir) {
		//Slight move to fit the tank in the narrow corridors and other places
		if (Direction.isTurn(currentDir, dir)) {
			switch (currentDir) {
				case WEST:
				case EAST:
					setX(round(getX()));
					break;
				case NORTH:
				case SOUTH:
					setY(round(getY()));
					break;
			}
		}
		currentDir = dir;

		switch (dir) {
			case WEST:
				image = directions[0];
				dx = -speed;
				dy = 0;
				break;
			case EAST:
				image = directions[1];
				dx = speed;
				dy = 0;
				break;
			case NORTH:
				image = directions[2];
				dy = -speed;
				dx = 0;
				break;
			case SOUTH:
				image = directions[3];
				dy = speed;
				dx = 0;
				break;
		}
	}

	/**
	 * @return the direction this tank is driving in
	 */
	public Direction getDirection() {
		return currentDir;
	}

	/**
	 * @return how many pixels this tank moves per tick
	 */
	public int getSpeed() {
		return speed;
	}

	/**
	 * Get bullets that tank has shot
	 *
	 * @return bullets
	 */
	public ArrayList<Bullet> getBullets() {
		return bullets;
	}

	/**
	 * PlayerTank fires a bullet. It should be copied to GameField, where we can control their collision
	 */
	public void fire() {
		long timePassed = System.currentTimeMillis() - bulletTimer;
		int delay = bullets.isEmpty() ? this.delay / 3 : this.delay;
		if (timePassed < delay)
			return;
		int x, y;
		switch (currentDir) {
			case WEST:
				x = getX() - Bullet.HEIGHT;
				y = getY() + (getHeight() - Bullet.WIDTH) / 2;
				break;
			case EAST:
				x = getX() + getWidth();
				y = getY() + (getHeight() - Bullet.WIDTH) / 2;
				break;
			case NORTH:
				x = getX() + (getWidth() - Bullet.WIDTH) / 2;
				y = getY() - Bullet.HEIGHT;
				break;
			default:
				x = getX() + (getWidth() - Bullet.WIDTH) / 2;
				y = getY() + getHeight();
				break;
		}
		if (this instanceof EnemyTank)
			bullets.add(new EnemyBullet(x, y, currentDir, bulletSpeed));
		else
			bullets.add(new Bullet(x, y, currentDir, bulletSpeed));
		bulletTimer = System.currentTimeMillis();
	}

	protected void setBulletSpeed(int bulletSpeed) {
		this.bulletSpeed = bulletSpeed;
	}

	public void setDelay(int delay) {
		this.delay = delay;
	}

	/**
	 * Rounds the coordinate so it fits into the game grid
	 *
	 * @param num needed coordinate
	 * @return rounded coordinate
	 */
	static int round(double num) {
		num /= GameField.BYTE;
		num = Math.round(num) * GameField.BYTE;
		return (int) num;
	}

}
