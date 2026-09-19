package game_objects.movables;

import java.awt.Rectangle;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Enemy tank decision making, modelled on the AI of the original Battle City (Famicom).
 * <p>
 * The original logic, as far as it has been reverse engineered, and how it maps onto this class:
 * <ul>
 *   <li><b>When to re-decide</b> ({@code EntityMovementAI}): an enemy picks a new direction only while
 *       it stands on a tile boundary and a 1 in 16 random roll hits
 *       ({@code posX&7==0 && posY&7==0 && PRNG&$0F==0}), and it skips its move on that tick. So most
 *       ticks an enemy just keeps driving in the direction it has.</li>
 *   <li><b>What to aim for</b> ({@code SpeedCtrlMove}): a rotating goal selector - chase the eagle
 *       (the base), then wander randomly, then chase the player, and around again. That is the
 *       "alternates between a random detour and advancing, but eventually goes after your base"
 *       behaviour of the original.</li>
 *   <li><b>Which direction</b> ({@code CalcDirToTarget}): the axis with the larger distance to the
 *       target is tried first (abs-delta weighting), the other axis second, any free direction last.
 *       A wander either re-rolls towards the target or turns 90 degrees
 *       ({@code RandomDirChange}: 50% goal, 25% left, 25% right).</li>
 *   <li><b>When blocked</b> ({@code EntityMovementBlocked}): 1 in 4 blocked enemies turns around,
 *       the rest just bump into the wall and keep facing it.</li>
 *   <li><b>When to shoot</b> ({@code EnemyFireTick}): a 1 in 32 chance per tick, without aiming -
 *       the bullet simply leaves in the direction the barrel points.</li>
 * </ul>
 * Two details are modelled instead of copied, because the ROM counter behind the goal selector is not
 * documented: how long each goal lasts (a few seconds here), and a blocked enemy that keeps bumping
 * gives up and re-decides after {@link #STUCK_TICKS} - the original's tanks drive through each other,
 * ours collide, so without that an enemy could wait behind a friend forever.
 */
public class EnemyTankBrain {

	/**
	 * {@code PRNG&$0F==0}: 1 in 16 chance per tick to re-decide while standing on a tile boundary.
	 */
	private static final int RECONSIDER_ROLL = 16;

	/**
	 * {@code PRNG&3==0}: 1 in 4 chance that a blocked enemy turns around instead of bumping.
	 */
	private static final int TURN_AROUND_ROLL = 4;

	/**
	 * {@code PRNG&$1F==0}: 1 in 32 chance per tick to fire.
	 */
	private static final int FIRE_ROLL = 32;

	/**
	 * How many ticks a blocked enemy keeps bumping before it re-decides anyway.
	 */
	private static final int STUCK_TICKS = 25;

	/**
	 * How long a goal lasts before the selector moves on, in ticks (50 per second).
	 */
	private static final int GOAL_TICKS = 120;

	/**
	 * Extra random ticks added to a goal, so enemies do not all switch at the same moment.
	 */
	private static final int GOAL_TICKS_VARIATION = 240;

	/**
	 * What an enemy is currently after - the three states of the original's goal selector.
	 */
	public enum Goal {
		EAGLE, RANDOM, PLAYER
	}

	/**
	 * What the tank should do this tick.
	 */
	public static final class Decision {

		/**
		 * The direction to drive in.
		 */
		public final Direction direction;
		/**
		 * True when the tank should not move this tick (it just re-decided, like the original).
		 */
		public final boolean hold;
		/**
		 * True when the tank should fire.
		 */
		public final boolean fire;

		Decision(Direction direction, boolean hold, boolean fire) {
			this.direction = direction;
			this.hold = hold;
			this.fire = fire;
		}
	}

	private final Random random;
	private Goal goal;
	private int goalTicks;
	private Direction direction;
	private int blockedTicks;

	public EnemyTankBrain(Random random) {
		this(random, Goal.EAGLE);
	}

	/**
	 * @param random source of randomness, seed it for reproducible behaviour
	 * @param goal   the goal the enemy starts with
	 */
	public EnemyTankBrain(Random random, Goal goal) {
		this.random = random;
		this.goal = goal;
		this.goalTicks = GOAL_TICKS + random.nextInt(GOAL_TICKS_VARIATION);
	}

	public Goal getGoal() {
		return goal;
	}

	/**
	 * @return the direction the enemy is driving in, or null before the first decision
	 */
	public Direction getDirection() {
		return direction;
	}

	/**
	 * Decides what the enemy does this tick.
	 *
	 * @param bounds         where the tank is now
	 * @param base           the base of the player, the "eagle" of the original
	 * @param player         the player tank, or null when it is not on the field
	 * @param facing         the direction the tank is facing now, used until the brain has decided once
	 * @param onTileBoundary true when the tank stands exactly on a tile boundary, which is the only
	 *                       place the original lets an enemy re-decide
	 * @param blocked        true when the tank cannot drive on in its current direction
	 * @param canMove        tests whether a direction is free
	 * @return what to do this tick
	 */
	public Decision decide(Rectangle bounds, Rectangle base, Rectangle player, Direction facing,
			boolean onTileBoundary, boolean blocked, Predicate<Direction> canMove) {
		if (direction == null) {
			//the tank was spawned facing somewhere, start from that
			direction = facing != null ? facing : Direction.SOUTH;
		}
		advanceGoal();

		if (blocked) {
			blockedTicks++;
			if (random.nextInt(TURN_AROUND_ROLL) == 0) {
				//BlockedFlipDir: the original reverses on the spot, it does not re-plan
				direction = opposite(direction);
				return new Decision(direction, true, shouldFire());
			}
			if (blockedTicks >= STUCK_TICKS) {
				//3 out of 4 blocked enemies just bump into the wall and a reversal does not count as
				//progress either, so an enemy that keeps bouncing off walls re-plans every
				//STUCK_TICKS ticks instead of driving up and down the same corridor forever
				blockedTicks = 0;
				direction = pickDirection(bounds, base, player, canMove);
				return new Decision(direction, true, shouldFire());
			}
			return new Decision(direction, true, shouldFire());
		}
		blockedTicks = 0;

		if (onTileBoundary && random.nextInt(RECONSIDER_ROLL) == 0) {
			direction = pickDirection(bounds, base, player, canMove);
			return new Decision(direction, true, shouldFire());
		}
		return new Decision(direction, false, shouldFire());
	}

	/**
	 * @return true when the tank fires this tick, a 1 in 32 chance, exactly like the original
	 */
	public boolean shouldFire() {
		return random.nextInt(FIRE_ROLL) == 0;
	}

	/**
	 * Rotates through the goals over time: base, wander, player, base, ...
	 */
	private void advanceGoal() {
		if (--goalTicks > 0) {
			return;
		}
		switch (goal) {
			case EAGLE:
				goal = Goal.RANDOM;
				break;
			case RANDOM:
				goal = Goal.PLAYER;
				break;
			default:
				goal = Goal.EAGLE;
				break;
		}
		goalTicks = GOAL_TICKS + random.nextInt(GOAL_TICKS_VARIATION);
	}

	private Direction pickDirection(Rectangle bounds, Rectangle base, Rectangle player, Predicate<Direction> canMove) {
		switch (goal) {
			case PLAYER:
				return towards(bounds, player != null ? player : base, canMove);
			case RANDOM:
				//half the time the wander still heads somewhere, otherwise it turns a corner
				if (random.nextBoolean()) {
					return towards(bounds, base, canMove);
				}
				Direction turn = random.nextBoolean() ? leftOf(direction) : rightOf(direction);
				return canMove.test(turn) ? turn : towards(bounds, base, canMove);
			default:
				return towards(bounds, base, canMove);
		}
	}

	/**
	 * The abs-delta weighting of {@code CalcDirToTarget}: the axis with more distance left is tried
	 * first, the other one second, and any free direction is better than standing still.
	 */
	private Direction towards(Rectangle bounds, Rectangle target, Predicate<Direction> canMove) {
		int dx = centre(target.x, target.width) - centre(bounds.x, bounds.width);
		int dy = centre(target.y, target.height) - centre(bounds.y, bounds.height);

		Direction horizontal = dx >= 0 ? Direction.EAST : Direction.WEST;
		Direction vertical = dy >= 0 ? Direction.SOUTH : Direction.NORTH;

		Direction first;
		if (Math.abs(dx) > Math.abs(dy)) {
			first = horizontal;
		} else if (Math.abs(dy) > Math.abs(dx)) {
			first = vertical;
		} else {
			first = random.nextBoolean() ? horizontal : vertical;
		}
		Direction second = first == horizontal ? vertical : horizontal;

		if (canMove.test(first)) {
			return first;
		}
		if (canMove.test(second)) {
			return second;
		}
		for (Direction candidate : Direction.values()) {
			if (canMove.test(candidate)) {
				return candidate;
			}
		}
		return direction != null ? direction : Direction.SOUTH;
	}

	private static Direction opposite(Direction direction) {
		switch (direction) {
			case NORTH:
				return Direction.SOUTH;
			case SOUTH:
				return Direction.NORTH;
			case WEST:
				return Direction.EAST;
			default:
				return Direction.WEST;
		}
	}

	private static Direction leftOf(Direction direction) {
		switch (direction) {
			case NORTH:
				return Direction.WEST;
			case WEST:
				return Direction.SOUTH;
			case SOUTH:
				return Direction.EAST;
			default:
				return Direction.NORTH;
		}
	}

	private static Direction rightOf(Direction direction) {
		switch (direction) {
			case NORTH:
				return Direction.EAST;
			case EAST:
				return Direction.SOUTH;
			case SOUTH:
				return Direction.WEST;
			default:
				return Direction.NORTH;
		}
	}

	private static int centre(int start, int size) {
		return start + size / 2;
	}
}
