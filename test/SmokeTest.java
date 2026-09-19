import game_content.GameEndPanel;
import resources_classes.AudioClip;
import game_content.GameFieldPanel;
import game_content.GameWindow;
import game_content.MenuPanel;
import map_tools.Level;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Drives the real Swing UI the way a player does, through every screen:
 * menu -> Play -> level 1 -> stage transition (this used to die on {@code Thread.stop}) -> game
 * over -> Menu -> pick stage 10 -> Play.
 * <p>
 * It uses the actual buttons and the level chooser, checks the panels that end up in the window,
 * asserts the audio state (menu music must stop when a level starts, battle music must play) and
 * fails on any uncaught exception from any thread, which is how the cross-thread label updates and
 * the leaked timers of the old code would show up.
 * <p>
 * A window opens on the desktop for the duration of the run, so it is skipped on a headless machine.
 */
public class SmokeTest {

    static final List<Throwable> errors = new CopyOnWriteArrayList<>();
    static AudioClip menuMusic;
    static AudioClip battleMusic;
    static GameWindow window;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIPPED: no display available (headless)");
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            errors.add(e);
            System.out.println("!! uncaught in thread " + t.getName() + ": " + e);
            e.printStackTrace(System.out);
        });

        SwingUtilities.invokeAndWait(() -> window = new GameWindow());
        System.out.println("phase 1: window created, panel=" + currentPanel());
        sleep(2000);
        check("menu is showing", currentPanel() instanceof MenuPanel);

        JButton playButton = findButton((Container) currentPanel(), "Play");
        check("menu has a Play button", playButton != null);
        menuMusic = (AudioClip) readField(currentPanel(), "music");
        check("menu music is playing in the menu", menuMusic != null && menuMusic.isPlaying());
        SwingUtilities.invokeAndWait(playButton::doClick);
        sleep(2500);   // load screen, then the level itself
        System.out.println("phase 2: level 1 started, panel=" + currentPanel());
        battleMusic = (AudioClip) readField(currentPanel(), "music");
        sleep(5000);
        check("level 1 is still running", currentPanel() instanceof GameFieldPanel);
        check("menu music stopped when the level started", !menuMusic.isPlaying());
        check("battle music is playing", battleMusic.isPlaying());

        GameFieldPanel first = (GameFieldPanel) currentPanel();
        System.out.println("phase 3: finishing level 1 (this used to throw UnsupportedOperationException)");
        SwingUtilities.invokeAndWait(first::roundWon);
        sleep(2500);
        System.out.println("   panel after transition = " + currentPanel());
        check("level transition moved on", currentPanel() instanceof GameFieldPanel
                && currentPanel() != first);

        System.out.println("phase 4: losing the game");
        SwingUtilities.invokeAndWait(((GameFieldPanel) currentPanel())::gameLost);
        sleep(2000);
        System.out.println("   panel = " + currentPanel());
        check("end panel is showing", currentPanel() instanceof GameEndPanel);

        System.out.println("phase 5: clicking 'Menu' on the end panel");
        JButton menuButton = findButton((Container) currentPanel(), "Menu");
        if (menuButton == null) {
            errors.add(new IllegalStateException("no Menu button on the end panel"));
            System.out.println("   !! Menu button not found");
        } else {
            SwingUtilities.invokeAndWait(menuButton::doClick);
            sleep(1500);
            System.out.println("   panel = " + currentPanel());
            check("back in the menu", currentPanel() instanceof MenuPanel);
        }

        System.out.println("phase 6: starting level 10 and letting it run");
        JComboBox<?> levels = findComboBox((Container) currentPanel());
        check("menu has a level chooser", levels != null);
        SwingUtilities.invokeAndWait(() -> levels.setSelectedItem(Level.TENTH));
        JButton playAgain = findButton((Container) currentPanel(), "Play");
        SwingUtilities.invokeAndWait(playAgain::doClick);
        sleep(3500);
        check("level 10 is running", currentPanel() instanceof GameFieldPanel);

        System.out.println();
        System.out.println(errors.isEmpty()
                ? "SMOKE TEST PASSED - no uncaught exceptions"
                : "SMOKE TEST FAILED - " + errors.size() + " uncaught exception(s)");
        System.exit(errors.isEmpty() ? 0 : 1);
    }

    static Component currentPanel() {
        Component[] components = ((Container) window.getContentPane()).getComponents();
        return components.length == 0 ? null : components[0];
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "   OK   " : "   FAIL ") + what);
        if (!ok) {
            errors.add(new IllegalStateException(what));
        }
    }

    static JButton findButton(Container container, String text) {
        if (container == null) {
            return null;
        }
        for (Component component : container.getComponents()) {
            if (component instanceof JButton && text.equals(((JButton) component).getText())) {
                return (JButton) component;
            }
            if (component instanceof Container) {
                JButton found = findButton((Container) component, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Reads a private field, so the test can look at the clip a panel is actually using. */
    static Object readField(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // keep looking up the hierarchy
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    static JComboBox<?> findComboBox(Container container) {
        if (container == null) {
            return null;
        }
        for (Component component : container.getComponents()) {
            if (component instanceof JComboBox) {
                return (JComboBox<?>) component;
            }
            if (component instanceof Container) {
                JComboBox<?> found = findComboBox((Container) component);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
