package resources_classes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates game resources (images, sounds, fonts) in a way that does not depend on
 * the working directory the game was started from.
 * <p>
 * The assets of this project live in a plain {@code resources/} folder next to {@code src/},
 * not on the class path, so the lookup order is:
 * <ol>
 *     <li>the path as given, relative to the current working directory (what the original
 *         JDK 8 era code relied on);</li>
 *     <li>relative to the folder the running classes were loaded from (so {@code out/} works);</li>
 *     <li>relative to the parent of that folder (the project root when running from {@code out/});</li>
 *     <li>finally the class path, for packaged/jar distributions.</li>
 * </ol>
 * Every failed lookup reports all the locations that were tried, instead of blowing up
 * later with a {@link NullPointerException} somewhere in the rendering code.
 */
public final class ResourceFile {

    private ResourceFile() {
    }

    /**
     * Opens a resource for reading.
     *
     * @param path resource path, e.g. {@code resources/sprites/map/base.png}
     * @return an open stream that the caller has to close
     * @throws IOException if the resource cannot be found anywhere
     */
    public static InputStream open(String path) throws IOException {
        for (File candidate : candidateFiles(path)) {
            if (candidate.isFile()) {
                return new FileInputStream(candidate);
            }
        }
        InputStream classpathStream = ResourceFile.class.getClassLoader().getResourceAsStream(path);
        if (classpathStream != null) {
            return classpathStream;
        }
        throw new FileNotFoundException(
                "Resource '" + path + "' not found. Looked in: " + triedLocations(path));
    }

    /**
     * @param path resource path
     * @return true when the resource can be found by {@link #open(String)}
     */
    public static boolean exists(String path) {
        for (File candidate : candidateFiles(path)) {
            if (candidate.isFile()) {
                return true;
            }
        }
        return ResourceFile.class.getClassLoader().getResource(path) != null;
    }

    /**
     * @param path resource path
     * @return a human readable list of every location this class looked at
     */
    public static String triedLocations(String path) {
        List<String> locations = new ArrayList<>();
        for (File candidate : candidateFiles(path)) {
            locations.add(candidate.getAbsolutePath());
        }
        locations.add("class path: " + path);
        return String.join(", ", locations);
    }

    private static List<File> candidateFiles(String path) {
        List<File> candidates = new ArrayList<>();
        candidates.add(new File(path));

        File codeSource = codeSourceDirectory();
        if (codeSource != null) {
            candidates.add(new File(codeSource, path));
            File parent = codeSource.getParentFile();
            if (parent != null) {
                candidates.add(new File(parent, path));
            }
        }
        return candidates;
    }

    /**
     * @return the directory the classes of this project were loaded from, or null when unknown
     */
    private static File codeSourceDirectory() {
        try {
            File location = new File(ResourceFile.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return location.isDirectory() ? location : location.getParentFile();
        } catch (Exception e) {
            return null;
        }
    }
}
