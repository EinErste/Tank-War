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
 * Headless checks of the enemy AI, which is modelled on the original Battle City.
 * <p>
 * Every rule of the original that the brain implements is checked here: it only re-decides while it
 * stands on a tile boundary and only on a 1 in 16 roll (and then it does not move that tick), it
 * rotates its goal between the base, a random wander and the player, a chase takes the axis with the
 * larger distance first, a wander turns corners, a blocked enemy turns around 1 time in 4 and
 * otherwise bumps into the wall, and it fires on a 1 in 32 roll without aiming.
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
        onlyReDecidesOnATileBoundary();
        reDecidesAboutOnceInSixteen();
        keepsDrivingOtherwise();
        turnsAroundWhenBlocked();
        bumpsIntoTheWallMostOfTheTime();
        wandersOffSometimes();
        huntsThePlayerInItsGoal();
        rotatesItsGoals();
        firesRarelyAndWithoutAiming();
        neverPicksABlockedDirection();
        drivesAroundAnObstacle();
        typeStats();

        System.out.println();
        System.out.println(failures == 0 ? "ENEMY AI TEST PASSED" : "ENEMY AI TEST FAILED (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
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

        EnemyTankBrain eastward = new EnemyTankBrain(new Random(2), Goal.EAGLE);
        expect("goes east when the base is mostly east (chose "
                        + firstDecision(eastward, tank, farEast, null) + ")",
                firstDecision(eastward, tank, farEast, null) == Direction.EAST);

        EnemyTankBrain southward = new EnemyTankBrain(new Random(2), Goal.EAGLE);
        expect("goes south when the base is mostly south (chose "
                        + firstDecision(southward, tank, farSouth, null) + ")",
                firstDecision(southward, tank, farSouth, null) == Direction.SOUTH);
    }

    /**
     * {@code EntityMovementAI}: an enemy that is not on a tile boundary never changes its mind.
     */
    private static void onlyReDecidesOnATileBoundary() {
        EnemyTankBrain brain = new EnemyTankBrain(new Random(3), Goal.EAGLE);
        Rectangle tank = new Rectangle(0, 0, TANK, TANK);
        brain.decide(tank, BASE, null, Direction.NORTH, true, false, everythingIsFree());

        boolean everHeld = false;
        for (int tick = 0; tick < 500; tick++) {
            if (brain.decide(tank, BASE, null, Direction.NORTH, false, false, everythingIsFree()).hold) {
                everHeld = true;
            }
        }
        expect("never re-decides away from a tile boundary", !everHeld);

        int holds = 0;
        for (int tick = 0; tick < 500; tick++) {
            if (brain.decide(tank, BASE, null, Direction.NORTH, true, false, everythingIsFree()).hold) {
                holds++;
            }
        }
        expect("does re-decide on a tile boundary, and holds that tick (held " + holds + "/500)", holds > 0);
    }

    /**
     * {@code PRNG&$0F==0}: that re-decision happens about once in sixteen ticks.
     */
    private static void reDecidesAboutOnceInSixteen() {
        int holds = 0;
        int ticks = 8000;
        for (int seed = 0; seed < 8; seed++) {
            EnemyTankBrain brain = new EnemyTankBrain(new Random(seed), Goal.EAGLE);
            Rectangle tank = new Rectangle(0, 0, TANK, TANK);
            for (int tick = 0; tick < ticks / 8; tick++) {
                if (brain.decide(tank, BASE, null, Direction.NORTH, true, false, everythingIsFree()).hold) {
                    holds++;
                }
            }
        }
        double chance = holds / (double) ticks;
        expect(String.format("re-decides on about 1 in 16 ticks (measured 1 in %.1f)", 1 / chance),
                chance > 1 / 24.0 && chance < 1 / 11.0);
    }

    /**
     * Most ticks it just drives on, which is what makes it advance in a straight line.
     */
    private static void keepsDrivingOtherwise() {
        EnemyTankBrain brain = new EnemyTankBrain(new Random(5), Goal.EAGLE);
        Rectangle tank = new Rectangle(0, 0, TANK, TANK);
        Direction first = brain.decide(tank, BASE, null, Direction.NORTH, false, false, everythingIsFree()).direction;

        int changes = 0;
        Direction current = first;
        for (int tick = 0; tick < 300; tick++) {
            Decision decision = brain.decide(tank, BASE, null, Direction.NORTH, false, false, everythingIsFree());
            if (decision.direction != current) {
                changes++;
                current = decision.direction;
            }
            if (decision.hold) {
                failures++;
                System.out.println("   FAIL held a tick while not on a tile boundary");
            }
        }
        expect("keeps its direction while nothing is in the way (changed " + changes + " times in 300 ticks)",
                changes == 0);
    }

    /**
     * {@code EntityMovementBlocked}: 1 blocked enemy in 4 turns around.
     */
    private static void turnsAroundWhenBlocked() {
        int turnarounds = 0;
        int blockedDecisions = 2000;
        for (int seed = 0; seed < 5; seed++) {
            EnemyTankBrain brain = new EnemyTankBrain(new Random(seed), Goal.EAGLE);
            Rectangle tank = new Rectangle(0, 0, TANK, TANK);
            //let it pick a direction first, from a free spot
            brain.decide(tank, BASE, null, Direction.NORTH, true, false, everythingIsFree());
            for (int tick = 0; tick < blockedDecisions / 5; tick++) {
                Direction before = brain.getDirection();
                Decision decision = brain.decide(tank, BASE, null, Direction.NORTH, true, true, everythingIsFree());
                if (decision.direction == opposite(before)) {
                    turnarounds++;
                }
            }
        }
        double rate = turnarounds / (double) blockedDecisions;
        expect(String.format("turns around on about 1 in 4 blocked ticks (measured %.0f%%)", rate * 100),
                rate > 0.15 && rate < 0.36);
    }

    private static void bumpsIntoTheWallMostOfTheTime() {
        EnemyTankBrain brain = new EnemyTankBrain(new Random(9), Goal.EAGLE);
        Rectangle tank = new Rectangle(0, 0, TANK, TANK);
        int keptFacing = 0;
        int ticks = 400;
        Direction previous = brain.decide(tank, BASE, null, Direction.NORTH, true, false, everythingIsFree()).direction;
        for (int tick = 0; tick < ticks; tick++) {
            Direction after = brain.decide(tank, BASE, null, Direction.NORTH, true, true, everythingIsFree()).direction;
            if (after == previous) {
                keptFacing++;
            }
            previous = after;
        }
        expect("keeps facing the wall instead of spinning (kept facing " + keptFacing + "/400 ticks)",
                keptFacing > 400 * 0.6);
    }

    /**
     * The wander goal turns corners instead of always chasing the target.
     */
    private static void wandersOffSometimes() {
        EnemyTankBrain brain = new EnemyTankBrain(new Random(11), Goal.RANDOM);
        Rectangle tank = new Rectangle(0, 0, TANK, TANK);
        Set<Direction> seen = EnumSet.noneOf(Direction.class);
        for (int tick = 0; tick < 4000; tick++) {
            seen.add(brain.decide(tank, BASE, null, Direction.NORTH, true, false, everythingIsFree()).direction);
        }
        expect("a wandering enemy takes more than one direction over time (" + seen + ")", seen.size() > 1);
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
            brain.decide(tank, BASE, null, Direction.NORTH, true, false, everythingIsFree());
            seen.add(brain.getGoal());
        }
        expect("cycles through all three goals over time (" + seen + ")", seen.size() == 3);
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
            if (aiming.decide(tank, new Rectangle(0, 0, TANK, TANK), null, Direction.NORTH, false, false, everythingIsFree()).fire) {
                shotsAtNothing++;
            }
        }
        expect("shoots even with nothing in front of it, like the original (" + shotsAtNothing + " shots)",
                shotsAtNothing > 50);
    }

    /**
     * Whatever it decides, it must be a direction it can drive.
     */
    private static void neverPicksABlockedDirection() {
        Set<Direction> allowed = EnumSet.of(Direction.NORTH, Direction.EAST);
        boolean chosenIsDrivable = true;
        for (int seed = 0; seed < 50; seed++) {
            EnemyTankBrain brain = new EnemyTankBrain(new Random(seed));
            Rectangle tank = new Rectangle(100, 100, TANK, TANK);
            brain.decide(tank, BASE, null, Direction.NORTH, true, false, allowed::contains);
            for (int tick = 0; tick < 200; tick++) {
                //whatever the brain picks for itself must be a direction the tank can drive
                Decision free = brain.decide(tank, BASE, null, Direction.NORTH, true, false, allowed::contains);
                if (free.hold && !allowed.contains(free.direction)) {
                    chosenIsDrivable = false;
                }
                //and while blocked, anything beyond "keep facing" and "turn around" must be drivable too
                Direction before = brain.getDirection();
                Decision blocked = brain.decide(tank, BASE, null, Direction.NORTH, true, true, allowed::contains);
                boolean keepsFacingOrReverses = blocked.direction == before || blocked.direction == opposite(before);
                if (!keepsFacingOrReverses && !allowed.contains(blocked.direction)) {
                    chosenIsDrivable = false;
                }
            }
        }
        expect("a direction it picks for itself is always one it can drive", chosenIsDrivable);
    }

    /**
     * The bug this test exists for: an enemy that keeps bumping into a wall has to try the other
     * axis instead of driving up and down the same corridor for the rest of the stage.
     */
    private static void drivesAroundAnObstacle() {
        //the base is straight south, so the first choice is always "south", which is blocked here
        Set<Direction> allowed = EnumSet.of(Direction.NORTH, Direction.EAST);
        Rectangle tank = new Rectangle(100, 100, TANK, TANK);

        //off the tile boundary on purpose: that is where a blocked tank used to be helpless, because
        //the 1 in 16 re-decision cannot fire and the reversal kept resetting the stuck counter
        boolean turnedAside = false;
        int ticksNeeded = 0;
        EnemyTankBrain brain = new EnemyTankBrain(new Random(31), Goal.EAGLE);
        brain.decide(tank, BASE, null, Direction.SOUTH, false, false, allowed::contains);
        for (int tick = 1; tick <= 300; tick++) {
            Decision decision = brain.decide(tank, BASE, null, Direction.SOUTH, false, true, allowed::contains);
            if (decision.direction == Direction.EAST) {
                turnedAside = true;
                ticksNeeded = tick;
                break;
            }
        }
        expect("an off-grid blocked enemy tries the free axis instead of bouncing forever"
                + (turnedAside ? " (after " + ticksNeeded + " ticks)" : ""), turnedAside);
        expect("and it does so within about half a second", turnedAside && ticksNeeded <= 40);
    }

    /**
     * The four types of the original with their documented stats.
     */
    private static void typeStats() {
        expect("basic tank is slow with a slow bullet and one hit",
                EnemyType.BASIC.getHealth() == 1 && EnemyType.BASIC.getSpeed() < EnemyType.FAST.getSpeed());
        expect("fast tank moves faster than the basic one",
                EnemyType.FAST.getSpeed() > EnemyType.BASIC.getSpeed());
        //a speed that does not divide the tile size leaves a blocked tank off the tile boundary,
        //where it can never re-decide - which is what made fast tanks bounce up and down forever
        boolean dividesTheTile = true;
        for (EnemyType type : EnemyType.values()) {
            if (GameField.BYTE % type.getSpeed() != 0) {
                dividesTheTile = false;
            }
        }
        expect("every enemy speed divides the tile size", dividesTheTile);
        expect("power tank shoots faster than the basic one",
                EnemyType.POWER.getBulletSpeed() > EnemyType.BASIC.getBulletSpeed());
        expect("armour tank takes four hits", EnemyType.ARMOR.getHealth() == 4);
        expect("scores are 100/200/300/400",
                EnemyType.BASIC.getPoints() == 100 && EnemyType.FAST.getPoints() == 200
                        && EnemyType.POWER.getPoints() == 300 && EnemyType.ARMOR.getPoints() == 400);

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
     * Runs ticks until the brain re-decides (which is when it picks a direction) and returns that
     * direction, so the checks do not depend on the 1 in 16 roll landing on the first tick.
     */
    private static Direction firstDecision(EnemyTankBrain brain, Rectangle tank, Rectangle base, Rectangle player) {
        for (int tick = 0; tick < 500; tick++) {
            Decision decision = brain.decide(tank, base, player, Direction.NORTH, true, false, everythingIsFree());
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
