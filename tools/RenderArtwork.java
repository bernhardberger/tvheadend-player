import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Reproducible launcher, banner, and marketing artwork for TVHeadend Player.
 *
 * Mark: a diamond aperture layered outward from the play symbol — orange play,
 * navy core, cyan band, navy keyline. The rotated square is a deliberate nod to
 * the diamond at the centre of the Tvheadend logo; the chevrons around it are
 * not reproduced. Because the outermost layer is navy and the cyan sits inside
 * the mark, the whole thing is self-contained and holds on any ground.
 *
 * Every surface derives from {@link #diamond} and {@link #playSymbol}, so raster
 * exports, the monochrome adaptive layer, and the SVG wordmark cannot drift apart.
 */
public final class RenderArtwork {
    // Tvheadend-inspired palette; all mark geometry is original.
    private static final Color CYAN = new Color(0x00, 0xBC, 0xFA);
    private static final Color ORANGE = new Color(0xFA, 0x7F, 0x00);
    private static final Color NAVY = new Color(0x0B, 0x1B, 0x2E);
    private static final Color MUTED = new Color(0x0B, 0x1B, 0x2E, 190);

    /** Adaptive-icon safe zone: 66dp of the 108dp grid. */
    private static final double SAFE_ZONE = 66.0 / 108.0;

    // Mark geometry, normalised to the safe-zone square. Half-diagonals and
    // corner radii for the three nested diamonds, outermost first.
    private static final double OUTER_HALF = 0.5;
    private static final double BAND_HALF = 29.5 / 66.0;
    private static final double CORE_HALF = 24.0 / 66.0;
    private static final double OUTER_CORNER = 13.0 / 66.0;
    private static final double BAND_CORNER = 11.5 / 66.0;
    private static final double CORE_CORNER = 9.5 / 66.0;

    /**
     * The play symbol's horizontal centre. A right-pointing triangle carries its
     * mass toward the flat back edge, so centring the bounding box leaves the area
     * centroid visibly left. This offset puts the centroid a shade past centre,
     * which is what reads as level.
     */
    private static final double PLAY_CX = 56.4 / 66.0 - 54.0 / 66.0 + 0.5;
    private static final double PLAY_R = 15.5 / 66.0;
    /** Round join applied to the play symbol, as a fraction of its radius. */
    private static final double PLAY_JOIN = 0.18;

    private RenderArtwork() {}

    public static void main(String[] args) throws IOException {
        writeBanner();
        writeLogo();
        writeSocialPreview();
        writeAdaptiveLayers();
        writePlayStoreIcon();
        writeLegacyIcons();
        writeMonochrome();
        writeSvg();
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return graphics;
    }

    /** The cyan ground every surface sits on. */
    private static void paintField(Graphics2D graphics, int width, int height) {
        graphics.setColor(CYAN);
        graphics.fillRect(0, 0, width, height);
    }

    // ---------------------------------------------------------------- geometry

    /** Maps the normalised unit square onto the mark square at ({@code x},{@code y}). */
    private static AffineTransform frame(double x, double y, double size) {
        AffineTransform transform = AffineTransform.getTranslateInstance(x, y);
        transform.scale(size, size);
        return transform;
    }

    /** One nested diamond: a rounded square turned through 45 degrees. */
    private static Shape diamond(double x, double y, double size, double half, double corner) {
        double side = half * Math.sqrt(2.0);
        Shape square = new RoundRectangle2D.Double(0.5 - side / 2.0, 0.5 - side / 2.0, side, side, corner, corner);
        AffineTransform rotate = AffineTransform.getRotateInstance(Math.PI / 4.0, 0.5, 0.5);
        return frame(x, y, size).createTransformedShape(rotate.createTransformedShape(square));
    }

    /** Play symbol, softened by a round-joined outline unioned onto the triangle. */
    private static Shape playSymbol(double x, double y, double size) {
        double width = PLAY_R * 0.98;
        double height = PLAY_R * 1.08;
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(PLAY_CX - width * 0.46, 0.5 - height * 0.5);
        triangle.lineTo(PLAY_CX + width * 0.54, 0.5);
        triangle.lineTo(PLAY_CX - width * 0.46, 0.5 + height * 0.5);
        triangle.closePath();

        BasicStroke join = new BasicStroke(
                (float) (PLAY_R * PLAY_JOIN), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        Area symbol = new Area(triangle);
        symbol.add(new Area(join.createStrokedShape(triangle)));
        return frame(x, y, size).createTransformedShape(symbol);
    }

    /**
     * The mark's ink as a single silhouette, with the cyan band and the play
     * symbol knocked out. Themed icons and the monochrome layer use this.
     */
    private static Shape markSilhouette(double x, double y, double size) {
        Area ink = new Area(diamond(x, y, size, OUTER_HALF, OUTER_CORNER));
        Area band = new Area(diamond(x, y, size, BAND_HALF, BAND_CORNER));
        band.subtract(new Area(diamond(x, y, size, CORE_HALF, CORE_CORNER)));
        ink.subtract(band);
        ink.subtract(new Area(playSymbol(x, y, size)));
        return ink;
    }

    // ---------------------------------------------------------------- painting

    /** Draws the mark in a square of {@code size} with origin at ({@code x},{@code y}). */
    private static void drawMark(Graphics2D graphics, double x, double y, double size) {
        graphics.setColor(NAVY);
        graphics.fill(diamond(x, y, size, OUTER_HALF, OUTER_CORNER));
        graphics.setColor(CYAN);
        graphics.fill(diamond(x, y, size, BAND_HALF, BAND_CORNER));
        graphics.setColor(NAVY);
        graphics.fill(diamond(x, y, size, CORE_HALF, CORE_CORNER));
        graphics.setColor(ORANGE);
        graphics.fill(playSymbol(x, y, size));
    }

    /** Centres the mark inside a square surface of {@code extent}. */
    private static void drawCentredMark(Graphics2D graphics, double extent, double fraction) {
        double markSize = extent * fraction;
        drawMark(graphics, (extent - markSize) / 2.0, (extent - markSize) / 2.0, markSize);
    }

    private static void drawWordmark(Graphics2D graphics, int x, int titleBaseline, int titleSize, int subtitleBaseline) {
        graphics.setColor(NAVY);
        graphics.setFont(new Font("DejaVu Sans", Font.BOLD, titleSize));
        graphics.drawString("TVHeadend Player for TV", x, titleBaseline);
        graphics.setColor(MUTED);
        graphics.setFont(new Font("DejaVu Sans", Font.PLAIN, Math.max(12, titleSize / 3)));
        graphics.drawString("Live TV client for TVHeadend servers", x, subtitleBaseline);
    }

    private static void writeBanner() throws IOException {
        BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(image);
        paintField(graphics, 320, 180);
        drawMark(graphics, 37, 55, 71);
        graphics.setColor(NAVY);
        graphics.setFont(new Font("DejaVu Sans", Font.BOLD, 26));
        graphics.drawString("TVHeadend", 132, 84);
        graphics.drawString("Player", 132, 113);
        graphics.dispose();
        writePng(image, Path.of("app/src/main/res/drawable/banner.png"));
    }

    private static void writeLogo() throws IOException {
        BufferedImage image = new BufferedImage(960, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = graphics(image);
        paintField(graphics, 960, 300);
        drawMark(graphics, 60, 60, 180);
        drawWordmark(graphics, 290, 145, 43, 190);
        graphics.dispose();
        writePng(image, Path.of("artwork/tvheadend-player-logo.png"));
    }

    private static void writeSocialPreview() throws IOException {
        BufferedImage image = new BufferedImage(1280, 640, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(image);
        paintField(graphics, 1280, 640);
        drawMark(graphics, 100, 155, 330);
        drawWordmark(graphics, 505, 295, 46, 350);
        graphics.setColor(NAVY);
        graphics.setFont(new Font("DejaVu Sans", Font.BOLD, 21));
        graphics.drawString("REMOTE-FIRST  /  OPEN SOURCE  /  ANDROID TV", 505, 410);
        graphics.dispose();
        writePng(image, Path.of("artwork/github-social-preview.png"));
    }

    /**
     * Adaptive layers on the 432px grid. The outer diamond's vertices sit on the
     * 66dp safe zone, so neither the circular nor the rounded-square mask clips it.
     */
    private static BufferedImage renderAdaptiveBackground() {
        BufferedImage background = new BufferedImage(432, 432, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(background);
        paintField(graphics, 432, 432);
        graphics.dispose();
        return background;
    }

    private static BufferedImage renderAdaptiveForeground() {
        BufferedImage foreground = new BufferedImage(432, 432, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = graphics(foreground);
        drawCentredMark(graphics, 432, SAFE_ZONE);
        graphics.dispose();
        return foreground;
    }

    private static void writeAdaptiveLayers() throws IOException {
        writePng(renderAdaptiveBackground(), Path.of("app/src/main/res/drawable/ic_launcher_background.png"));
        writePng(renderAdaptiveForeground(), Path.of("app/src/main/res/drawable/ic_launcher_foreground.png"));
    }

    /**
     * Play listing icon: 512x512, full-bleed, opaque — Play applies its own mask.
     * No adaptive safe zone applies here, so the mark runs larger than on the
     * launcher and the artwork does not read as a small shape adrift in cyan.
     */
    private static void writePlayStoreIcon() throws IOException {
        BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(image);
        paintField(graphics, 512, 512);
        drawCentredMark(graphics, 512, 0.78);
        graphics.dispose();
        writePng(image, Path.of("app/src/main/ic_launcher-playstore.png"));
    }

    /**
     * Pre-26 fallbacks. minSdk 28 always uses the adaptive icon, so these exist
     * only as a self-contained plate for tooling that still reads mipmaps.
     */
    private static void writeLegacyIcons() throws IOException {
        String[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] sizes = {48, 72, 96, 144, 192};
        for (int index = 0; index < densities.length; index++) {
            int size = sizes[index];
            Path directory = Path.of("app/src/main/res/mipmap-" + densities[index]);
            writePng(renderLegacyIcon(size, false), directory.resolve("ic_launcher.png"));
            writePng(renderLegacyIcon(size, true), directory.resolve("ic_launcher_round.png"));
        }
    }

    private static BufferedImage renderLegacyIcon(int size, boolean round) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = graphics(image);
        graphics.setClip(round
                ? new Ellipse2D.Double(0, 0, size, size)
                : new RoundRectangle2D.Double(0, 0, size, size, size * 0.22, size * 0.22));
        paintField(graphics, size, size);
        // A round plate uses the same safe-zone inset as the adaptive icon.
        drawCentredMark(graphics, size, round ? SAFE_ZONE : 0.78);
        graphics.dispose();
        return image;
    }

    // ------------------------------------------------------------ path export

    private static String number(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        return text.endsWith(".00") ? text.substring(0, text.length() - 3) : text;
    }

    private static String toPathData(Shape shape) {
        StringBuilder data = new StringBuilder();
        PathIterator iterator = shape.getPathIterator(null);
        double[] coordinates = new double[6];
        while (!iterator.isDone()) {
            switch (iterator.currentSegment(coordinates)) {
                case PathIterator.SEG_MOVETO -> data.append("M").append(number(coordinates[0])).append(",").append(number(coordinates[1]));
                case PathIterator.SEG_LINETO -> data.append(" L").append(number(coordinates[0])).append(",").append(number(coordinates[1]));
                case PathIterator.SEG_QUADTO -> data.append(" Q").append(number(coordinates[0])).append(",").append(number(coordinates[1]))
                        .append(" ").append(number(coordinates[2])).append(",").append(number(coordinates[3]));
                case PathIterator.SEG_CUBICTO -> data.append(" C").append(number(coordinates[0])).append(",").append(number(coordinates[1]))
                        .append(" ").append(number(coordinates[2])).append(",").append(number(coordinates[3]))
                        .append(" ").append(number(coordinates[4])).append(",").append(number(coordinates[5]));
                case PathIterator.SEG_CLOSE -> data.append(" Z");
                default -> throw new IllegalStateException("Unexpected path segment");
            }
            iterator.next();
        }
        return data.toString();
    }

    private static void writeMonochrome() throws IOException {
        // 108dp adaptive grid; the mark fills the 66dp safe zone like the
        // foreground layer, so themed icons match the coloured icon exactly.
        double markSize = 108 * SAFE_ZONE;
        double origin = (108 - markSize) / 2.0;
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="108dp"
                    android:height="108dp"
                    android:viewportWidth="108"
                    android:viewportHeight="108">
                    <path
                        android:fillColor="#FFFFFFFF"
                        android:fillType="evenOdd"
                        android:pathData="%s" />
                </vector>
                """.formatted(toPathData(markSilhouette(origin, origin, markSize)));
        Files.writeString(
                Path.of("app/src/main/res/drawable/ic_launcher_monochrome.xml"),
                xml,
                StandardCharsets.UTF_8);
    }

    private static void writeSvg() throws IOException {
        double markSize = 180;
        double markOrigin = 60;
        String outer = toPathData(diamond(markOrigin, markOrigin, markSize, OUTER_HALF, OUTER_CORNER));
        String band = toPathData(diamond(markOrigin, markOrigin, markSize, BAND_HALF, BAND_CORNER));
        String core = toPathData(diamond(markOrigin, markOrigin, markSize, CORE_HALF, CORE_CORNER));
        String play = toPathData(playSymbol(markOrigin, markOrigin, markSize));
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="960" height="300" viewBox="0 0 960 300">
                  <title>TVHeadend Player logo</title>
                  <rect width="960" height="300" fill="#00BCFA"/>
                  <!-- Diamond aperture, layered outward from the play symbol -->
                  <path fill="#0B1B2E" d="%s"/>
                  <path fill="#00BCFA" d="%s"/>
                  <path fill="#0B1B2E" d="%s"/>
                  <!-- Player symbol -->
                  <path fill="#FA7F00" d="%s"/>
                  <text x="290" y="145" fill="#0b1b2e" font-family="DejaVu Sans, sans-serif" font-size="43" font-weight="700">TVHeadend Player for TV</text>
                  <text x="290" y="190" fill="#0b1b2e" fill-opacity="0.75" font-family="DejaVu Sans, sans-serif" font-size="18">Live TV client for TVHeadend servers</text>
                </svg>
                """.formatted(outer, band, core, play);
        Files.writeString(Path.of("artwork/tvheadend-player-logo.svg"), svg, StandardCharsets.UTF_8);
    }

    private static void writePng(BufferedImage image, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("PNG writer unavailable for " + path);
        }
    }
}
