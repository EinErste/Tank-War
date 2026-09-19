import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Turns a PNG into a multi size Windows .ico file, for the packaged launcher
 * (jpackage --icon wants an .ico, and Windows Explorer picks the size it needs).
 * <p>
 * The image is centred on a square transparent canvas first, so a non-square source keeps its
 * aspect ratio, and every icon entry is written as a 32 bit BGRA DIB - the format every Windows
 * version and every packaging tool understands.
 * <p>
 * Usage: {@code java PngToIco <source.png> <target.ico>}
 */
public class PngToIco {

    private static final int[] SIZES = {16, 32, 48, 64, 128, 256};

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: PngToIco <source.png> <target.ico>");
            System.exit(2);
        }
        Path source = Paths.get(args[0]);
        Path target = Paths.get(args[1]);

        BufferedImage original = ImageIO.read(source.toFile());
        if (original == null) {
            throw new IOException("'" + source + "' is not a readable image");
        }
        BufferedImage square = toSquare(original);

        byte[][] images = new byte[SIZES.length][];
        for (int i = 0; i < SIZES.length; i++) {
            images[i] = toDib(scale(square, SIZES[i]));
        }
        writeIcon(target, images);
        System.out.println("wrote " + target + " (" + SIZES.length + " sizes from "
                + original.getWidth() + "x" + original.getHeight() + ")");
    }

    /**
     * Centres the image on a transparent square canvas, so a 733x600 logo does not get stretched.
     */
    private static BufferedImage toSquare(BufferedImage source) {
        int side = Math.max(source.getWidth(), source.getHeight());
        BufferedImage canvas = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, (side - source.getWidth()) / 2, (side - source.getHeight()) / 2, null);
        g.dispose();
        return canvas;
    }

    private static BufferedImage scale(BufferedImage source, int size) {
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        return scaled;
    }

    /**
     * @return a BITMAPINFOHEADER + BGRA pixels (bottom up) + AND mask, the classic icon image format
     */
    private static byte[] toDib(BufferedImage image) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);

        writeInt(out, 40);                     // BITMAPINFOHEADER size
        writeInt(out, width);
        writeInt(out, height * 2);             // XOR image + AND mask
        writeShort(out, 1);                    // colour planes
        writeShort(out, 32);                   // bits per pixel
        writeInt(out, 0);                      // BI_RGB, no compression
        writeInt(out, width * height * 4);     // size of the pixel data
        writeInt(out, 0);                      // horizontal resolution
        writeInt(out, 0);                      // vertical resolution
        writeInt(out, 0);                      // palette colours
        writeInt(out, 0);                      // important colours

        for (int y = height - 1; y >= 0; y--) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                out.writeByte(argb & 0xFF);            // blue
                out.writeByte((argb >> 8) & 0xFF);     // green
                out.writeByte((argb >> 16) & 0xFF);    // red
                out.writeByte((argb >>> 24) & 0xFF);   // alpha
            }
        }
        int maskRowBytes = ((width + 31) / 32) * 4;   // 1 bit per pixel, padded to 4 bytes
        for (int y = 0; y < height; y++) {
            for (int b = 0; b < maskRowBytes; b++) {
                out.writeByte(0);                     // transparency comes from the alpha channel
            }
        }
        out.flush();
        return bytes.toByteArray();
    }

    private static void writeIcon(Path target, byte[][] images) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(target)))) {
            writeShort(out, 0);                 // reserved
            writeShort(out, 1);                 // type: icon
            writeShort(out, images.length);     // number of images
            int offset = 6 + 16 * images.length;
            for (int i = 0; i < images.length; i++) {
                int size = SIZES[i];
                out.writeByte(size >= 256 ? 0 : size);   // 0 means 256
                out.writeByte(size >= 256 ? 0 : size);
                out.writeByte(0);               // palette colours
                out.writeByte(0);               // reserved
                writeShort(out, 1);             // colour planes
                writeShort(out, 32);            // bits per pixel
                writeInt(out, images[i].length);
                writeInt(out, offset);
                offset += images[i].length;
            }
            for (byte[] image : images) {
                out.write(image);
            }
        }
    }

    private static void writeShort(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
    }

    private static void writeInt(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
        out.writeByte((value >> 16) & 0xFF);
        out.writeByte((value >> 24) & 0xFF);
    }
}
