import game_content.GameWindow;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Screenshots the menu and fails when it is blank.
 * <p>
 * This is the regression test for the packaged build that showed a plain white menu: the menu
 * background was loaded with {@code Toolkit.createImage(String)}, which only resolves against the
 * working directory, so in a build that runs from somewhere else every menu image was missing while
 * the game itself still worked. Nothing else noticed, because no test looked at the pixels.
 * <p>
 * It opens a real window, brings it to the front, captures it and checks that the menu is not a flat
 * fill: at least a quarter of the pixels must be far from white and there must be plenty of distinct
 * colours. The capture is also written to {@code test/out/menu-screenshot.png} so it can be looked at.
 * <p>
 * Skipped on a headless machine.
 */
public class MenuBackgroundTest {

    private static final double MIN_NON_WHITE_FRACTION = 0.25;
    private static final int MIN_DISTINCT_COLOURS = 200;
    private static final int WHITE_TOLERANCE = 40;
    private static final String SCREENSHOT = "test/out/menu-screenshot.png";

    private static GameWindow window;
    private static int failures;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIPPED: no display available (headless)");
            return;
        }

        SwingUtilities.invokeAndWait(() -> window = new GameWindow());
        SwingUtilities.invokeAndWait(() -> {
            window.toFront();
            window.setAlwaysOnTop(true);
            window.repaint();
        });
        Thread.sleep(4000);   // let the background image load and a few frames paint

        Rectangle bounds = window.getBounds();
        BufferedImage shot;
        try {
            shot = new Robot().createScreenCapture(bounds);
        } catch (Exception e) {
            System.out.println("   FAIL could not capture the screen: " + e);
            finish();
            return;
        }

        File out = new File(SCREENSHOT);
        if (out.getParentFile() != null) {
            out.getParentFile().mkdirs();
        }
        ImageIO.write(shot, "png", out);

        int nonWhite = 0;
        Set<Integer> colours = new HashSet<>();
        for (int y = 0; y < shot.getHeight(); y++) {
            for (int x = 0; x < shot.getWidth(); x++) {
                int rgb = shot.getRGB(x, y) & 0xFFFFFF;
                colours.add(rgb);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (Math.abs(r - 255) > WHITE_TOLERANCE
                        || Math.abs(g - 255) > WHITE_TOLERANCE
                        || Math.abs(b - 255) > WHITE_TOLERANCE) {
                    nonWhite++;
                }
            }
        }
        double fraction = nonWhite / (double) (shot.getWidth() * shot.getHeight());
        System.out.printf("   menu capture %dx%d: %.0f%% non-white pixels, %d distinct colours%n",
                shot.getWidth(), shot.getHeight(), fraction * 100, colours.size());
        System.out.println("   screenshot: " + out.getAbsolutePath());

        expect("the menu is not a blank white panel", fraction >= MIN_NON_WHITE_FRACTION);
        expect("the menu is not a flat fill (background image is there)",
                colours.size() >= MIN_DISTINCT_COLOURS);

        SwingUtilities.invokeAndWait(() -> {
            window.setAlwaysOnTop(false);
            window.dispose();
        });
        finish();
    }

    private static void finish() {
        System.out.println();
        System.out.println(failures == 0 ? "MENU BACKGROUND TEST PASSED" : "MENU BACKGROUND TEST FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void expect(String what, boolean passed) {
        System.out.println((passed ? "   OK   " : "   FAIL ") + what);
        if (!passed) {
            failures++;
        }
    }
}
