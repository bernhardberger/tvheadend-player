import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Reproducible launcher, banner, and marketing artwork for TVHeadend Player.
 *
 * Mark: cyan TV set (official TVHeadend cyan) with the orange center diamond and
 * a play triangle — reads as a TVHeadend live-TV player at launcher scale.
 */
public final class RenderArtwork {
    // Official Tvheadend palette sampled from upstream logobig.png
    private static final Color TVH_CYAN = new Color(0x00, 0xBC, 0xFA);
    private static final Color TVH_ORANGE = new Color(0xFA, 0x7F, 0x00);

    private static final Color NAVY = new Color(0x08, 0x0F, 0x1E);
    private static final Color SCREEN = new Color(0x0A, 0x1A, 0x2E);
    private static final Color TILE = new Color(0x10, 0x1D, 0x33);
    private static final Color TILE_STROKE = new Color(0x32, 0x4A, 0x6B);
    private static final Color WHITE = new Color(0xF4, 0xF7, 0xFB);
    private static final Color MUTED = new Color(0xB5, 0xC1, 0xD4);

    private RenderArtwork() {}

    public static void main(String[] args) throws IOException {
        writeBanner();
        writeLogo();
        writeSocialPreview();
        writeAdaptiveLayers();
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
        graphics.setPaint(new GradientPaint(0, 0, new Color(0x11, 0x22, 0x40), width, height, NAVY));
        graphics.fillRect(0, 0, width, height);
    }

    /**
     * Draws the app mark in a square of {@code size} with origin at ({@code x},{@code y}).
     * Geometry stays inside ~10% margins so adaptive-icon masks keep the TV readable.
     */
    private static void drawMark(Graphics2D graphics, float x, float y, float size, boolean tile) {
        if (tile) {
            graphics.setColor(TILE);
            graphics.fill(new RoundRectangle2D.Float(x, y, size, size, size * 0.22f, size * 0.22f));
            graphics.setColor(TILE_STROKE);
            graphics.setStroke(new BasicStroke(Math.max(2f, size * 0.018f)));
            graphics.draw(new RoundRectangle2D.Float(
                    x + size * 0.01f,
                    y + size * 0.01f,
                    size * 0.98f,
                    size * 0.98f,
                    size * 0.22f,
                    size * 0.22f));
        }

        float tvLeft = x + size * 0.14f;
        float tvTop = y + size * 0.12f;
        float tvWidth = size * 0.72f;
        float tvHeight = size * 0.52f;
        float tvRadius = size * 0.10f;
        float bezel = size * 0.075f;

        // Solid cyan TV chassis
        graphics.setColor(TVH_CYAN);
        graphics.fill(new RoundRectangle2D.Float(tvLeft, tvTop, tvWidth, tvHeight, tvRadius, tvRadius));

        // Dark screen inset
        graphics.setColor(SCREEN);
        graphics.fill(new RoundRectangle2D.Float(
                tvLeft + bezel,
                tvTop + bezel,
                tvWidth - bezel * 2f,
                tvHeight - bezel * 2f,
                tvRadius * 0.55f,
                tvRadius * 0.55f));

        float cx = tvLeft + tvWidth * 0.5f;
        float cy = tvTop + tvHeight * 0.5f;

        // Official-style orange center diamond (rounded square rotated 45°)
        float diamondRadius = size * 0.155f;
        float diamondCorner = size * 0.06f;
        RoundRectangle2D diamondRect = new RoundRectangle2D.Float(
                cx - diamondRadius,
                cy - diamondRadius,
                diamondRadius * 2f,
                diamondRadius * 2f,
                diamondCorner,
                diamondCorner);
        AffineTransform rotate = AffineTransform.getRotateInstance(Math.PI / 4.0, cx, cy);
        Area diamond = new Area(rotate.createTransformedShape(diamondRect));
        graphics.setColor(TVH_ORANGE);
        graphics.fill(diamond);

        // Play triangle cut through the diamond (screen-colored so it reads on any bg)
        float play = size * 0.115f;
        GeneralPath triangle = new GeneralPath();
        triangle.moveTo(cx - play * 0.30f, cy - play * 0.48f);
        triangle.lineTo(cx - play * 0.30f, cy + play * 0.48f);
        triangle.lineTo(cx + play * 0.56f, cy);
        triangle.closePath();
        graphics.setColor(SCREEN);
        graphics.fill(triangle);

        // Neck
        float neckWidth = size * 0.07f;
        float neckTop = tvTop + tvHeight;
        float neckBottom = y + size * 0.74f;
        graphics.setColor(TVH_CYAN);
        graphics.fill(new RoundRectangle2D.Float(
                cx - neckWidth * 0.5f,
                neckTop - size * 0.01f,
                neckWidth,
                neckBottom - neckTop + size * 0.01f,
                neckWidth * 0.35f,
                neckWidth * 0.35f));

        // Orange base (brand accent + “player” warmth)
        float baseWidth = size * 0.40f;
        float baseHeight = size * 0.075f;
        float baseTop = y + size * 0.74f;
        graphics.setColor(TVH_ORANGE);
        graphics.fill(new RoundRectangle2D.Float(
                cx - baseWidth * 0.5f,
                baseTop,
                baseWidth,
                baseHeight,
                baseHeight,
                baseHeight));
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
        drawMark(graphics, 55, 55, 190, true);
        drawWordmark(graphics, 290, 145, 54, 190);
        graphics.dispose();
        writePng(image, Path.of("artwork/tvheadend-player-logo.png"));
    }

    private static void writeSocialPreview() throws IOException {
        BufferedImage image = new BufferedImage(1280, 640, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(image);
        paintBackground(graphics, 1280, 640);
        drawMark(graphics, 95, 150, 340, true);
        drawWordmark(graphics, 505, 295, 62, 350);
        graphics.setColor(TVH_ORANGE);
        graphics.setFont(new Font("DejaVu Sans", Font.BOLD, 21));
        graphics.drawString("REMOTE-FIRST  /  OPEN SOURCE  /  ANDROID TV", 505, 410);
        graphics.dispose();
        writePng(image, Path.of("artwork/github-social-preview.png"));
    }

    private static void writeAdaptiveLayers() throws IOException {
        BufferedImage background = new BufferedImage(432, 432, BufferedImage.TYPE_INT_RGB);
        Graphics2D backgroundGraphics = graphics(background);
        paintBackground(backgroundGraphics, 432, 432);
        backgroundGraphics.dispose();
        writePng(background, Path.of("app/src/main/res/drawable/ic_launcher_background.png"));

        BufferedImage foreground = new BufferedImage(432, 432, BufferedImage.TYPE_INT_ARGB);
        Graphics2D foregroundGraphics = graphics(foreground);
        // Keep full TV+stand inside the adaptive safe zone (~66% center)
        drawMark(foregroundGraphics, 78, 78, 276, false);
        foregroundGraphics.dispose();
        writePng(foreground, Path.of("app/src/main/res/drawable/ic_launcher_foreground.png"));
    }

    private static void writeLegacyIcons() throws IOException {
        String[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] sizes = {48, 72, 96, 144, 192};
        for (int index = 0; index < densities.length; index++) {
            BufferedImage image = new BufferedImage(sizes[index], sizes[index], BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = graphics(image);
            paintBackground(graphics, sizes[index], sizes[index]);
            drawMark(graphics, sizes[index] * 0.08f, sizes[index] * 0.08f, sizes[index] * 0.84f, false);
            graphics.dispose();
            Path directory = Path.of("app/src/main/res/mipmap-" + densities[index]);
            writePng(image, directory.resolve("ic_launcher.png"));
            writePng(image, directory.resolve("ic_launcher_round.png"));
        }
    }

    private static void writeMonochrome() throws IOException {
        // Viewport 108dp adaptive grid; geometry matches drawMark(18, 18, 72).
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
                        android:pathData="M28.1,26.6h51.8c3.6,0 6.5,2.9 6.5,6.5v30.9c0,3.6 -2.9,6.5 -6.5,6.5H28.1c-3.6,0 -6.5,-2.9 -6.5,-6.5V33.1c0,-3.6 2.9,-6.5 6.5,-6.5z M33.5,32.0h41.0c2.1,0 3.8,1.7 3.8,3.8v22.9c0,2.1 -1.7,3.8 -3.8,3.8H33.5c-2.1,0 -3.8,-1.7 -3.8,-3.8V35.8c0,-2.1 1.7,-3.8 3.8,-3.8z" />
                    <path
                        android:fillColor="#FFFFFFFF"
                        android:fillType="evenOdd"
                        android:pathData="M54,37.0 L65.2,48.2 L54,59.4 L42.8,48.2 Z M50.0,42.7 L50.0,53.7 L60.4,48.2 Z" />
                    <path
                        android:fillColor="#FFFFFFFF"
                        android:pathData="M51.5,70.1h5c0.9,0 1.6,0.7 1.6,1.6v6.2c0,0.9 -0.7,1.6 -1.6,1.6h-5c-0.9,0 -1.6,-0.7 -1.6,-1.6v-6.2c0,-0.9 0.7,-1.6 1.6,-1.6z" />
                    <path
                        android:fillColor="#FFFFFFFF"
                        android:pathData="M39.6,79.3h28.8c2.1,0 3.8,1.7 3.8,3.8v0c0,2.1 -1.7,3.8 -3.8,3.8H39.6c-2.1,0 -3.8,-1.7 -3.8,-3.8v0c0,-2.1 1.7,-3.8 3.8,-3.8z" />
                </vector>
                """;
        Files.writeString(
                Path.of("app/src/main/res/drawable/ic_launcher_monochrome.xml"),
                xml,
                StandardCharsets.UTF_8);
    }

    private static void writeSvg() throws IOException {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="960" height="300" viewBox="0 0 960 300">
                  <title>TVHeadend Player logo</title>
                  <defs>
                    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0" stop-color="#112240"/>
                      <stop offset="1" stop-color="#080f1e"/>
                    </linearGradient>
                  </defs>
                  <rect width="960" height="300" fill="url(#bg)"/>
                  <rect x="55" y="55" width="190" height="190" rx="42" fill="#101d33" stroke="#324a6b" stroke-width="4"/>
                  <!-- TV chassis -->
                  <rect x="81.6" y="77.8" width="136.8" height="98.8" rx="19" fill="#00BCFA"/>
                  <!-- Screen -->
                  <rect x="95.9" y="92.1" width="108.3" height="70.3" rx="10.5" fill="#0A1A2E"/>
                  <!-- Orange diamond -->
                  <g transform="translate(150 127.2) rotate(45)">
                    <rect x="-29.5" y="-29.5" width="58.9" height="58.9" rx="11.4" fill="#FA7F00"/>
                  </g>
                  <!-- Play cutout -->
                  <polygon points="138.5,109.3 138.5,145.1 174.2,127.2" fill="#0A1A2E"/>
                  <!-- Neck -->
                  <rect x="143.4" y="174.7" width="13.3" height="21.9" rx="4" fill="#00BCFA"/>
                  <!-- Base -->
                  <rect x="112" y="195.6" width="76" height="14.3" rx="7.1" fill="#FA7F00"/>
                  <text x="290" y="145" fill="#f4f7fb" font-family="DejaVu Sans, sans-serif" font-size="54" font-weight="700">TVHeadend Player</text>
                  <text x="290" y="190" fill="#b5c1d4" font-family="DejaVu Sans, sans-serif" font-size="18">Live TV client for TVHeadend servers</text>
                </svg>
                """;
        Files.writeString(Path.of("artwork/tvheadend-player-logo.svg"), svg, StandardCharsets.UTF_8);
    }

    private static void writePng(BufferedImage image, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("PNG writer unavailable for " + path);
        }
    }
}
