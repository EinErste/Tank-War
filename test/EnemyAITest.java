import game_content.Difficulty;
import game_content.GameField;
import game_objects.movables.Direction;
import game_objects.movables.EnemyTankBrain;
import game_objects.movables.EnemyTankBrain.Decision;
import game_objects.movables.EnemyTankBrain.Goal;
import game_objects.movables.EnemyType;

import java.awt.Rectangle;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Headless checks of the enemy AI.
 * <p>
 * The rules the original Battle City uses are checked here: it re-decides only on a tile boundary and
 * only on a 1 in 16 roll (and does not move that tick), it rotates its goal between the base, a wander
 * and the player, it takes the axis with the larger distance towards its target, it fires on a 1 in 32
 * roll without aiming, and it shoots the bricks in its way.
 * <p>
 * So are the rules that were added on top, because this game is not the original: it goes around an
 * obstacle instead of reversing into its own tracks, it commits to a direction before re-deciding, and
 * it always finds a way out of a corner.
 * <p>
 * The brains are seeded and their goal is set explicitly, so every check is deterministic.
 */
public class EnemyAITest {

    private static final int TANK = 2 * GameField.BYTE;
    private static final Rectangle BASE = new Rectangle(500, 500, TANK, TANK);

    private static int failures;

    public static void main(String[] args) {
        chasesTheBase();
        takesTheDominantAxisFirst();
        huntsThePlayerInItsGoal();
        rotatesItsGoals();
        wandersOffSometimes();
        onlyReDecidesOnATileBoundary();
        reDecidesRarely();
        keepsDrivingOtherwise();
        goesAroundInsteadOfReversing();
        drivesAroundAFriend();
        shootsThroughWhatItCanBreak();
        facesWallsButNeverGivesUp();
        drivesAroundAnObstacle();
        firesRarelyAndWithoutAiming();
        typeStats();
        difficultySettings();

        System.out.println();
        System.out.println(failures == 0 ? "ENEMY AI TEST PASSED" : "ENEMY AI TEST FAILED (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    /**
     * decide() with "nothing special in the way", which is what most of these checks care about.
     */
    private static Decision decide(EnemyTankBrain brain, Rectangle tank, Rectangle base, Rectangle player,
            Direction facing, boolean onTileBoundary, boolean blocked, Predicate<Direction> canMove) {
        return brain.decide(tank, base, player, facing, onTileBoundary, blocked, false, false, canMove);
    }

    /**
     * The main goal of the original's selector is the eagle, the base.
     */
    private static void chasesTheBase() {
        EnemyTankBrain brain = new EnemyTankBrain(new Random(1), Goal.EAGLE);
        Rectangle tank = new Rectangle(0, 0, TANK, TANK);
        Direction chosen = firstDecision(brain, tank, BASE, null);
        boolean towardsBase = chosen == Direction.EAST || chosen == Direction.SOUTH;
        expect("an enemy in the open heads for the base (chose " + chosen + ")", towardsBase);
    }

    /**
     * {@code CalcDirToTarget}: abs-delta weighting, so the axis with more distance left goes first.
     */
    private static void takesTheDominantAxisFirst() {
        Rectangle tank = new Rectangle(0, 0, TANK, TANK);
        Rectangle farEast = new Rectangle(20 * GameField.BYTE, 3 * GameField.BYTE, TANK, TANK);
        Rectangle farSouth = new Rectangle(3 * GameField.BYTE, 20 * GameField.BYTE, TANK, TANK);

        Direction east = firstDecision(new EnemyTankBrain(new Random(2), Goal.EAGLE), tank, farEast, null);
        expect("goes east when the target is mostly east (chose " + east + ")", east == Direction.EAST);

        Direction south = firstDecision(new EnemyTankBrain(new Random(2), Goal.EAGLE), tank, farSouth, null);
        expect("goes south when the target is mostly south (chose " + south + ")", south == Direction.SOUTH);
    }

    /**
     * The third goal of the selector is the player.
     */
    private static void huntsThePlayerInItsGoal() {
        EnemyTankBrain brain = new EnemyTankBrain(new Random(13), Goal.PLAYER);
        Rectangle tank = new Rectangle(0, 0, TANK, TANK);
        Rectangle player = new Rectangle(0, 10 * GameField.BYTE, TANK, TANK);
        Rectangle eastBase = new Rectangle(20 * GameField.BYTE, 0, TANK, TANK);
        Direction chosen = firstDecision(brain, tank, eastBase, player);
        expect("with the player goal it goes for the player, not the base (chose " + chosen + ")",
                chosen == Direction.SOUTH);
    }

    /**
     * The goals rotate: base, wander, player, and around again.
     */
    private static void rotatesItsGoals() {
        EnemyTankBrain brain = new EnemyTankBrain(new Random(17), Goal.EAGLE);
        Rectangle tank = new Rectangle(0, 0, TANK, TANK);
        Set<Goal> seen = EnumSet.noneOf(Goal.class);
        for (int tick = 0; tick < 4000; tick++) {
            decide(brain, tank, BASE, null, Direction.NORTH, true, false, everythingIsFree());
            seen.add(brain.getGoal());
        }
        expect("cycles through all three goals over time (" + seen + ")", seen.size() == 3);
    }

    /**
     * The wander goal takes corners instead of always chasing the target.
     */
    private static void wandersOffSometimes() {
        EnemyTankBrain brain = new EnemyTankBrain(new Random(11), Goal.RANDOM);
        Rectangle tank = new Rectangle(0, 0, TANK, TANK);
        Set<Direction> seen = EnumSet.noneOf(Direction.class);
        for (int tick = 0; tick < 6000; tick++) {
            seen.add(decide(brain, tank, BASE, null, Direction.NORTH, true, false, everythingIsFree()).direction);
        }
        expect("a wandering enemy takes more than one direction over time (" + seen + ")", seen.size() > 1);
    }

    /**
     * {@code EntityMovementAI}: an enemy away from a tile boundary never changes its mind.
     */
    private static void onlyReDecidesOnATileBoundary() {
        EnemyTankBrain brain = new EnemyTankBrain(new Random(3), Goal.EAGLE);
        Rectangle tank = new Rectangle(0, 0, TANK, TANK);
        decide(brain, tank, BASE, null, Direction.NORTH, true, false, everythingIsFree());

        boolean everHeld = false;
        for (int tick = 0; tick < 500; tick++) {
            Goal before = brain.getGoal();
            boolean held = decide(brain, tank, BASE, null, Direction.NORTH, false, false, everythingIsFree()).hold;
            //the one exception is a goal change, which is meant to show immediately
            if (held && brain.getGoal() == before) {
                everHeld = true;
            }
        }
        expect("never re-decides away from a tile boundary (except when its goal changes)", !everHeld);

        int holds = 0;
        for (int tick = 0; tick < 500; tick++) {
            if (decide(brain, tank, BASE, null, Direction.NORTH, true, false, everythingIsFree()).hold) {
                holds++;
            }
        }
        expect("does re-decide on a tile boundary (" + holds + " times in 500 ticks)", holds > 0);
    }

    /**
     * The 1 in 16 roll, on top of the commitment: re-decisions stay rare, which is what makes an enemy
     * look like it is going somewhere instead of dithering.
     */
    private static void reDecidesRarely() {
        int holds = 0;
        int ticks = 20000;
        for (int seed = 0; seed < 8; seed++) {
            EnemyTankBrain brain = new EnemyTankBrain(new Random(seed), Goal.EAGLE);
            Rectangle tank = new Rectangle(0, 0, TANK, TANK);
            for (int tick = 0; tick < ticks / 8; tick++) {
                if (decide(brain, tank, BASE, null, Direction.NORTH, true, false, everythingIsFree()).hold) {
                    holds++;
                }
            }
        }
        double perTick = holds / (double) ticks;
        expect(String.format("re-decides rarely, about once every %.0f ticks", 1 / perTick),
                perTick < 1 / 20.0 && perTick > 1 / 200.0);
    }

    /**
     * Most ticks it just drives on, which is what makes it advance in a straight line.
     */
    private static void keepsDrivingOtherwise() {
        EnemyTankBrain brain = new EnemyTankBrain(new Random(5), Goal.EAGLE);
        Rectangle tank = new Rectangle(0, 0, TANK, TANK);
        Direction first = decide(brain, tank, BASE, null, Direction.NORTH, false, false, everythingIsFree()).direction;

        int changes = 0;
        Direction current = first;
        for (int tick = 0; tick < 300; tick++) {
            Decision decision = decide(brain, tank, BASE, null, Direction.NORTH, false, false, everythingIsFree());
            if (decision.direction != current) {
                changes++;
                current = decision.direction;
            }
        }
        expect("keeps its direction while nothing is in the way (changed " + changes + " times in 300 ticks)",
                changes == 0);
    }

    /**
     * The anti-jitter rule: a blocked enemy turns a free corner instead of reversing into its tracks.
     */
    private static void goesAroundInsteadOfReversing() {
        Rectangle tank = new Rectangle(100, 100, TANK, TANK);
        Set<Direction> northAndEastFree = EnumSet.of(Direction.NORTH, Direction.EAST);

        EnemyTankBrain brain = new EnemyTankBrain(new Random(41), Goal.EAGLE);
        brain.decide(tank, BASE, null, Direction.SOUTH, false, true, false, false, northAndEastFree::contains);
        Direction chosen = Direction.SOUTH;
        for (int tick = 0; tick < 30; tick++) {
            chosen = brain.decide(tank, BASE, null, Direction.SOUTH, false, true, false, false,
                    northAndEastFree::contains).direction;
            if (chosen != Direction.SOUTH) {
                break;
            }
        }
        expect("a blocked enemy turns the free corner (chose " + chosen + ")", chosen == Direction.EAST);
        expect("and it does not reverse into the way it came", chosen != Direction.NORTH);
    }

    /**
     * A friend in the way is not something to shoot at: go around it, quickly.
     */
    private static void drivesAroundAFriend() {
        Rectangle tank = new Rectangle(100, 100, TANK, TANK);
        Set<Direction> northAndEastFree = EnumSet.of(Direction.NORTH, Direction.EAST);
        EnemyTankBrain brain = new EnemyTankBrain(new Random(47), Goal.EAGLE);
        brain.decide(tank, BASE, null, Direction.SOUTH, false, false, false, false, northAndEastFree::contains);

        int ticksToTurn = 0;
        for (int tick = 1; tick <= 60; tick++) {
            if (brain.decide(tank, BASE, null, Direction.SOUTH, false, true, true, false,
                    northAndEastFree::contains).direction == Direction.EAST) {
                ticksToTurn = tick;
                break;
            }
        }
        expect("goes around another tank quickly (after " + ticksToTurn + " ticks)",
                ticksToTurn > 0 && ticksToTurn <= 10);
    }

    /**
     * Digging: a brick in front is worth shooting, so it keeps facing it and keeps firing.
     */
    private static void shootsThroughWhatItCanBreak() {
        Rectangle tank = new Rectangle(100, 100, TANK, TANK);
        Set<Direction> onlyNorthIsFree = EnumSet.of(Direction.NORTH);

        EnemyTankBrain digging = new EnemyTankBrain(new Random(43), Goal.EAGLE);
        digging.decide(tank, BASE, null, Direction.SOUTH, false, false, false, false, onlyNorthIsFree::contains);
        boolean keptFacing = true;
        int shots = 0;
        for (int tick = 0; tick < 120; tick++) {
            Decision decision = digging.decide(tank, BASE, null, Direction.SOUTH, false, true, false, true,
                    onlyNorthIsFree::contains);
            if (decision.direction != Direction.SOUTH) {
                keptFacing = false;
            }
            if (decision.fire) {
                shots++;
            }
        }
        expect("keeps facing a brick it can break and keeps shooting it (" + shots + " shots)",
                keptFacing && shots > 0);

        EnemyTankBrain solid = new EnemyTankBrain(new Random(43), Goal.EAGLE);
        solid.decide(tank, BASE, null, Direction.SOUTH, false, false, false, false, onlyNorthIsFree::contains);
        boolean wentAround = false;
        for (int tick = 0; tick < 120; tick++) {
            if (solid.decide(tank, BASE, null, Direction.SOUTH, false, true, false, false,
                    onlyNorthIsFree::contains).direction != Direction.SOUTH) {
                wentAround = true;
                break;
            }
        }
        expect("goes around an obstacle it cannot break", wentAround);
    }

    /**
     * Facing a wall is deliberate (that is how bricks get shot away), but never for good.
     */
    private static void facesWallsButNeverGivesUp() {
        Rectangle tank = new Rectangle(100, 100, TANK, TANK);
        Set<Direction> onlyNorthIsFree = EnumSet.of(Direction.NORTH);

        EnemyTankBrain driving = new EnemyTankBrain(new Random(37), Goal.EAGLE);
        Direction first = decide(driving, tank, BASE, null, Direction.NORTH, false, false,
                onlyNorthIsFree::contains).direction;
        boolean heldGoal = true;
        for (int tick = 0; tick < 30; tick++) {
            driving.onMoved();
            if (decide(driving, tank, BASE, null, Direction.NORTH, false, false,
                    onlyNorthIsFree::contains).direction != first) {
                heldGoal = false;
            }
        }
        expect("keeps facing its goal while it can move (faced " + first + ")", heldGoal);

        //only the way it came is free: a dead end has to be backed out of, not sat in
        EnemyTankBrain deadEnd = new EnemyTankBrain(new Random(37), Goal.EAGLE);
        deadEnd.decide(tank, BASE, null, Direction.SOUTH, false, false, false, false, onlyNorthIsFree::contains);
        Direction escaped = Direction.SOUTH;
        for (int tick = 0; tick < 60; tick++) {
            escaped = decide(deadEnd, tank, BASE, null, Direction.SOUTH, false, true,
                    onlyNorthIsFree::contains).direction;
            if (escaped != Direction.SOUTH) {
                break;
            }
        }
        expect("backs out of a dead end (chose " + escaped + ")", escaped == Direction.NORTH);
    }

    /**
     * The bug this check exists for: an enemy that is blocked off the tile boundary (where the 1 in 16
     * re-decision cannot fire) has to try the free axis instead of bouncing up and down forever.
     */
    private static void drivesAroundAnObstacle() {
        Set<Direction> allowed = EnumSet.of(Direction.NORTH, Direction.EAST);
        Rectangle tank = new Rectangle(100, 100, TANK, TANK);

        EnemyTankBrain brain = new EnemyTankBrain(new Random(31), Goal.EAGLE);
        brain.decide(tank, BASE, null, Direction.SOUTH, false, false, false, false, allowed::contains);
        int ticksNeeded = 0;
        for (int tick = 1; tick <= 120; tick++) {
            if (brain.decide(tank, BASE, null, Direction.SOUTH, false, true, false, false,
                    allowed::contains).direction == Direction.EAST) {
                ticksNeeded = tick;
                break;
            }
        }
        expect("an off-grid blocked enemy takes the free axis"
                + (ticksNeeded > 0 ? " (after " + ticksNeeded + " ticks)" : ""), ticksNeeded > 0);
        expect("and it does so within about a second", ticksNeeded > 0 && ticksNeeded <= 50);
    }

    /**
     * {@code EnemyFireTick}: a 1 in 32 chance per tick, with no aiming at all.
     */
    private static void firesRarelyAndWithoutAiming() {
        EnemyTankBrain brain = new EnemyTankBrain(new Random(19), Goal.EAGLE);
        int shots = 0;
        int ticks = 32000;
        for (int tick = 0; tick < ticks; tick++) {
            if (brain.shouldFire()) {
                shots++;
            }
        }
        double rate = shots / (double) ticks;
        expect(String.format("fires on about 1 in 32 ticks (measured 1 in %.1f)", 1 / rate),
                rate > 1 / 48.0 && rate < 1 / 21.0);

        EnemyTankBrain aiming = new EnemyTankBrain(new Random(23), Goal.EAGLE);
        Rectangle tank = new Rectangle(100, 100, TANK, TANK);
        int shotsAtNothing = 0;
        for (int tick = 0; tick < 4000; tick++) {
            if (decide(aiming, tank, new Rectangle(0, 0, TANK, TANK), null, Direction.NORTH, false, false,
                    everythingIsFree()).fire) {
                shotsAtNothing++;
            }
        }
        expect("shoots even with nothing in front of it, like the original (" + shotsAtNothing + " shots)",
                shotsAtNothing > 50);
    }

    /**
     * The four types of the original with their documented stats.
     */
    private static void typeStats() {
        expect("basic tank is slow with a slow bullet and one hit",
                EnemyType.BASIC.getHealth() == 1 && EnemyType.BASIC.getSpeed() < EnemyType.FAST.getSpeed());
        expect("fast tank moves faster than the basic one",
                EnemyType.FAST.getSpeed() > EnemyType.BASIC.getSpeed());
        expect("power tank shoots faster than the basic one",
                EnemyType.POWER.getBulletSpeed() > EnemyType.BASIC.getBulletSpeed());
        expect("armour tank takes four hits", EnemyType.ARMOR.getHealth() == 4);
        expect("scores are 100/200/300/400",
                EnemyType.BASIC.getPoints() == 100 && EnemyType.FAST.getPoints() == 200
                        && EnemyType.POWER.getPoints() == 300 && EnemyType.ARMOR.getPoints() == 400);

        //a speed that does not divide the tile size leaves a blocked tank off the tile boundary,
        //where it can never re-decide - which is what made fast tanks bounce up and down forever
        boolean dividesTheTile = true;
        for (EnemyType type : EnemyType.values()) {
            if (GameField.BYTE % type.getSpeed() != 0) {
                dividesTheTile = false;
            }
        }
        expect("every enemy speed divides the tile size", dividesTheTile);

        Random random = new Random(29);
        int early = 0;
        for (int i = 0; i < 2000; i++) {
            if (EnemyType.pickForStage(1, random) == EnemyType.ARMOR) {
                early++;
            }
        }
        expect("stage 1 has no armour tanks, like the original (" + early + ")", early == 0);
        int late = 0;
        for (int i = 0; i < 2000; i++) {
            if (EnemyType.pickForStage(10, random) == EnemyType.ARMOR) {
                late++;
            }
        }
        expect("the last stage does have armour tanks (" + late + ")", late > 400);
    }

    /**
     * The difficulty has to change the enemies, not just a label: easier settings shoot less and wander
     * more, harder settings shoot more and push towards the base for longer.
     */
    private static void difficultySettings() {
        expect("easy puts fewer tanks on the field than hard",
                Difficulty.EASY.getEnemiesOnScreen() < Difficulty.HARD.getEnemiesOnScreen());
        expect("hard sends more tanks per stage than easy",
                Difficulty.HARD.getEnemiesPerStage() > Difficulty.EASY.getEnemiesPerStage());
        expect("hard gives the player fewer lives than easy",
                Difficulty.HARD.getLives() < Difficulty.EASY.getLives());
        expect("normal is the balance the game had before",
                Difficulty.NORMAL.getEnemiesOnScreen() == 6 && Difficulty.NORMAL.getEnemiesPerStage() == 32
                        && Difficulty.NORMAL.getLives() == 3);
        expect("easier settings fire less often than harder ones",
                Difficulty.EASY.getBrainSettings().fireRoll > Difficulty.HARD.getBrainSettings().fireRoll);
        expect("easier settings push towards the base for less time",
                Difficulty.EASY.getBrainSettings().goalTicks < Difficulty.HARD.getBrainSettings().goalTicks);

        //the measured fire rate has to follow the setting
        int easyShots = shotsIn(Difficulty.EASY, 20000);
        int hardShots = shotsIn(Difficulty.HARD, 20000);
        expect("an easy enemy really does shoot less than a hard one (" + easyShots + " vs " + hardShots + ")",
                easyShots < hardShots);

        //and the type mix has to lean towards the elite tanks on hard
        int easyElite = eliteCount(Difficulty.EASY);
        int hardElite = eliteCount(Difficulty.HARD);
        expect("hard leans towards fast, power and armour tanks (" + easyElite + " vs " + hardElite + " of 2000)",
                hardElite > easyElite);
    }

    private static int shotsIn(Difficulty difficulty, int ticks) {
        EnemyTankBrain brain = new EnemyTankBrain(new Random(53), Goal.EAGLE, difficulty.getBrainSettings());
        int shots = 0;
        for (int tick = 0; tick < ticks; tick++) {
            if (brain.shouldFire()) {
                shots++;
            }
        }
        return shots;
    }

    private static int eliteCount(Difficulty difficulty) {
        Random random = new Random(59);
        int elite = 0;
        for (int i = 0; i < 2000; i++) {
            if (EnemyType.pickForStage(5, random, difficulty.getEliteBias()) != EnemyType.BASIC) {
                elite++;
            }
        }
        return elite;
    }

    /**
     * Runs ticks until the brain actually picks a direction and returns it, so the checks do not depend
     * on the 1 in 16 roll landing on the first tick.
     */
    private static Direction firstDecision(EnemyTankBrain brain, Rectangle tank, Rectangle base, Rectangle player) {
        for (int tick = 0; tick < 500; tick++) {
            Decision decision = decide(brain, tank, base, player, Direction.NORTH, true, false, everythingIsFree());
            if (decision.hold) {
                return decision.direction;
            }
        }
        return null;
    }

    private static Predicate<Direction> everythingIsFree() {
        return direction -> true;
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

    private static void expect(String what, boolean passed) {
        System.out.println((passed ? "   OK   " : "   FAIL ") + what);
        if (!passed) {
            failures++;
        }
    }
}
