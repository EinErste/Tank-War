package game_content;

import game_objects.Destructible;
import game_objects.map_objects.MapObject;
import game_objects.map_objects.impassables.Base;
import game_objects.map_objects.powerups.PowerUp;
import game_objects.map_objects.turf.Explosion;
import game_objects.movables.*;
import map_tools.Level;
import map_tools.Map;
import resources_classes.GameSound;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class GameField extends JPanel implements Runnable {

	/**
	 * Scale (for resize)
	 */
	public static final int SCALE = 3;
	/**
	 * Enemy count
	 */
	public static final int ENEMY_COUNT = 32;
	/**
	 * Maximum number of enemies on screen
	 */
	public static final int MAX_ENEMIES = 8;
	/**
	 * Size of the game map relative to the tile size. Actually its twice as small relative to the Tank because every map tile is divided into four destructible parts
	 */
	public static final int MAP_SIZE = 26;
	/**
	 * HOLY CONSTANT - Size of the one tile
	 */
	public static final int BYTE = 8 * SCALE;
	/**
	 * Dimensions of a game field (height and width are always equal)
	 */
	public static final int FIELD_DIMENSIONS = MAP_SIZE * BYTE;
	/**
	 * Delay in miliseconds
	 */
	public static final int DELAY = 20;
	/**
	 * How many frames it takes for tenth second to pass
	 */
	public static final int TENTH_OF_SECOND = 100 / DELAY;

	private Set<Explosion> explosions;
	private Set<Tank> tanks;
	private Set<Bullet> bullets;
	private Set<PowerUp> powerUps;
	private Base base;
	private Map map;
	private PlayerTank playerTank;
	private int tankAmount;

	private Thread animator;
	private GameFieldPanel gameFieldPanel;
	private Timer endTimer;
	private Timer spawnTimer;
	private Timer timeStopTimer;
	private Random rand;
	/**
	 * Written by the Swing timers, read by the animator thread
	 */
	private volatile boolean timeStopped;
	/**
	 * Set by {@link #dispose()}: the level is over, nothing may run any more
	 */
	private volatile boolean disposed;

	public GameField(Level level, GameFieldPanel gameFieldPanel) {
		this.gameFieldPanel = gameFieldPanel;
		initGameField(level);
	}

	/**
	 * Initialization method
	 */
	private void initGameField(Level level) {
		setBackground(Color.BLACK);
		setPreferredSize(new Dimension(FIELD_DIMENSIONS, FIELD_DIMENSIONS));
		setFocusable(true);
		initMap(level);

	}

	private void initMap(Level level) {
		addKeyListener(new Adapter());
		map = Map.getLevelMap(level);
		base = map.getBase();
		explosions = ConcurrentHashMap.newKeySet();
		tanks = ConcurrentHashMap.newKeySet();
		spawnPlayerTank();
		rand = new Random();
		bullets = ConcurrentHashMap.newKeySet();
		powerUps = ConcurrentHashMap.newKeySet();
		spawnTimer = new Timer(2000, e -> spawnEnemyTank());
		spawnTimer.start();
		spawnEnemyTank();
	}

	private void spawnPlayerTank() {
		tanks.removeIf(tank -> tank instanceof PlayerTank);
		playerTank = new PlayerTank(8 * BYTE, 24 * BYTE, Direction.NORTH);
		tanks.add(playerTank);
	}

	private void spawnEnemyTank() {
		if (tanks.size() < MAX_ENEMIES+1 && tankAmount < ENEMY_COUNT) {
			List<Integer> list = new ArrayList<>();
			list.add(0);
			list.add(BYTE*12);
			list.add(BYTE*12*2);
			Collections.shuffle(list);
			for (int x : list ) {
				if (noTankAt(x, 0)) {
					tankAmount++;
					tanks.add(new EnemyTank(x, 0, Direction.SOUTH));
					break;
				}
			}
		}
	}

	private boolean noTankAt(int x, int y) {
		for (Tank t : tanks) {
			if (t.getBounds().intersects(x, y, 2 * BYTE, 2 * BYTE))
				return false;
		}
		return true;
	}


	/**
	 * Overriden method. Used to start the Thread when it's added to the JFrame
	 */
	@Override
	public void addNotify() {
		super.addNotify();

		//The panel can be added to a container more than once, the game loop must not be duplicated
		if (animator == null || !animator.isAlive()) {
			animator = new Thread(this, "game-loop");
			animator.setDaemon(true);
			animator.start();
		}
	}

	/**
	 * All actions that should be performed every game tick
	 */
	private void cycle() {
		checkTankRespawns();
		checkWinCondtions();
		checkAllTanksCollision();
		updateBullets();
		addPowerUps();
		//Synchronizing drawing because of buffering
		Toolkit.getDefaultToolkit().sync();
	}

	private void addPowerUps() {
		if(rand.nextDouble() < 0.001)
			powerUps.add(new PowerUp(rand.nextInt(25)*BYTE,rand.nextInt(25)*BYTE));
		for(PowerUp p : powerUps) {
			if (p.getBounds().intersects(playerTank.getBounds())) {
				p.setVisible(false);
				switch (p.getType()) {
					case UPGRADE:
						GameSound.getBoostSoundInstance().play();
						playerTank.upgrade();
						break;
					case HEALTH:
						GameSound.getBoostSoundInstance().play();
						gameFieldPanel.playerRespawnGained();
						break;
					case TIME_STOP:
						stopTime();
						break;
				}
			}
		}

	}

	/**
	 * Freezes every enemy tank for a few seconds: first the "ZA WARUDO" shout, then the countdown,
	 * after which the music comes back.
	 */
	private void stopTime(){
		if (timeStopTimer != null) {
			timeStopTimer.stop();
		}
		GameSound.stopTimeSound[1].stop();
		GameSound.stopTimeSound[0].stop();
		GameSound.stopTimeSound[0].play();
		gameFieldPanel.musicStop();

		Timer shoutTimer = new Timer(4000, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (disposed) {
					return;
				}
				GameSound.stopTimeSound[1].stop();
				GameSound.stopTimeSound[1].play();
				timeStopped = true;
				gameFieldPanel.requestFocusField();
				timeStopTimer = new Timer(5700, k -> {
					timeStopped = false;
					if (!disposed) {
						gameFieldPanel.musicPlay();
					}
				});
				timeStopTimer.setRepeats(false);
				timeStopTimer.start();
			}
		});
		shoutTimer.setRepeats(false);
		shoutTimer.start();
	}

	/**
	 * Ends this level for good: stops the game loop, the enemy spawner and every pending timer.
	 * Without this the level that was left behind kept spawning enemy tanks forever.
	 */
	public void dispose() {
		disposed = true;
		if (spawnTimer != null) {
			spawnTimer.stop();
		}
		if (endTimer != null) {
			endTimer.stop();
		}
		if (timeStopTimer != null) {
			timeStopTimer.stop();
		}
		if (animator != null) {
			animator.interrupt();
		}
	}

	private void checkAllTanksCollision() {
		for (Tank t : tanks) {
			if(! (t instanceof EnemyTank && timeStopped) ) {
				if (t instanceof EnemyTank) {
					t.fire();
					if (rand.nextDouble() < 0.02)
						t.changeDirection(Direction.values()[rand.nextInt(Direction.values().length)]);
				}
				if (!checkWallCollisions(t) && !checkTankCollisions(t)) {
					t.move();
				} else if(t instanceof EnemyTank){
					for (int i = 0; i < 8; i++) {
						t.changeDirection(Direction.values()[rand.nextInt(Direction.values().length)]);
						if(!checkWallCollisions(t) && !checkTankCollisions(t))
							break;
					}
				}
			}
		}
	}

	private void checkTankRespawns(){
		if (!playerTank.isVisible() && endTimer==null) {
			gameFieldPanel.playerTankDestroyed();
			if (gameFieldPanel.getRespawns()!=-1){
				spawnPlayerTank();
			} else {
				endTimer = new Timer(3000, new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						gameFieldPanel.gameLost();
					}
				});
				endTimer.setRepeats(false);
				endTimer.start();
			}
		}
	}

	/**
	 * Checking collisions of tanks with other tanks on the map
	 */
	private boolean checkTankCollisions(Tank tank) {
		Rectangle nextBounds = tank.getTheoreticalBounds();
		Rectangle currentBounds = tank.getBounds();
		for (Tank t : tanks) {
			//A tank that already overlaps another one (possible after a grid snap) has to be able to back out
			if (t != tank && nextBounds.intersects(t.getBounds()) && !currentBounds.intersects(t.getBounds()))
				return true;
		}
		return false;
	}

	/**
	 * Checking collisions of tanks with other objects on the map
	 */
	private boolean checkWallCollisions(Tank tank) {
		Rectangle nextBounds = tank.getTheoreticalBounds();
		Rectangle currentBounds = tank.getBounds();
		for (MapObject mo : map) {
			//Only obstacles the tank is not standing in already block the move: turning snaps a tank
			//onto the grid and could otherwise leave it stuck inside a wall for the rest of the level
			if (mo.isCollidable() && nextBounds.intersects(mo.getBounds()) && !currentBounds.intersects(mo.getBounds()))
				return true;
		}
		return !this.getBounds().contains(nextBounds);

	}

	//Timer must be initialized only one time or duplicate menu appears
	private void checkWinCondtions() {
		if (base.isDefeated() && endTimer == null) {
			endTimer = new Timer(1000, e -> gameFieldPanel.gameLost());
			endTimer.setRepeats(false);
			endTimer.start();
		}
	}

	private void updateBullets() {
		for (Tank t : tanks) {
			t.getBullets().removeIf(bullet -> !bullet.isVisible());
			bullets.addAll(t.getBullets());
			bullets.removeIf(bullet -> !bullet.isVisible());
		}
		for (Bullet b : bullets) {
			if (!b.isVisible())
				continue;
			Rectangle bBounds = b.getTheoreticalBounds();
			for (Bullet b1 : bullets) {
				if (b != b1 && b1.isVisible() && bBounds.intersects(b1.getBounds())) {
					b.destroy();
					b1.destroy();
					explosions.add(b.getExplosion());
					explosions.add(b1.getExplosion());
				}
			}
			for (MapObject mo : map) {
				if (b.isVisible() && mo instanceof Destructible && bBounds.intersects(mo.getBounds())) {
					((Destructible) mo).destroy();
					b.destroy();
					explosions.add(b.getExplosion());
				}

			}
			for (Tank t : tanks) {
				if (b.isVisible() && bBounds.intersects(t.getBounds())) {
					b.destroy();
					if(!(b instanceof EnemyBullet) || t instanceof PlayerTank) {
						t.destroy();
						explosions.add(b.getExplosion());
						if (t instanceof EnemyTank) {
							gameFieldPanel.enemyTankDestroyed();
						}
					}
				}
			}
			if (b.isVisible()) {
				if (!this.getBounds().contains(bBounds))
					b.destroy();
				else
					b.move();
			}
		}

	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		drawTanks(g);
		drawMapObjects(g);
		drawBullets(g);
		drawPowerUps(g);
		drawExplosion(g);
	}

	private void drawPowerUps(Graphics g) {
		powerUps.removeIf(powerUp -> !powerUp.isVisible());
		for(PowerUp p: powerUps) {
			if(p.isVisible())
				g.drawImage(p.getImage(),p.getX(),p.getY(),this);
		}
	}

	/**
	 * Draw bullets on graphics
	 *
	 * @param g Graphics we draw on
	 */
	private void drawBullets(Graphics g) {
		for (Bullet b : bullets) {
			if (b.isVisible()) {
				g.drawImage(b.getImage(), b.getX(), b.getY(), this);
			}
		}
	}

	/**
	 * Draw player tank on graphics
	 *
	 * @param g Graphics we draw on
	 */
	private void drawTanks(Graphics g) {
		tanks.removeIf(enemyTank -> !enemyTank.isVisible());
		for (Tank tank : tanks) {
			g.drawImage(tank.getImage(), tank.getX(), tank.getY(), this);
		}
	}

	/**
	 * Draw all objects that are on the map on graphics
	 *
	 * @param g Graphics we draw on
	 */
	private void drawMapObjects(Graphics g) {
		map.removeIf(mapObject -> !mapObject.isVisible());
		for (MapObject mo : map)
			if (mo.isVisible())
				g.drawImage(mo.getImage(), mo.getX(), mo.getY(), this);

	}

	/**
	 * Draw explosions on graphics
	 *
	 * @param g Graphics we draw on
	 */
	private void drawExplosion(Graphics g) {
		explosions.removeIf(explosion -> !explosion.isVisible());
		for (Explosion ex : explosions) {
			g.drawImage(ex.getImage(), ex.getX(), ex.getY(), this);
			ex.cycle();
		}
	}

	/**
	 * Method for running the game in a thread for continuous and uninterrupted game performance. We use a while-loop to perform some actions specified in a cycle method and then repaint the whole game.
	 */
	@Override
	public void run() {

		long beforeTime = System.currentTimeMillis();

		while (!Thread.currentThread().isInterrupted() && !disposed) {
			try {
			cycle();
			repaint();

			long timeDiff = System.currentTimeMillis() - beforeTime;
			long sleep = DELAY - timeDiff;

			if (sleep < 0)
				sleep = 2;

			Thread.sleep(sleep);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}

			beforeTime = System.currentTimeMillis();
		}
	}

	private class Adapter extends KeyAdapter {

		@Override
		public void keyTyped(KeyEvent e) {
			playerTank.fire();
		}

		@Override
		public void keyPressed(KeyEvent e) {
			int key = e.getKeyCode();

			switch (key) {
				case KeyEvent.VK_LEFT:
					playerTank.changeDirection(Direction.WEST);
					break;
				case KeyEvent.VK_RIGHT:
					playerTank.changeDirection(Direction.EAST);
					break;
				case KeyEvent.VK_UP:
					playerTank.changeDirection(Direction.NORTH);
					break;
				case KeyEvent.VK_DOWN:
					playerTank.changeDirection(Direction.SOUTH);
					break;
			}
		}

		@Override
		public void keyReleased(KeyEvent e) {
			playerTank.keyReleased(e);
		}
	}
}
