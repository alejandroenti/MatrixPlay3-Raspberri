package com.project.client;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

import java.io.IOException;
import java.io.File;
import java.awt.*;
import java.awt.image.BufferedImage;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.util.Duration;

import com.piomatter.*;

public class TestScreenTwo  {

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

    public static UtilsWS wsClient;
    public static String clientName = "raspberri_" + (int)(Math.random() * 1000);

    static String url = "";

    
    public static void main(String[] args) throws Exception {
        connectToServer();
        
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
    /***** Conexión al servidor (se llama en ViewConfig) *****/
    public static void connectToServer() {

        System.out.println("Connecting to server...");
        pauseDuring(100, () -> { // Give time to show connecting message ...
            
            // Generar instancia de wsClient con la URI registrada
            UtilsWS.resetSharedInstance(); // Asegura que si falla un intento de conexión (URI incorrecta), luego puede hacer otro intento correcto
            //wsClient = UtilsWS.getSharedInstance("ws://localhost:3000");
            wsClient = UtilsWS.getSharedInstance("wss://matrixplay3.ieti.site:443");

            wsClient.onOpen((response) -> { Platform.runLater(() -> { wsOpen(response); }); });
            wsClient.onMessage((response) -> { Platform.runLater(() -> { wsMessage(response); }); });
            wsClient.onError((response) -> { Platform.runLater(() -> { wsError(response); }); });
    
            wsClient.connect(); // Hay que hacer la conexión después de definir los handler para mensaje y error, sino utiliza el error definido en UtilsWS

            pauseDuring(100, () -> {
                askConfiguration();
                // askCountdown();
            });
            
        });
    }

    public static void log(String message) {
        System.out.println(message);
    }

    public static void askConfiguration() {
        JSONObject json = new JSONObject();
        json.put("type", "configuration");
        wsClient.safeSend(json.toString());
    }

    public static void askCountdown() {
        JSONObject json = new JSONObject();
        json.put("type", "countdown");
        wsClient.safeSend(json.toString());
    }

    /***** Detiene el programa durante X milisegundos, y luego realiza un Runnable *****/
    public static void pauseDuring(long milliseconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(milliseconds));
        pause.setOnFinished(event -> Platform.runLater(action));
        pause.play();
    }

    private static void wsOpen(String response) {
        Platform.runLater(()->{ 
            // Enviar al servidor el nombre del cliente

            JSONObject json = new JSONObject();
            json.put("type", "register");
            json.put("clientName", clientName);
            wsClient.safeSend(json.toString());
        });
    }

    /***** Realiza una acción cuando recibe un mensaje del servidor *****/
    private static void wsMessage(String response) {
        Platform.runLater(()->{ 

            // Si no es un JSON, sólo lo imprime por pantalla
            
            boolean isJson = response.charAt(0) == '{';
            if (!isJson) {
                System.out.println("Not a JSON, This is the message:");
                System.out.println(response);
                return;
            }

            // Convertir respuesta a JSONObject
            JSONObject msgObj = new JSONObject(response);

            // Comprobar tipo de respuesta
            String type = msgObj.getString("type");
            System.out.println("Received message of type: " + type);
            System.out.println("Message content: " + msgObj.toString(2));
            switch (type) {
                
                default:
                    System.out.println("Unknown message type: " + type);
                    break;
            }
        });
    }

    /***** Realiza una acción cuando hay un error de red o protocolo (p.ej. desconexión) *****/
    private static void wsError(String response) {

        System.out.println("Estoy en wsError");
        String connectionRefused = "S’ha refusat la connexió";
        if (response.indexOf(connectionRefused) != -1) {
            System.out.println("El servidor no está disponible. Reintentando conexión en 5 segundos...");
        }
    }

    /***** Cierra el cliente *****/
    public static void closeClient() {
        System.out.println("Cerrando aplicación...");
    
        // Cierra el WebSocket si está abierto
        if (wsClient != null) {
            wsClient.forceExit();
        }

        Platform.exit();
        System.exit(0);
    }

    public static void sendChallenge(String challengedPlayer) {
        // Enviar al servidor el nombre del cliente
        JSONObject json = new JSONObject();
        json.put("type", "challenge");
        json.put("clientName", clientName);
        json.put("challengedClientName", challengedPlayer);
        wsClient.safeSend(json.toString());

        
    }
}
