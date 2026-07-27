import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
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
 * Mark: an original segmented cyan widescreen with clipped corners, cross-shaped
 * gaps, and an orange play symbol inside a circular dark knockout. The negative space
 * recalls Tvheadend's visual rhythm without copying its radial logo geometry.
 *
 * Every surface derives from {@link #television} and {@link #playTriangle}, so
 * raster exports, the monochrome adaptive layer, and the SVG wordmark cannot
 * drift apart.
 */
public final class RenderArtwork {
    // Tvheadend-inspired palette; all mark geometry is original.
    private static final Color CYAN = new Color(0x00, 0xBC, 0xFA);
    private static final Color ORANGE = new Color(0xFA, 0x7F, 0x00);

    private static final Color NAVY = new Color(0x08, 0x0F, 0x1E);
    private static final Color HIGHLIGHT = new Color(0x11, 0x22, 0x40);
    private static final Color GLOW = new Color(0x1B, 0x39, 0x5C);
    private static final Color TILE = new Color(0x10, 0x1D, 0x33);
    private static final Color TILE_STROKE = new Color(0x32, 0x4A, 0x6B);
    private static final Color WHITE = new Color(0xF4, 0xF7, 0xFB);
    private static final Color MUTED = new Color(0xB5, 0xC1, 0xD4);

    private static final double PLAY_CORNER = 0.025;

    /** Fraction of a plate tile occupied by the mark itself. */
    private static final double TILE_MARK = 0.80;
    /** Adaptive-icon safe zone: 66dp of the 108dp grid. */
    private static final double SAFE_ZONE = 66.0 / 108.0;

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

    private static void paintBackground(Graphics2D graphics, int width, int height) {
        graphics.setPaint(new GradientPaint(0, 0, HIGHLIGHT, width, height, NAVY));
        graphics.fillRect(0, 0, width, height);
    }

    /** Adds a soft centred glow so the mark sits on depth instead of a flat field. */
    private static void paintGlow(Graphics2D graphics, float cx, float cy, float radius) {
        graphics.setPaint(new RadialGradientPaint(
                new Point2D.Float(cx, cy),
                radius,
                new float[] {0f, 1f},
                new Color[] {new Color(GLOW.getRed(), GLOW.getGreen(), GLOW.getBlue(), 130), new Color(GLOW.getRed(), GLOW.getGreen(), GLOW.getBlue(), 0)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        graphics.fill(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2f, radius * 2f));
    }

    // ---------------------------------------------------------------- geometry

    /**
     * Builds a closed polygon with circular-arc corners, in mark-normalised
     * coordinates that {@code frame} then maps onto the target square.
     */
    private static Shape roundedPolygon(double[][] points, double radius, double maxTangent, AffineTransform frame) {
        GeneralPath path = new GeneralPath(Path2D.WIND_NON_ZERO);
        int count = points.length;
        for (int index = 0; index < count; index++) {
            double[] previous = points[(index + count - 1) % count];
            double[] current = points[index];
            double[] next = points[(index + 1) % count];

            double[] toPrevious = unit(current, previous);
            double[] toNext = unit(current, next);
            double halfAngle = Math.acos(clamp(toPrevious[0] * toNext[0] + toPrevious[1] * toNext[1])) / 2.0;
            double tangent = Math.min(radius / Math.tan(halfAngle), maxTangent);
            tangent = Math.min(tangent, 0.5 * Math.min(distance(current, previous), distance(current, next)));

            double startX = current[0] + toPrevious[0] * tangent;
            double startY = current[1] + toPrevious[1] * tangent;
            double endX = current[0] + toNext[0] * tangent;
            double endY = current[1] + toNext[1] * tangent;

            if (index == 0) {
                path.moveTo(startX, startY);
            } else {
                path.lineTo(startX, startY);
            }
            path.quadTo(current[0], current[1], endX, endY);
        }
        path.closePath();
        return frame.createTransformedShape(path);
    }

    private static double[] unit(double[] from, double[] to) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double length = Math.hypot(dx, dy);
        return new double[] {dx / length, dy / length};
    }

    private static double distance(double[] a, double[] b) {
        return Math.hypot(b[0] - a[0], b[1] - a[1]);
    }

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    /** Maps the normalised unit square onto the mark square at ({@code x},{@code y}). */
    private static AffineTransform frame(double x, double y, double size) {
        AffineTransform transform = AffineTransform.getTranslateInstance(x, y);
        transform.scale(size, size);
        return transform;
    }

    /** Four filled corner blocks separated by narrow horizontal and vertical gaps. */
    private static Shape television(double x, double y, double size) {
        double[][] topLeft = {
            {0.15, 0.17},
            {0.47, 0.17},
            {0.47, 0.47},
            {0.05, 0.47},
            {0.05, 0.28},
        };
        AffineTransform frame = frame(x, y, size);
        Area segments = new Area();
        for (int horizontal = 0; horizontal < 2; horizontal++) {
            for (int vertical = 0; vertical < 2; vertical++) {
                AffineTransform mirrored = new AffineTransform(frame);
                mirrored.translate(horizontal, vertical);
                mirrored.scale(horizontal == 0 ? 1.0 : -1.0, vertical == 0 ? 1.0 : -1.0);
                segments.add(new Area(roundedPolygon(topLeft, 0.018, 0.024, mirrored)));
            }
        }
        segments.subtract(new Area(playClearance(x, y, size)));
        return segments;
    }

    /** Circular knockout that keeps the four cyan blocks optically balanced. */
    private static Shape playClearance(double x, double y, double size) {
        return frame(x, y, size).createTransformedShape(
                new Ellipse2D.Double(0.26, 0.25, 0.48, 0.48));
    }

    /** Standalone play symbol, nudged right for optical centring. */
    private static Shape playTriangle(double x, double y, double size) {
        double[][] points = {
            {0.40, 0.35},
            {0.67, 0.49},
            {0.40, 0.63},
        };
        return roundedPolygon(points, PLAY_CORNER, PLAY_CORNER * 3.0, frame(x, y, size));
    }

    // ---------------------------------------------------------------- painting

    /**
     * Draws the app mark in a square of {@code size} with origin at ({@code x},{@code y}).
     * With {@code tile} the mark sits on a rounded plate for banner and marketing use.
     */
    private static void drawMark(Graphics2D graphics, double x, double y, double size, boolean tile) {
        double markX = x;
        double markY = y;
        double markSize = size;
        if (tile) {
            graphics.setColor(TILE);
            graphics.fill(new RoundRectangle2D.Double(x, y, size, size, size * 0.22, size * 0.22));
            graphics.setColor(TILE_STROKE);
            graphics.setStroke(new BasicStroke((float) Math.max(2.0, size * 0.018)));
            graphics.draw(new RoundRectangle2D.Double(
                    x + size * 0.01,
                    y + size * 0.01,
                    size * 0.98,
                    size * 0.98,
                    size * 0.22,
                    size * 0.22));
            markSize = size * TILE_MARK;
            markX = x + (size - markSize) / 2.0;
            markY = y + (size - markSize) / 2.0;
        }

        graphics.setColor(CYAN);
        graphics.fill(television(markX, markY, markSize));
        graphics.setColor(ORANGE);
        graphics.fill(playTriangle(markX, markY, markSize));
    }

    private static void drawWordmark(Graphics2D graphics, int x, int titleBaseline, int titleSize, int subtitleBaseline) {
        graphics.setColor(WHITE);
        graphics.setFont(new Font("DejaVu Sans", Font.BOLD, titleSize));
        graphics.drawString("TVHeadend Player", x, titleBaseline);
        graphics.setColor(MUTED);
        graphics.setFont(new Font("DejaVu Sans", Font.PLAIN, Math.max(12, titleSize / 3)));
        graphics.drawString("Live TV client for TVHeadend servers", x, subtitleBaseline);
    }

    private static void writeBanner() throws IOException {
        BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(image);
        paintBackground(graphics, 320, 180);
        paintGlow(graphics, 72, 90, 150);
        drawMark(graphics, 24, 42, 96, true);
        graphics.setColor(WHITE);
        graphics.setFont(new Font("DejaVu Sans", Font.BOLD, 25));
        graphics.drawString("TVHeadend", 138, 78);
        graphics.drawString("Player", 138, 108);
        graphics.setColor(MUTED);
        graphics.setFont(new Font("DejaVu Sans", Font.PLAIN, 12));
        graphics.drawString("Live TV for Android TV", 138, 132);
        graphics.dispose();
        writePng(image, Path.of("app/src/main/res/drawable/banner.png"));
    }

    private static void writeLogo() throws IOException {
        BufferedImage image = new BufferedImage(960, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = graphics(image);
        paintBackground(graphics, 960, 300);
        paintGlow(graphics, 150, 150, 300);
        drawMark(graphics, 55, 55, 190, true);
        drawWordmark(graphics, 290, 145, 54, 190);
        graphics.dispose();
        writePng(image, Path.of("artwork/tvheadend-player-logo.png"));
    }

    private static void writeSocialPreview() throws IOException {
        BufferedImage image = new BufferedImage(1280, 640, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(image);
        paintBackground(graphics, 1280, 640);
        paintGlow(graphics, 265, 320, 520);
        drawMark(graphics, 95, 150, 340, true);
        drawWordmark(graphics, 505, 295, 62, 350);
        graphics.setColor(ORANGE);
        graphics.setFont(new Font("DejaVu Sans", Font.BOLD, 21));
        graphics.drawString("REMOTE-FIRST  /  OPEN SOURCE  /  ANDROID TV", 505, 410);
        graphics.dispose();
        writePng(image, Path.of("artwork/github-social-preview.png"));
    }

    /**
     * Adaptive layers on the 432px grid. The complete segmented widescreen
     * stays inside the 66dp safe zone so circular masks cannot clip the mark.
     */
    private static BufferedImage renderAdaptiveBackground() {
        BufferedImage background = new BufferedImage(432, 432, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(background);
        paintBackground(graphics, 432, 432);
        paintGlow(graphics, 216, 216, 340);
        graphics.dispose();
        return background;
    }

    private static BufferedImage renderAdaptiveForeground() {
        BufferedImage foreground = new BufferedImage(432, 432, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = graphics(foreground);
        double markSize = 432 * SAFE_ZONE;
        drawMark(graphics, (432 - markSize) / 2.0, (432 - markSize) / 2.0, markSize, false);
        graphics.dispose();
        return foreground;
    }

    private static void writeAdaptiveLayers() throws IOException {
        writePng(renderAdaptiveBackground(), Path.of("app/src/main/res/drawable/ic_launcher_background.png"));
        writePng(renderAdaptiveForeground(), Path.of("app/src/main/res/drawable/ic_launcher_foreground.png"));
    }

    /** Play listing icon: 512x512, full-bleed, opaque — Play applies its own mask. */
    private static void writePlayStoreIcon() throws IOException {
        BufferedImage composite = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(composite);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(renderAdaptiveBackground(), 0, 0, 512, 512, null);
        graphics.drawImage(renderAdaptiveForeground(), 0, 0, 512, 512, null);
        graphics.dispose();
        writePng(composite, Path.of("app/src/main/ic_launcher-playstore.png"));
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
        paintBackground(graphics, size, size);
        paintGlow(graphics, size / 2f, size / 2f, size * 0.78f);
        // A round plate uses the same safe-zone inset as the adaptive icon.
        double markSize = size * (round ? SAFE_ZONE : 0.78);
        drawMark(graphics, (size - markSize) / 2.0, (size - markSize) / 2.0, markSize, false);
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

    /** Mark as one path containing the circularly knocked-out body and play symbol. */
    private static String markPathData(double x, double y, double size) {
        return toPathData(television(x, y, size)) + " " + toPathData(playTriangle(x, y, size));
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
                """.formatted(markPathData(origin, origin, markSize));
        Files.writeString(
                Path.of("app/src/main/res/drawable/ic_launcher_monochrome.xml"),
                xml,
                StandardCharsets.UTF_8);
    }

    private static void writeSvg() throws IOException {
        double markSize = 190 * TILE_MARK;
        double markOrigin = 55 + (190 - markSize) / 2.0;
        String television = toPathData(television(markOrigin, markOrigin, markSize));
        String play = toPathData(playTriangle(markOrigin, markOrigin, markSize));
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="960" height="300" viewBox="0 0 960 300">
                  <title>TVHeadend Player logo</title>
                  <defs>
                    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0" stop-color="#112240"/>
                      <stop offset="1" stop-color="#080f1e"/>
                    </linearGradient>
                    <radialGradient id="glow" cx="0.5" cy="0.5" r="0.5">
                      <stop offset="0" stop-color="#1b395c" stop-opacity="0.5"/>
                      <stop offset="1" stop-color="#1b395c" stop-opacity="0"/>
                    </radialGradient>
                  </defs>
                  <rect width="960" height="300" fill="url(#bg)"/>
                  <circle cx="150" cy="150" r="300" fill="url(#glow)"/>
                  <rect x="55" y="55" width="190" height="190" rx="42" fill="#101d33" stroke="#324a6b" stroke-width="4"/>
                  <!-- Original segmented widescreen -->
                  <path fill="#00BCFA" fill-rule="evenodd" d="%s"/>
                  <!-- Player symbol -->
                  <path fill="#FA7F00" d="%s"/>
                  <text x="290" y="145" fill="#f4f7fb" font-family="DejaVu Sans, sans-serif" font-size="54" font-weight="700">TVHeadend Player</text>
                  <text x="290" y="190" fill="#b5c1d4" font-family="DejaVu Sans, sans-serif" font-size="18">Live TV client for TVHeadend servers</text>
                </svg>
                """.formatted(television, play);
        Files.writeString(Path.of("artwork/tvheadend-player-logo.svg"), svg, StandardCharsets.UTF_8);
    }

    private static void writePng(BufferedImage image, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("PNG writer unavailable for " + path);
        }
    }
}
