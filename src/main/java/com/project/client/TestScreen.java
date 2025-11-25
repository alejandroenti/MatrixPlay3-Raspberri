package com.project.client;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

import java.io.IOException;
import java.io.File;
import java.awt.*;
import java.awt.image.BufferedImage;

import com.piomatter.*;

public class TestScreen {

    static final int WIDTH = 64, HEIGHT = 64;
    static final int ADDR = 5;          // ABCDE (64x64)
    static final int LANES = 2;         // 2 lanes
    static final int BRIGHTNESS = 100;  // 0..255 (software)
    static final int FPS_CAP = 60;      // FPS target
    static final int WAIT_SECONDS = 10; // Countdown duration

    // Animation states
    static final double LINE_SPEED_PX_PER_S   = (HEIGHT - 1); // vertical movement
    static final double CIRCLE_SPEED_PX_PER_S = (WIDTH - 1);  // horizontal movement
    static final int    CIRCLE_RADIUS = 8;

    static String url = "";

    public static void main(String[] args) throws Exception {

        // 0) Open Piomatter (FPS cap handled by UtilsFPS)
        var pm = new PioMatter(WIDTH, HEIGHT, ADDR, LANES, BRIGHTNESS, 0);
        var fb = pm.mapFramebuffer();

        System.out.println("Config: WIDTH=" + WIDTH + ", HEIGHT=" + HEIGHT + ", LANES=" + LANES + ", BRIGHTNESS=" + BRIGHTNESS);

        // 1) Back-buffer with Java2D
        BufferedImage back = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = back.createGraphics();

        // LED-matrix friendly hints
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);


        // FPS helper
        var fps = new UtilsFPS();
        long lastTime = System.nanoTime();
        long startTime = lastTime;

        try (InputStream is = TestScreen.class.getResourceAsStream("/configuration.json")) {
            if (is == null) {
                throw new IOException("Resource not found!");
            }

            // Read the InputStream as a String
            String jsonText = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // Parse JSON
            JSONObject obj = new JSONObject(jsonText);

            // Get the value behind the key "url"
            String jsonUrl = obj.getString("url");
            System.out.println("URL: " + jsonUrl);
            url = jsonUrl;

        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            // Clear to black before starting
            PioMatter.flushBlack(pm, fb, 2, 10);

            while (true) {
                fps.beginFrame();

                // Calculate speed according to FPS
                double dt = fps.getDeltaSeconds();
                if (dt <= 0) dt = 1.0 / FPS_CAP;

                
                // Draw frame background
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, WIDTH, HEIGHT);

                // Tests
                g.setColor(Color.WHITE);
                
                Font customFont = loadCustomFont("/fonts/Doto-Black.ttf", 12f);
                // Font customFont = loadCustomFont("/fonts/0xProtoNerdFont-Bold.ttf", 10f);

                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                ge.registerFont(customFont);
                
                g.setFont(customFont);
                g.drawString(url, 5, 64/2);


                // Show "countdown" (bottom-right)
                double elapsed = (System.nanoTime() - startTime) / 1_000_000_000.0;
                double remaining = WAIT_SECONDS - elapsed;
                if (remaining <= 0) break;

                // FPS overlay (top-left)
                fps.drawOverlay(g, 2, 10);
                // Copy BufferedImage → framebuffer RGB888 with defined brightness
                PioMatter.copyBufferedImageToRGB888(back, fb.data, fb.strideBytes, WIDTH, HEIGHT, BRIGHTNESS);

                // Present frame
                pm.swap();

                // End frame + cap
                fps.endFrameAndCap(FPS_CAP);
            }

        } finally {
            g.dispose();
            // Fade to black at the end
            PioMatter.flushBlack(pm, fb, 3, 15);
            pm.close();
        }

        System.out.println("END animated demo after " + WAIT_SECONDS + " seconds.");
    }

    private static Font loadCustomFont(String path, float size) {
        try (InputStream is = Main.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Font resource not found: " + path);
            }

            return Font.createFont(Font.TRUETYPE_FONT, is).deriveFont(size);
        } catch (FontFormatException | IOException e) {
            e.printStackTrace();
            System.err.println("Falling back to default font.");
            return new Font("SansSerif", Font.PLAIN, (int) size);
        }
    }
}
