package game_objects.movables;

import game_objects.Destructible;

import java.awt.event.KeyEvent;

public class PlayerTank extends Tank implements Destructible {

	private int level = 1;

	public PlayerTank(int x, int y, Direction dir) {
		super(x, y, dir, 1500);

		init();
	}

	private void init() {
		loadImage("resources/sprites/player_tank/lvl1/tank_%s.png");
		getImageDimensions();
	}

	public void keyReleased(KeyEvent e) {
		int key = e.getKeyCode();

		if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_RIGHT) {
			dx = 0;
		}

		if (key == KeyEvent.VK_UP || key == KeyEvent.VK_DOWN) {
			dy = 0;
		}
	}

	public void upgrade() {
		level++;
		if (level == 2) {
			loadImage("resources/sprites/player_tank/lvl2/tank_%s.png");
			setBulletSpeed(3);
			setDelay(1000);
		} else if (level == 3) {
			loadImage("resources/sprites/player_tank/lvl3/tank_%s.png");
			setBulletSpeed(4 );
			setDelay(500);
		}

	}
}
