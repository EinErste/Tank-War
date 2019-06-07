package game_objects.map_objects.impassables;

import game_objects.Destructible;

public class BrickWall extends Impassable implements Destructible {

	public BrickWall(int x, int y) {
		super(x, y);

		init();
	}

	private void init() {
		loadImage("resources/sprites/map/brick_wall.png");
		getImageDimensions();
	}

	@Override
	public void destroy() {

	}
}
