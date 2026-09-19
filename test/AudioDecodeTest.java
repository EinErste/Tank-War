import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes every audio file in {@code resources/music} and fails when one of them yields no sound.
 * <p>
 * This is the check that caught the real reason four of the music tracks were silent: the MP3
 * service provider returns 0 from {@code read()} before the first frame is ready, and a decode loop
 * that treats 0 as "end of stream" drops the whole file. So this test reads the same way
 * {@link resources_classes.AudioClip} does, and reports how many seconds it actually decoded.
 * <p>
 * The JDK cannot read MP3, so when the decoder from {@code lib/} is missing the MP3 files are
 * skipped with a notice instead of being reported as failures.
 */
public class AudioDecodeTest {

    /**
     * How many empty reads we tolerate before treating a stream as finished.
     */
    private static final int MAX_EMPTY_READS = 64;

    /**
     * Anything shorter than this did not really decode.
     */
    private static final double MIN_SECONDS = 0.05;

    private static int failures;

    public static void main(String[] args) throws Exception {
        boolean mp3Decoder = hasMp3Decoder();
        if (!mp3Decoder) {
            System.out.println("note: no MP3 decoder on the class path, MP3 files are skipped "
                    + "(run tools/get-deps.sh or tools\\get-deps.bat to fetch it)");
        }

        List<Path> files = new ArrayList<>();
        Files.walk(Paths.get("resources/music")).filter(Files::isRegularFile).sorted().forEach(files::add);

        int checked = 0;
        int skipped = 0;
        for (Path file : files) {
            String path = file.toString().replace('\\', '/');
            if (path.toLowerCase().endsWith(".mp3") && !mp3Decoder) {
                skipped++;
                continue;
            }
            checked++;
            try {
                double seconds = decodedSeconds(file);
                if (seconds < MIN_SECONDS) {
                    failures++;
                    System.out.printf("   FAIL %-56s decoded only %.2fs%n", path, seconds);
                } else {
                    System.out.printf("   ok   %-56s %7.2fs%n", path, seconds);
                }
            } catch (Exception e) {
                failures++;
                System.out.printf("   FAIL %-56s %s%n", path, e);
            }
        }

        System.out.println();
        System.out.println("checked " + checked + " file(s), skipped " + skipped + ", failures " + failures);
        System.out.println(failures == 0 ? "AUDIO DECODE TEST PASSED" : "AUDIO DECODE TEST FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    /**
     * @return true when an MP3 service provider is on the class path
     */
    private static boolean hasMp3Decoder() {
        try {
            Class.forName("javazoom.spi.mpeg.sampled.file.MpegAudioFileReader");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * @return how many seconds of PCM the file decodes to
     */
    private static double decodedSeconds(Path file) throws Exception {
        try (AudioInputStream source = AudioSystem.getAudioInputStream(
                new BufferedInputStream(new FileInputStream(file.toFile()), 65536))) {
            AudioFormat base = source.getFormat();
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    base.getSampleRate(), 16, base.getChannels(), base.getChannels() * 2,
                    base.getSampleRate(), false);
            AudioInputStream pcm = base.matches(target) ? source
                    : AudioSystem.getAudioInputStream(target, source);

            byte[] buffer = new byte[8192];
            long bytes = 0;
            int read;
            while ((read = readChunk(pcm, buffer)) > 0) {
                bytes += read;
            }
            return bytes / (double) (base.getChannels() * 2 * (int) base.getSampleRate());
        }
    }

    /**
     * Reads the next chunk, tolerating the 0 an {@link AudioInputStream} may return before its first
     * frame is ready. Only a negative value means the stream ended.
     */
    private static int readChunk(AudioInputStream stream, byte[] buffer) throws IOException {
        for (int emptyReads = 0; emptyReads < MAX_EMPTY_READS; emptyReads++) {
            int read = stream.read(buffer);
            if (read != 0) {
                return read;
            }
        }
        return -1;
    }
}
