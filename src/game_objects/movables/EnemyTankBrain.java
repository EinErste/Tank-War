package game_objects.movables;

import java.awt.Rectangle;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Enemy tank decision making: the behaviour of the original Battle City, with the parts that make it
 * twitchy in this engine replaced by something more deliberate.
 * <p>
 * <b>Kept from the original</b> (see {@code REVERSE.md} of the Famicom disassembly for the routines):
 * <ul>
 *   <li>an enemy re-decides on a tile boundary on a 1 in 16 roll, and skips its move on that tick
 *       ({@code EntityMovementAI}), so it drives in straight lines,</li>
 *   <li>the goal rotates between chasing the base, wandering and chasing the player
 *       ({@code SpeedCtrlMove}) - "a random detour, but eventually it goes for your base",</li>
 *   <li>the direction towards a target is the axis with the larger distance ({@code CalcDirToTarget}),</li>
 *   <li>it fires on a 1 in 32 roll per tick, without aiming ({@code EnemyFireTick}),</li>
 *   <li>it shoots the bricks that are in its way, which is how the original's tanks dig towards the
 *       base,</li>
 *   <li>a wandering enemy turns 90 degree corners ({@code RandomDirChange}).</li>
 * </ul>
 * <b>Changed, because this game is not the original</b> (the original lets tanks drive through each
 * other and its enemies bounce off walls a lot, which here reads as jitter and standing still):
 * <ul>
 *   <li>a blocked enemy no longer reverses 25% of the time ({@code EntityMovementBlocked}) - it turns a
 *       free corner instead and only backs out when both sides are blocked, so it does not undo its
 *       progress,</li>
 *   <li>the direction it picks is the first one that is actually free, and only faces a wall when
 *       nothing else is open - that keeps the digging, without bumping into walls for no reason,</li>
 *   <li>it commits to a direction for {@link Settings#minCommitTicks} before re-deciding, which removes the
 *       dithering,</li>
 *   <li>it is blocked by other tanks, so a friend in the way makes it go around quickly
 *       ({@link Settings#turnAsideTicks}),</li>
 *   <li>{@link Settings#noProgressTicks} without making any progress forces a way out, so nothing can park in
 *       a corner forever.</li>
 * </ul>
 */
public class EnemyTankBrain {

	/**
	 * Every knob of the AI in one place, so {@code Difficulty} can hand in easier or harder ones.
	 * Ticks are ticks of the game loop, 50 per second.
	 */
	public static final class Settings {

		/**
		 * {@code PRNG&$0F==0}: 1 in this many ticks to re-decide while on a tile boundary.
		 */
		public final int reconsiderRoll;
		/**
		 * {@code PRNG&$1F==0}: 1 in this many ticks to fire.
		 */
		public final int fireRoll;
		/**
		 * How long an enemy keeps driving the same way before it may re-decide.
		 */
		public final int minCommitTicks;
		/**
		 * How long it keeps shooting a breakable obstacle before going around instead.
		 */
		public final int digTicks;
		/**
		 * How long it bumps into something before it turns a corner.
		 */
		public final int turnAsideTicks;
		/**
		 * Ticks without any progress at all after which it forces its way out, whatever it takes.
		 */
		public final int noProgressTicks;
		/**
		 * How long a goal lasts before the selector moves on.
		 */
		public final int goalTicks;
		/**
		 * Extra random ticks added to a goal, so enemies do not all switch at the same moment.
		 */
		public final int goalTicksVariation;

		public Settings(int reconsiderRoll, int fireRoll, int minCommitTicks, int digTicks,
				int turnAsideTicks, int noProgressTicks, int goalTicks, int goalTicksVariation) {
			this.reconsiderRoll = reconsiderRoll;
			this.fireRoll = fireRoll;
			this.minCommitTicks = minCommitTicks;
			this.digTicks = digTicks;
			this.turnAsideTicks = turnAsideTicks;
			this.noProgressTicks = noProgressTicks;
			this.goalTicks = goalTicks;
			this.goalTicksVariation = goalTicksVariation;
		}

		/**
		 * The settings the game used before difficulties existed, also used by the tests.
		 */
		public static final Settings DEFAULT =
				new Settings(16, 32, 25, 150, 6, 75, 150, 300);
	}

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
		 * True when the tank should not move this tick (it just re-decided, or it is digging).
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
	private final Settings settings;
	private Goal goal;
	private int goalTicks;
	private Direction direction;
	private int ticksInDirection;
	private int ticksBlocked;
	private int ticksWithoutProgress;

	public EnemyTankBrain(Random random) {
		this(random, Goal.EAGLE, Settings.DEFAULT);
	}

	/**
	 * @param random source of randomness, seed it for reproducible behaviour
	 * @param goal   the goal the enemy starts with
	 */
	public EnemyTankBrain(Random random, Goal goal) {
		this(random, goal, Settings.DEFAULT);
	}

	/**
	 * @param random   source of randomness
	 * @param goal     the goal the enemy starts with
	 * @param settings the knobs of the AI, see {@link Settings}
	 */
	public EnemyTankBrain(Random random, Goal goal, Settings settings) {
		this.random = random;
		this.goal = goal;
		this.settings = settings;
		this.goalTicks = settings.goalTicks + random.nextInt(settings.goalTicksVariation);
	}

	public Settings getSettings() {
		return settings;
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
	 * Tells the brain that the tank really moved, which resets its patience.
	 */
	public void onMoved() {
		ticksWithoutProgress = 0;
	}

	/**
	 * Decides what the enemy does this tick.
	 *
	 * @param bounds              where the tank is now
	 * @param base                the base of the player, the "eagle" of the original
	 * @param player              the player tank, or null when it is not on the field
	 * @param facing              the direction the tank faces now, used until the brain has decided once
	 * @param onTileBoundary      true when the tank stands on a tile boundary, the only place the
	 *                            original lets an enemy re-decide
	 * @param blocked             true when the tank cannot drive on in its current direction
	 * @param blockedByTank       true when it is another tank that is in the way
	 * @param blockerIsBreakable  true when the obstacle in front can be shot away (a brick or the base)
	 * @param canMove             tests whether a direction is free
	 * @return what to do this tick
	 */
	public Decision decide(Rectangle bounds, Rectangle base, Rectangle player, Direction facing,
			boolean onTileBoundary, boolean blocked, boolean blockedByTank, boolean blockerIsBreakable,
			Predicate<Direction> canMove) {
		if (direction == null) {
			//the tank was spawned facing somewhere, start from that
			direction = facing != null ? facing : Direction.SOUTH;
		}
		boolean goalChanged = advanceGoal();
		ticksInDirection++;
		ticksWithoutProgress++;

		if (blocked) {
			ticksBlocked++;
			return blocked(bounds, base, player, blockedByTank, blockerIsBreakable, canMove);
		}
		ticksBlocked = 0;

		if (goalChanged || (onTileBoundary && ticksInDirection >= settings.minCommitTicks
				&& random.nextInt(settings.reconsiderRoll) == 0)) {
			turnTo(pickDirection(bounds, base, player, canMove));
			return new Decision(direction, true, shouldFire());
		}
		return new Decision(direction, false, shouldFire());
	}

	/**
	 * @return true when the tank fires this tick, a 1 in 32 chance, like the original
	 */
	public boolean shouldFire() {
		return random.nextInt(settings.fireRoll) == 0;
	}

	/**
	 * What to do when the way ahead is blocked: shoot through it if that can work, otherwise go around,
	 * otherwise back out.
	 */
	private Decision blocked(Rectangle bounds, Rectangle base, Rectangle player, boolean blockedByTank,
			boolean blockerIsBreakable, Predicate<Direction> canMove) {
		//a brick (or the base) in the way is worth shooting: that is how the original digs its way
		if (blockerIsBreakable && !blockedByTank && ticksBlocked <= settings.digTicks) {
			return new Decision(direction, true, shouldFire());
		}

		Direction aside = freeCorner(bounds, targetOf(base, player), canMove);
		if (aside != null) {
			if (ticksBlocked >= settings.turnAsideTicks) {
				turnTo(aside);
			}
			return new Decision(direction, true, shouldFire());
		}

		//nothing beside us: back out of the dead end
		Direction back = opposite(direction);
		if (canMove.test(back)) {
			turnTo(back);
			return new Decision(direction, true, shouldFire());
		}

		//boxed in on three sides: keep facing the wall, and force a way out if this takes too long
		if (ticksWithoutProgress >= settings.noProgressTicks) {
			Direction escape = anyFreeDirection(canMove);
			if (escape != null) {
				turnTo(escape);
				ticksWithoutProgress = 0;
			}
		}
		return new Decision(direction, true, shouldFire());
	}

	/**
	 * Rotates through the goals over time: base, wander, player, base, ...
	 *
	 * @return true when the goal just changed
	 */
	private boolean advanceGoal() {
		if (--goalTicks > 0) {
			return false;
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
		goalTicks = settings.goalTicks + random.nextInt(settings.goalTicksVariation);
		return true;
	}

	private void turnTo(Direction newDirection) {
		if (newDirection != null && newDirection != direction) {
			direction = newDirection;
			ticksInDirection = 0;
			ticksBlocked = 0;
		}
	}

	private Direction pickDirection(Rectangle bounds, Rectangle base, Rectangle player, Predicate<Direction> canMove) {
		switch (goal) {
			case PLAYER:
				return towards(bounds, player != null ? player : base, canMove);
			case RANDOM:
				//half the time the wander still heads for the base, otherwise it takes a corner
				if (random.nextBoolean()) {
					return towards(bounds, base, canMove);
				}
				Direction detour = freeCorner(bounds, base, canMove);
				return detour != null ? detour : towards(bounds, base, canMove);
			default:
				return towards(bounds, base, canMove);
		}
	}

	/**
	 * The abs-delta weighting of {@code CalcDirToTarget} - the axis with the larger distance first, the
	 * other one second - but the first direction that is actually free wins, and only when both are
	 * blocked does it face the wall in the way and shoot it.
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
		//both ways are blocked: face the way we want to go and shoot through it
		return first;
	}

	/**
	 * @return a free direction 90 degrees off the current one, preferring the side that gets closer to
	 *         the target, or null when neither side is open
	 */
	private Direction freeCorner(Rectangle bounds, Rectangle target, Predicate<Direction> canMove) {
		Direction left = leftOf(direction);
		Direction right = rightOf(direction);
		boolean leftIsFree = canMove.test(left);
		boolean rightIsFree = canMove.test(right);
		if (!leftIsFree && !rightIsFree) {
			return null;
		}
		if (leftIsFree && rightIsFree) {
			int leftProgress = progress(left, bounds, target);
			int rightProgress = progress(right, bounds, target);
			if (leftProgress == rightProgress) {
				return random.nextBoolean() ? left : right;
			}
			return leftProgress > rightProgress ? left : right;
		}
		return leftIsFree ? left : right;
	}

	private Direction anyFreeDirection(Predicate<Direction> canMove) {
		for (Direction candidate : Direction.values()) {
			if (canMove.test(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	/**
	 * @return the player when the goal says so and the player is on the field, the base otherwise
	 */
	private Rectangle targetOf(Rectangle base, Rectangle player) {
		return goal == Goal.PLAYER && player != null ? player : base;
	}

	/**
	 * @return how much closer to the target a one step move in this direction gets
	 */
	private static int progress(Direction candidate, Rectangle bounds, Rectangle target) {
		int fromX = centre(bounds.x, bounds.width);
		int fromY = centre(bounds.y, bounds.height);
		int toX = centre(target.x, target.width);
		int toY = centre(target.y, target.height);

		int stepX = candidate == Direction.EAST ? 1 : candidate == Direction.WEST ? -1 : 0;
		int stepY = candidate == Direction.SOUTH ? 1 : candidate == Direction.NORTH ? -1 : 0;

		int before = Math.abs(toX - fromX) + Math.abs(toY - fromY);
		int after = Math.abs(toX - (fromX + stepX)) + Math.abs(toY - (fromY + stepY));
		return before - after;
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
