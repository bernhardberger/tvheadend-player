import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class RenderArtwork {
    private static final Color NAVY = new Color(0x08, 0x0F, 0x1E);
    private static final Color TILE = new Color(0x10, 0x1D, 0x33);
    private static final Color WHITE = new Color(0xF4, 0xF7, 0xFB);
    private static final Color MUTED = new Color(0xB5, 0xC1, 0xD4);
    private static final Color CYAN = new Color(0x59, 0xC3, 0xFF);
    private static final Color BLUE = new Color(0x65, 0x7C, 0xFF);
    private static final Color AMBER = new Color(0xFF, 0xB4, 0x54);

    private RenderArtwork() {}

    public static void main(String[] args) throws IOException {
        writeBanner();
        writeLogo();
        writeSocialPreview();
        writeAdaptiveLayers();
        writeLegacyIcons();
        writeSvg();
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return graphics;
    }

    private static void paintBackground(Graphics2D graphics, int width, int height) {
        graphics.setPaint(new GradientPaint(0, 0, new Color(0x11, 0x22, 0x40), width, height, NAVY));
        graphics.fillRect(0, 0, width, height);
    }

    private static void drawMark(Graphics2D graphics, float x, float y, float size, boolean tile) {
        if (tile) {
            graphics.setColor(TILE);
            graphics.fill(new RoundRectangle2D.Float(x, y, size, size, size * 0.22f, size * 0.22f));
            graphics.setColor(new Color(0x32, 0x4A, 0x6B));
            graphics.setStroke(new BasicStroke(Math.max(2f, size * 0.018f)));
            graphics.draw(new RoundRectangle2D.Float(x, y, size, size, size * 0.22f, size * 0.22f));
        }

        float left = x + size * 0.18f;
        float laneHeight = size * 0.075f;
        float corner = laneHeight;
        float[] laneY = {0.25f, 0.40f, 0.55f, 0.70f};
        float[] laneWidth = {0.44f, 0.53f, 0.34f, 0.48f};
        Color[] colors = {WHITE, CYAN, BLUE, WHITE};
        for (int index = 0; index < laneY.length; index++) {
            graphics.setColor(colors[index]);
            graphics.fill(new RoundRectangle2D.Float(
                    left,
                    y + size * laneY[index],
                    size * laneWidth[index],
                    laneHeight,
                    corner,
                    corner));
        }

        graphics.setColor(AMBER);
        graphics.fill(new RoundRectangle2D.Float(
                x + size * 0.74f,
                y + size * 0.20f,
                size * 0.07f,
                size * 0.62f,
                size * 0.07f,
                size * 0.07f));
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
        graphics.setColor(AMBER);
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
        drawMark(foregroundGraphics, 91, 91, 250, false);
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
                  <rect x="89" y="103" width="84" height="14" rx="7" fill="#f4f7fb"/>
                  <rect x="89" y="131" width="101" height="14" rx="7" fill="#59c3ff"/>
                  <rect x="89" y="160" width="65" height="14" rx="7" fill="#657cff"/>
                  <rect x="89" y="188" width="91" height="14" rx="7" fill="#f4f7fb"/>
                  <rect x="196" y="93" width="13" height="118" rx="7" fill="#ffb454"/>
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
