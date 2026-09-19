package resources_classes;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal, dependency free replacement for the JavaFX class the game used to rely on
 * ({@code javafx.scene.media.AudioClip} - only shipped with Oracle JDK 8).
 * <p>
 * It is backed by {@link javax.sound.sampled} and keeps the small part of the JavaFX API the
 * game actually uses: {@link #play()}, {@link #stop()}, {@link #isPlaying()} and
 * {@link #setVolume(double)}.
 * <h3>Playback modes</h3>
 * <ul>
 *     <li><b>preloaded</b> (short sound effects) - the whole clip is decoded into memory the first
 *         time it is played, so replaying it costs nothing and several voices can overlap,</li>
 *     <li><b>streaming</b> (music) - the file is decoded while it plays, which keeps memory usage
 *         flat no matter how long the track is. A streaming clip plays a single voice at a time.</li>
 * </ul>
 * <h3>Formats</h3>
 * WAV is decoded by the JDK itself. MP3 (all the music and most of the sound effects of this game)
 * needs an {@code javax.sound} service provider on the class path - {@code lib/jlayer-1.0.1.jar},
 * see {@code README.md}. When it is missing, or when the machine has no audio device at all, the
 * game still runs: the affected clip is skipped and one short warning is printed.
 */
public class AudioClip {

    /**
     * Size of the buffer handed to the mixer, and of the chunks we read/decode.
     */
    private static final int BUFFER_BYTES = 8192;

    /**
     * How many empty reads we tolerate before we treat a stream as finished, see {@link #readChunk}.
     */
    private static final int MAX_EMPTY_READS = 64;

    /**
     * Set once when no audio output can be opened, so we stop retrying (and stop logging).
     */
    private static volatile boolean outputUnavailable = false;

    /**
     * Paths we already complained about, to keep the console readable during play.
     */
    private static final Set<String> reportedProblems = ConcurrentHashMap.newKeySet();

    private final String path;
    private final boolean preload;
    private final boolean polyphonic;

    private volatile double volume = 1;

    /**
     * Currently running playback threads; {@code isPlaying()} is simply "any voice left?".
     */
    private final Set<Thread> voices = ConcurrentHashMap.newKeySet();
    /**
     * Lines owned by those voices, so {@link #stop()} can cut them off immediately.
     */
    private final Set<SourceDataLine> lines = ConcurrentHashMap.newKeySet();

    private volatile byte[] decodedSamples;
    private volatile AudioFormat decodedFormat;

    /**
     * Creates a streaming (music style) clip.
     *
     * @param path resource path, e.g. {@code resources/music/menu/2+2.mp3}
     */
    public AudioClip(String path) {
        this(path, false);
    }

    /**
     * @param path    resource path, e.g. {@code resources/music/sounds/explosion.wav}
     * @param preload true for short sound effects that should be decoded into memory once and
     *                allowed to overlap, false for music that is streamed from disk
     */
    public AudioClip(String path, boolean preload) {
        this.path = path;
        this.preload = preload;
        this.polyphonic = preload;
    }

    /**
     * Starts (or restarts) playback from the beginning of the clip.
     */
    public void play() {
        play(false);
    }

    /**
     * @param loop true to repeat the clip until {@link #stop()} is called
     */
    public void play(boolean loop) {
        if (outputUnavailable || volume <= 0) {
            return;
        }
        if (!polyphonic) {
            stop();
        }
        Thread voice = new Thread(() -> playVoice(loop), "audio:" + fileName());
        voice.setDaemon(true);
        voices.add(voice);
        voice.start();
    }

    /**
     * Stops every voice of this clip right away.
     */
    public void stop() {
        for (Thread voice : voices) {
            voice.interrupt();
        }
        voices.clear();
        for (SourceDataLine line : lines) {
            close(line);
        }
        lines.clear();
    }

    /**
     * @return true while at least one voice of this clip is playing
     */
    public boolean isPlaying() {
        return !voices.isEmpty();
    }

    /**
     * @param volume 0 (silent) to 1 (full), applied to the samples before they reach the mixer
     */
    public void setVolume(double volume) {
        this.volume = Math.max(0, Math.min(1, volume));
    }

    public double getVolume() {
        return volume;
    }

    /**
     * @return true when this clip can actually produce sound on this machine
     */
    public boolean isPlayable() {
        return !outputUnavailable;
    }

    @Override
    public String toString() {
        return "AudioClip[" + path + "]";
    }

    private String fileName() {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /**
     * Decodes and plays the clip on the calling (voice) thread.
     */
    private void playVoice(boolean loop) {
        SourceDataLine line = null;
        try {
            do {
                try (AudioInputStream stream = preload ? preloadedStream() : openStream()) {
                    if (line == null) {
                        line = openLine(stream.getFormat());
                        if (line == null) {
                            return;
                        }
                        lines.add(line);
                    }
                    byte[] buffer = new byte[BUFFER_BYTES];
                    int read;
                    while ((read = readChunk(stream, buffer)) > 0) {
                        applyVolume(buffer, read);
                        line.write(buffer, 0, read);
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                    }
                }
            } while (loop && !Thread.currentThread().isInterrupted());
            if (!Thread.currentThread().isInterrupted()) {
                line.drain();
            }
        } catch (Exception e) {
            reportProblem(e);
        } finally {
            voices.remove(Thread.currentThread());
            if (line != null) {
                lines.remove(line);
                close(line);
            }
        }
    }

    /**
     * Opens the resource and converts it to 16 bit signed PCM, the only format we mix ourselves.
     */
    /**
     * Reads the next chunk of PCM.
     * <p>
     * An {@link AudioInputStream} is allowed to return 0 before the first frame is ready; the MP3
     * service provider does exactly that for some files (the whole track would be skipped if a 0
     * were mistaken for the end of the stream). Only a negative value means "no more data".
     *
     * @return the number of bytes read into the buffer, or -1 at the end of the stream
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

    private AudioInputStream openStream() throws Exception {
        InputStream raw = new BufferedInputStream(ResourceFile.open(path), 64 * 1024);
        AudioInputStream stream = AudioSystem.getAudioInputStream(raw);
        AudioFormat sourceFormat = stream.getFormat();
        if (sourceFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED
                || sourceFormat.getSampleSizeInBits() != 16) {
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.getSampleRate(),
                    16,
                    sourceFormat.getChannels(),
                    sourceFormat.getChannels() * 2,
                    sourceFormat.getSampleRate(),
                    false);
            stream = AudioSystem.getAudioInputStream(targetFormat, stream);
        }
        return stream;
    }

    /**
     * @return the clip decoded into memory, decoding it on first use
     */
    private AudioInputStream preloadedStream() throws Exception {
        byte[] samples = decodedSamples;
        if (samples == null) {
            synchronized (this) {
                samples = decodedSamples;
                if (samples == null) {
                    try (AudioInputStream stream = openStream()) {
                        ByteArrayOutputStream collected = new ByteArrayOutputStream();
                        byte[] buffer = new byte[BUFFER_BYTES];
                        int read;
                        while ((read = readChunk(stream, buffer)) > 0) {
                            collected.write(buffer, 0, read);
                        }
                        samples = collected.toByteArray();
                        decodedFormat = stream.getFormat();
                    }
                    decodedSamples = samples;
                }
            }
        }
        AudioFormat format = decodedFormat;
        return new AudioInputStream(new ByteArrayInputStream(samples), format,
                samples.length / format.getFrameSize());
    }

    private SourceDataLine openLine(AudioFormat format) {
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, BUFFER_BYTES * 2);
            line.start();
            return line;
        } catch (LineUnavailableException | IllegalArgumentException e) {
            if (!outputUnavailable) {
                outputUnavailable = true;
                System.err.println("[audio] no audio output is available on this machine ("
                        + e.getMessage() + "); the game continues without sound");
            }
            return null;
        }
    }

    /**
     * Scales 16 bit signed samples in place, which is how {@link #setVolume(double)} works.
     */
    private void applyVolume(byte[] buffer, int length) {
        double gain = volume;
        if (gain >= 0.999) {
            return;
        }
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            sample = (int) Math.round(sample * gain);
            buffer[i] = (byte) sample;
            buffer[i + 1] = (byte) (sample >> 8);
        }
    }

    private void close(SourceDataLine line) {
        try {
            line.stop();
            line.flush();
            line.close();
        } catch (Exception ignored) {
            // the line is already gone, nothing left to do
        }
    }

    private void reportProblem(Exception e) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        if (reportedProblems.add(path)) {
            System.err.println("[audio] '" + path + "' cannot be played: " + e
                    + (e instanceof javax.sound.sampled.UnsupportedAudioFileException
                    ? " (MP3 playback needs the decoder jars in lib/, run tools/get-deps.* - see README.md)"
                    : ""));
        }
    }
}
