import game_content.GameField;
import game_objects.map_objects.MapObject;
import game_objects.map_objects.impassables.Base;
import game_objects.movables.Direction;
import game_objects.movables.PlayerTank;
import game_objects.movables.Tank;
import map_tools.Level;
import map_tools.Map;

import java.awt.*;
import java.lang.reflect.Method;

/**
 * Headless checks of the game rules that do not need a window.
 * <p>
 * Covered: a tank is blocked by a wall it is not standing in, but can always move while it
 * overlaps one (turning snaps a tank onto the grid, which used to leave it stuck inside a wall for
 * the rest of the stage), the same rule for tank against tank, the field edges, and the spawn data
 * of all ten stages - the base position plus the player and enemy spawn squares must be free of
 * collidable objects.
 */
public class LogicTest {

    static int failures = 0;

    public static void main(String[] args) throws Exception {
        collisionRule();
        levelData();
        System.out.println(failures == 0 ? "\nLOGIC TEST PASSED" : "\nLOGIC TEST FAILED (" + failures + ")");
        System.exit(failures == 0 ? 0 : 1);
    }

    static void collisionRule() throws Exception {
        System.out.println("== collision rule (tank must be able to leave a wall it is stuck in)");
        GameField field = new GameField(Level.FIRST, null);
        field.setBounds(0, 0, GameField.FIELD_DIMENSIONS, GameField.FIELD_DIMENSIONS);
        Method check = GameField.class.getDeclaredMethod("checkWallCollisions", Tank.class);
        check.setAccessible(true);

        // a brick of level 1 sits at 48,48 and is 48x48 large
        Tank approaching = new PlayerTank(0, 48, Direction.EAST);
        approaching.changeDirection(Direction.EAST);          // dx = +SCALE
        expect("blocked when driving into a wall from free space",
                Boolean.TRUE.equals(check.invoke(field, approaching)));

        Tank inside = new PlayerTank(60, 60, Direction.EAST);
        inside.changeDirection(Direction.EAST);
        expect("free to move while overlapping the wall (used to be stuck forever)",
                Boolean.FALSE.equals(check.invoke(field, inside)));

        Tank outside = new PlayerTank(0, 48, Direction.WEST);
        outside.changeDirection(Direction.WEST);
        expect("blocked when leaving the field", Boolean.TRUE.equals(check.invoke(field, outside)));

        Tank free = new PlayerTank(0, 0, Direction.SOUTH);
        free.changeDirection(Direction.SOUTH);
        expect("free to move in open space", Boolean.FALSE.equals(check.invoke(field, free)));

        // the same rule for tanks: a tank that overlaps another one may back out
        Method tankCheck = GameField.class.getDeclaredMethod("checkTankCollisions", Tank.class);
        tankCheck.setAccessible(true);
        java.lang.reflect.Field tankSet = GameField.class.getDeclaredField("tanks");
        tankSet.setAccessible(true);
        java.util.Set<Tank> tanks = (java.util.Set<Tank>) tankSet.get(field);
        Tank a = new PlayerTank(0, 0, Direction.EAST);
        Tank b = new PlayerTank(48, 0, Direction.EAST);       // touching a, not overlapping
        tanks.add(a);
        tanks.add(b);
        a.changeDirection(Direction.EAST);
        expect("blocked when driving into another tank", Boolean.TRUE.equals(tankCheck.invoke(field, a)));
        tanks.clear();                                        // isolate the next assertion
        Tank c = new PlayerTank(0, 0, Direction.EAST);
        Tank d = new PlayerTank(12, 0, Direction.EAST);       // already overlapping c
        c.changeDirection(Direction.EAST);
        tanks.add(c);
        tanks.add(d);
        expect("free to move while overlapping another tank",
                Boolean.FALSE.equals(tankCheck.invoke(field, c)));

        field.dispose();
        field.removeNotify();
    }

    static void levelData() throws Exception {
        System.out.println("\n== level data (spawn points must not be inside walls)");
        for (Level level : Level.values()) {
            Map map = Map.getLevelMap(level);
            Base base = map.getBase();
            expect(level + ": base present at " + base.getX() + "," + base.getY(),
                    base != null && base.getX() == 12 * GameField.BYTE
                            && base.getY() == 24 * GameField.BYTE);
            expect(level + ": player spawn is clear", clear(map, 8 * GameField.BYTE, 24 * GameField.BYTE));
            expect(level + ": base is not walled in on the spot", clear(map, 12 * GameField.BYTE, 24 * GameField.BYTE));
            for (int x : new int[]{0, GameField.BYTE * 12, GameField.BYTE * 24}) {
                expect(level + ": enemy spawn at x=" + x + " is clear", clear(map, x, 0));
            }
        }
    }

    /** @return true when no collidable map object overlaps the 2x2 tile square at x,y */
    static boolean clear(Map map, int x, int y) {
        Rectangle square = new Rectangle(x, y, 2 * GameField.BYTE, 2 * GameField.BYTE);
        for (MapObject object : map) {
            if (object.isCollidable() && !(object instanceof Base) && object.getBounds().intersects(square)) {
                System.out.println("        blocked by " + object.getClass().getSimpleName()
                        + " at " + object.getBounds());
                return false;
            }
        }
        return true;
    }

    static void expect(String what, boolean ok) {
        System.out.println((ok ? "   OK   " : "   FAIL ") + what);
        if (!ok) {
            failures++;
        }
    }
}
