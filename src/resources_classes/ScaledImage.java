package resources_classes;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

public class ScaledImage {

    public static final Color darkRed = new Color(172,17,21);

    /**
     * Loads an image from the resources folder and scales it to the requested size.
     * <p>
     * The path used to be handed straight to {@link Toolkit#createImage(String)}, which resolves it
     * against the working directory only. A packaged build runs from the folder the player unzipped,
     * where {@code resources/} is not, so every menu image came out blank - the menu showed a plain
     * white panel instead of the animated background. The path goes through {@link ResourceFile} now,
     * so it is found next to the jar as well.
     * <p>
     * Animated GIFs are loaded through the toolkit on purpose: a scaled instance of a toolkit image
     * keeps following the animation, while {@code ImageIO} would return the first frame only.
     * Everything else is read with {@code ImageIO}, which returns a fully decoded image right away.
     *
     * @param path   resource path, e.g. {@code resources/sprites/menu/background2.gif}
     * @param width  target width
     * @param height target height
     * @return the scaled image
     */
    public static Image create(String path, int width, int height){
        Image image = load(path);
        return image.getScaledInstance(width,height,Image.SCALE_DEFAULT);
    }

    /**
     * @param path resource path
     * @return the image, never null
     */
    private static Image load(String path) {
        URL url;
        try {
            url = ResourceFile.url(path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load image '" + path + "'. " + e.getMessage(), e);
        }

        if (path.toLowerCase().endsWith(".gif")) {
            Image animated = Toolkit.getDefaultToolkit().createImage(url);
            if (animated == null) {
                throw new IllegalStateException("Cannot load image '" + path + "' from " + url);
            }
            return animated;
        }

        BufferedImage loaded;
        try {
            loaded = ImageIO.read(url);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load image '" + path + "'. " + e.getMessage(), e);
        }
        if (loaded == null) {
            throw new IllegalStateException("Cannot decode image '" + path
                    + "': the file is not a readable image format");
        }
        return loaded;
    }
}
