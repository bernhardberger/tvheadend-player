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
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 * Reproducible launcher, banner, and brand artwork for Tvheadend Player.
 *
 * Mark: a diamond aperture layered outward from the play symbol — orange play,
 * neutral charcoal core, and cyan diamond on a dark field. The rotated square
 * is a deliberate nod to the diamond at the centre of the Tvheadend logo; the
 * chevrons around it are not reproduced. The cyan diamond is the complete outer
 * silhouette, without a redundant dark keyline.
 *
 * Every surface derives from {@link #diamond} and {@link #playSymbol}, so raster
 * exports, the monochrome adaptive layer, and the SVG wordmark cannot drift apart.
 */
public final class RenderArtwork {
    // Tvheadend-inspired palette; all mark geometry is original.
    private static final Color CYAN = new Color(0x00, 0xBC, 0xFA);
    private static final Color ORANGE = new Color(0xFA, 0x7F, 0x00);
    private static final Color FIELD = new Color(0x0F, 0x10, 0x14);
    private static final Color CORE = new Color(0x17, 0x17, 0x17);
    private static final Color TEXT = new Color(0xE3, 0xE3, 0xE8);
    private static final Font WORDMARK = loadWordmark();
    private static final FontRenderContext FONT_CONTEXT = new FontRenderContext(null, true, true);

    private static Font loadWordmark() {
        try {
            return Font.createFont(Font.TRUETYPE_FONT, Path.of("artwork/fonts/Outfit-550.ttf").toFile());
        } catch (Exception error) {
            throw new IllegalStateException("Pinned Outfit 550 font is required; no fallback allowed", error);
        }
    }

    /** Adaptive-icon safe zone: 66dp of the 108dp grid. */
    private static final double SAFE_ZONE = 66.0 / 108.0;

    // Mark geometry, normalised to the safe-zone square. The cyan outer diamond
    // fills the safe zone; the core leaves a band about 9% of the diamond span.
    private static final double OUTER_HALF = 0.5;
    private static final double CORE_HALF = 27.0 / 66.0;
    private static final double OUTER_CORNER = 13.0 / 66.0;
    private static final double CORE_CORNER = 10.5 / 66.0;

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
        writeBrandSurfaces();
        writeAdaptiveLayers();
        writePlayStoreIcon();
        writeLegacyIcons();
        writeMonochrome();
        writeStartupResources();
        writePreview();
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return graphics;
    }

    /** The dark neutral ground every surface sits on. */
    private static void paintField(Graphics2D graphics, int width, int height) {
        graphics.setColor(FIELD);
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
     * The cyan diamond ring and play symbol as one monochrome silhouette.
     * Themed icons and the monochrome layer use this.
     */
    private static Shape markSilhouette(double x, double y, double size) {
        Area ink = new Area(diamond(x, y, size, OUTER_HALF, OUTER_CORNER));
        ink.subtract(new Area(diamond(x, y, size, CORE_HALF, CORE_CORNER)));
        ink.add(new Area(playSymbol(x, y, size)));
        return ink;
    }

    // ---------------------------------------------------------------- painting

    /** Draws the mark in a square of {@code size} with origin at ({@code x},{@code y}). */
    private static void drawMark(Graphics2D graphics, double x, double y, double size) {
        graphics.setColor(CYAN);
        graphics.fill(diamond(x, y, size, OUTER_HALF, OUTER_CORNER));
        graphics.setColor(CORE);
        graphics.fill(diamond(x, y, size, CORE_HALF, CORE_CORNER));
        graphics.setColor(ORANGE);
        graphics.fill(playSymbol(x, y, size));
    }

    /** Centres the mark inside a square surface of {@code extent}. */
    private static void drawCentredMark(Graphics2D graphics, double extent, double fraction) {
        double markSize = extent * fraction;
        drawMark(graphics, (extent - markSize) / 2.0, (extent - markSize) / 2.0, markSize);
    }

    private record Ink(Shape shape, Color color) {}

    private static List<Ink> mark(double x, double y, double size) {
        return new ArrayList<>(List.of(
                new Ink(diamond(x, y, size, OUTER_HALF, OUTER_CORNER), CYAN),
                new Ink(diamond(x, y, size, CORE_HALF, CORE_CORNER), CORE),
                new Ink(playSymbol(x, y, size), ORANGE)));
    }

    private static void text(List<Ink> ink, String text, float size, float x, float baseline, Color color) {
        Font font = WORDMARK.deriveFont(size).deriveFont(
                java.util.Map.of(java.awt.font.TextAttribute.KERNING, java.awt.font.TextAttribute.KERNING_ON));
        if (font.canDisplayUpTo(text) != -1) throw new IllegalArgumentException("Missing brand glyph");
        char[] characters = text.toCharArray();
        ink.add(new Ink(font.layoutGlyphVector(FONT_CONTEXT, characters, 0, characters.length,
                Font.LAYOUT_LEFT_TO_RIGHT).getOutline(x, baseline), color));
    }

    private static void writeBrandSurfaces() throws IOException {
        // Accepted sample02 paired stack, in its original 320x180 coordinates.
        List<Ink> banner = mark(26.5859375, 51, 78);
        text(banner, "Tvheadend", 36, 118.1679375f, 83, TEXT);
        text(banner, "Player", 36, 118.1679375f, 122, ORANGE);
        export("tvheadend-player-banner", 320, 180, banner, 1, 2, 4);
        // Android's documented TV banner is 320x180 at xhdpi (160x90dp).
        String[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        double[] scales = {0.5, 0.75, 1, 1.5, 2};
        for (int i = 0; i < densities.length; i++) {
            writePng(render(320, 180, banner, scales[i]),
                    Path.of("app/src/main/res/drawable-" + densities[i] + "/banner.png"));
        }

        // Accepted sample03 marquee. Preserve its optical spacing, including
        // the measured inter-word advance, rather than recomputing a new fit.
        List<Ink> marquee = mark(24.125, 69, 42);
        text(marquee, "Tvheadend", 27, 77.125f, 99, TEXT);
        text(marquee, "Player", 27, 216.58984375f, 99, ORANGE);
        // Three times the reference width; remove only the extra blank field
        // vertically to retain the existing 960x300 family export canvas.
        List<Ink> family = transformed(marquee, 3, 0, -120);
        export("tvheadend-player-logo", 960, 300, family, 1, 2);
        List<Ink> contextual = new ArrayList<>(family);
        text(contextual, "for Android TV", 24, 231.375f, 230, TEXT);
        export("tvheadend-player-android-tv", 960, 300, contextual, 1, 2);
        export("tvheadend-player-symbol", 512, 512, mark(56.32, 56.32, 399.36), 1, 2);

        List<Ink> social = transformed(banner, 3, 160, 50);
        export("github-social-preview", 1280, 640, social, 1);
        writePng(render(320, 180, marquee, 1), Path.of("artifacts/brand-preview/marquee-320x180.png"));
        writePng(render(320, 180, marquee, 4), Path.of("artifacts/brand-preview/marquee-1280x720.png"));
    }

    private static List<Ink> transformed(List<Ink> ink, double scale, double x, double y) {
        AffineTransform transform = AffineTransform.getTranslateInstance(x, y);
        transform.scale(scale, scale);
        return new ArrayList<>(ink.stream()
                .map(item -> new Ink(transform.createTransformedShape(item.shape()), item.color()))
                .toList());
    }

    private static BufferedImage render(int width, int height, List<Ink> ink, double scale) {
        BufferedImage image = new BufferedImage((int) (width * scale), (int) (height * scale), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(image);
        graphics.scale(scale, scale);
        paintField(graphics, width, height);
        for (Ink item : ink) {
            if (!new java.awt.geom.Rectangle2D.Double(0, 0, width, height).contains(item.shape().getBounds2D())) {
                throw new IllegalArgumentException("Artwork is cropped");
            }
            graphics.setColor(item.color());
            graphics.fill(item.shape());
        }
        graphics.dispose();
        return image;
    }

    private static void export(String name, int width, int height, List<Ink> ink, int... scales) throws IOException {
        for (int scale : scales) {
            writePng(render(width, height, ink, scale), Path.of("artwork/" + name + (scale == 1 ? "" : "@" + scale + "x") + ".png"));
        }
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + width
                + "\" height=\"" + height + "\" viewBox=\"0 0 " + width + " " + height + "\">\n"
                + "  <title>Tvheadend Player" + (name.endsWith("android-tv") ? " for Android TV" : "") + "</title>\n"
                + "  <rect width=\"100%\" height=\"100%\" fill=\"#0F1014\"/>\n");
        for (Ink item : ink) {
            svg.append("  <path fill=\"").append(String.format(Locale.ROOT, "#%06X", item.color().getRGB() & 0xFFFFFF))
                    .append("\" d=\"").append(toPathData(item.shape())).append("\"/>\n");
        }
        svg.append("</svg>\n");
        Files.writeString(Path.of("artwork/" + name + ".svg"), svg, StandardCharsets.UTF_8);
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

    private static void writePng(BufferedImage image, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("PNG writer unavailable for " + path);
        }
    }

    private static void writeStartupResources() throws IOException {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="utf-8"?>
                <!-- Original symbol paths, tightly framed for existing in-app startup. -->
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="96dp" android:height="96dp"
                    android:viewportWidth="368" android:viewportHeight="368">
                    <group android:translateX="-72" android:translateY="-72">
                """);
        for (Ink item : mark(56.32, 56.32, 399.36)) {
            xml.append("        <path android:fillColor=\"")
                    .append(String.format(Locale.ROOT, "#%06X", item.color().getRGB() & 0xFFFFFF))
                    .append("\" android:pathData=\"").append(toPathData(item.shape())).append("\"/>\n");
        }
        xml.append("    </group>\n</vector>\n");
        Files.writeString(Path.of("app/src/main/res/drawable/startup_brand_symbol.xml"), xml, StandardCharsets.UTF_8);
        Path font = Path.of("app/src/main/res/font/outfit_550.ttf");
        Files.createDirectories(font.getParent());
        Files.copy(Path.of("artwork/fonts/Outfit-550.ttf"), font, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Path license = Path.of("app/src/main/assets/licenses/Outfit-OFL.txt");
        Files.createDirectories(license.getParent());
        Files.copy(Path.of("artwork/fonts/OFL.txt"), license, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void writePreview() throws IOException {
        StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="en"><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Tvheadend Player — settled brand assets</title>
                <style>
                *{box-sizing:border-box}body{margin:0;background:#0F1014;color:#E3E3E8;font:16px/1.5 system-ui,sans-serif}
                main{max-width:1080px;margin:auto;padding:32px 24px}h1{font-size:28px;margin:0}h2{font-size:19px}
                p{max-width:80ch;color:#bbc0ca}section{margin:32px 0;padding:20px;border:1px solid #34353b;border-radius:12px}
                img{display:block;max-width:100%;height:auto}small{display:block;color:#bbc0ca;margin-top:12px}
                .swatches{display:flex;flex-wrap:wrap;gap:18px}.swatches span{border-top:6px solid var(--c);padding-top:6px}
                </style><main><h1>Tvheadend Player</h1>
                <p>Settled brand assets · original diamond/play geometry · Outfit 550.<br>
                Independent GPLv3 client descended from Preclikos/tvhstream. Not affiliated with or endorsed by Tvheadend.</p>
                <div class="swatches"><span style="--c:#00BCFA">Cyan #00BCFA</span><span style="--c:#FA7F00">Orange #FA7F00</span>
                <span style="--c:#171717">Core #171717</span><span style="--c:#E3E3E8">Off-white #E3E3E8</span></div>
                """);
        String[][] plates = {
                {"Launcher banner · 320 × 180", "tvheadend-player-banner.png", "320"},
                {"Launcher banner · 1280 × 720 source", "tvheadend-player-banner@4x.png", "960"},
                {"Family wordmark · 1920 × 600 source", "tvheadend-player-logo@2x.png", "960"},
                {"Separate contextual lockup", "tvheadend-player-android-tv@2x.png", "960"},
                {"Symbol-only avatar · 1024 × 1024 source", "tvheadend-player-symbol@2x.png", "256"}
        };
        for (String[] plate : plates) {
            html.append("<section><h2>").append(plate[0]).append("</h2><img alt=\"").append(plate[0])
                    .append("\" width=\"").append(plate[2]).append("\" src=\"data:image/png;base64,")
                    .append(Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of("artwork/" + plate[1]))))
                    .append("\"><small>").append(plate[1]).append(" · rendered directly from vector shapes</small></section>");
        }
        html.append("<p>Portable SVGs contain outlined glyphs. Outfit font sources and SIL OFL 1.1 are preserved in artwork/fonts. "
                + "Static exports establish artwork quality only; physical launcher placement, overscan and ten-foot acceptance remain unverified.</p></main></html>");
        Path preview = Path.of("artifacts/brand-preview/index.html");
        Files.createDirectories(preview.getParent());
        Files.writeString(preview, html, StandardCharsets.UTF_8);
    }
}
