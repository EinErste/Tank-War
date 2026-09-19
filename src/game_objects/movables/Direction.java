package game_objects.movables;


/**
 * Enum for directions. It is used for simplifying the code readability in some places.
 */
public enum Direction {
	WEST, EAST, NORTH, SOUTH;

	/**
	 * If two directions are in near each other, or, in other words, we are making 90 percent turn, we return true. Else it's always false
	 * @param dir1 First direction
	 * @param dir2 Second direction
	 * @return true if a 90 percent turn, otherwise false.
	 */
	public static boolean isTurn(Direction dir1, Direction dir2) {
		if(dir1== WEST || dir1 == EAST)
			if(dir2 == NORTH || dir2 == SOUTH)
				return true;
		if(dir1== NORTH || dir1 == SOUTH)
			if(dir2 == WEST || dir2 == EAST)
				return true;
		return false;
	}
}
