package com.project2;

import com.piomatter.PioMatter;
import com.piomatter.UtilsFPS;
import com.piomatter.UtilsImage;
import com.piomatter.UtilsImage.FitMode;
import com.project.client.TestScreen;

import org.json.JSONObject;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

public class Main {

    enum States {
        INIT,
        COUNTDOWN,
        PLAY,
        ENDGAME
    }

    // Matriu
    private static final int WIDTH = 64, HEIGHT = 64;
    private static final int ADDR = 5;          // ABCDE
    private static final int LANES = 2;         // 2 lanes
    private static final int BRIGHTNESS = 200;  // 0..255
    private static final int FPS_CAP = 60;

    public static int player_width = 4;
    public static int player_height = 16;

    // Dibuix
    private static final int TEXT_X = 5;
    // Reservem una franja superior per a l'overlay d'FPS (~10-12px) + marge.
    private static final int RESERVED_TOP = 12;
    private static final int TEXT_TOP_PAD = 2; // separació extra respecte l'overlay

    // Estat missatge
    private enum Mode { NONE, TEXT, IMAGE }
    private volatile Mode mode = Mode.NONE;
    private volatile String  text = null;
    private volatile BufferedImage image = null;
    private volatile long expireAtMs = 0L;

    public int countdownNumber = 10;

    public static String player1Name = "Player1";
    public static String player2Name = "Player2";

    public static String winnerName = "Winner";

    public double player1X = 10;
    public double player2X = 46;
    public double player1Y = 32;
    public double player2Y = 32;

    public double ballX = 5;
    public double ballY = 5;
    public int ballRadius = 2;

    public int[] score = {0, 0};

    private final UtilsWS ws;

    private States state;

    public Color player1Color = new Color(198, 37, 50);
    public Color player2Color = new Color(174, 100, 245);
    public Color ballColor = new Color(245, 188, 100);

    public Main(String serverUri) {
        ws = UtilsWS.getSharedInstance(serverUri);
        state = States.INIT;
        ws.connect();
        ws.onMessage(this::onWsMessage);
    }

    private void onWsMessage(String msg) {
        try {
            System.out.println("[client] Received message: " + msg);
            JSONObject o = new JSONObject(msg);
            String t = o.optString("type", "");
            long ttl = Math.max(1, o.optLong("ttl_ms", 100000L));
            expireAtMs = System.currentTimeMillis() + ttl;

            switch (t) {
                case "salutation" -> {
                    
                    image = null;
                    mode = Mode.TEXT;
                    askConfiguration();
                    // text = o.optString("message", "");
                    // System.out.println("[client] TEXT: " + text);
                    
                }
                case "configuration" -> {
                    // Llegim configuració
                    String jsonUrl = o.optString("url", "");
                    System.out.println("[client] Configuration URL: " + jsonUrl);

                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("url", jsonUrl);

                    // Create a writable directory inside 'target'
                    File dir = new File("target/configuration");
                    if (!dir.exists()) {
                        dir.mkdirs(); // creates directories if not present
                    }

                    File file = new File(dir, "configuration.json");
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(jsonObject.toString(4)); // Pretty-print with indentation
                    }
                    System.out.println("File written to: " + file.getAbsolutePath());

                    text = jsonUrl;

                }
                case "image" -> {
                    String b64 = o.optString("b64", "");
                    if (b64.isEmpty()) { mode = Mode.NONE; return; }
                    try {
                        byte[] data = Base64.getDecoder().decode(b64);
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                        if (img != null) {
                            image = img;
                            text = null;
                            mode = Mode.IMAGE;
                            System.out.println("[client] IMAGE: " + o.optString("name", "(unnamed)"));
                        } else {
                            System.out.println("[client] IMAGE decode failed.");
                            mode = Mode.NONE;
                        }
                    } catch (Exception e) {
                        System.out.println("[client] IMAGE error: " + e.getMessage());
                        mode = Mode.NONE;
                    }
                }
                case "countdown" -> {
                    state = States.COUNTDOWN;
                    countdownNumber = o.getInt("value");
                    player1Name = o.getString("player1Name");
                    player2Name = o.getString("player2Name");
                }

                case "initialPosition" -> {
                    Double[] rawPos1 = new Double[2];
                    rawPos1[0] = Double.valueOf(o.getString("p1").split(" ")[0]);
                    rawPos1[1] = Double.valueOf(o.getString("p1").split(" ")[1]);
                    Double[] rawPos2 = new Double[2];
                    rawPos2[0] = Double.valueOf(o.getString("p2").split(" ")[0]);
                    rawPos2[1] = Double.valueOf(o.getString("p2").split(" ")[1]);

                    Double playerWidth = Double.valueOf(o.getString("playersSize").split(" ")[0]) * 64;
                    Double playerHeight = Double.valueOf(o.getString("playersSize").split(" ")[1]) * 64;
                    player_width = playerWidth.intValue();
                    player_height = playerHeight.intValue();

                    Double ballXRaw = Double.valueOf(o.getString("ball").split(" ")[0]);
                    Double ballYRaw = Double.valueOf(o.getString("ball").split(" ")[1]);
                    Double ballSizeRaw = o.getDouble("ballRadius");
                    ballRadius = (int)(ballSizeRaw * 64);

                    ballX = denormalizePositions(ballXRaw, ballYRaw)[0] - ballRadius/2;
                    ballY = denormalizePositions(ballXRaw, ballYRaw)[1] - ballRadius/2;

                    player1X = denormalizePositions(rawPos1[0], rawPos1[1])[0];
                    player2X = denormalizePositions(rawPos2[0], rawPos2[0])[0];
                    
                    player1Y = denormalizePositions(rawPos1[0], rawPos1[1])[1];
                    player2Y = denormalizePositions(rawPos2[0], rawPos2[1])[1];
                }
                
                case "playerPosition" -> {
                    System.out.println("Player position received");
                    // JSONObject position = obj.getJSONObject("initialPosition");

                    double posxRaw = Double.parseDouble(o.getString("position").split(" ")[0]);
                    double posyRaw = Double.parseDouble(o.getString("position").split(" ")[1]);
                    String player = o.getString("playerName");
                    System.out.println("Player1Name: " + player1Name);
                    System.out.println("Player2Name: " + player2Name);

                    if (player.equals(player1Name)) {
                        // player1X = denormalizePositions(posxRaw, posyRaw)[0];
                        player1Y = denormalizePositions(posxRaw, posyRaw)[1];
                    }
                    else if (player.equals(player2Name)) {
                        // player2X = denormalizePositions(posxRaw, posyRaw)[0];
                        player2Y = denormalizePositions(posxRaw, posyRaw)[1];
                    }
                    
                    
                }

                case "ballPosition" -> {
                    System.out.println("Ball position received");
                    // JSONObject position = obj.getJSONObject("initialPosition");

                    double posxRaw = Double.parseDouble(o.getString("position").split(" ")[0]);
                    double posyRaw = Double.parseDouble(o.getString("position").split(" ")[1]);

                    ballX = denormalizePositions(posxRaw, posyRaw)[0] - ballRadius/2;
                    ballY = denormalizePositions(posxRaw, posyRaw)[1] - ballRadius/2;
                }

                case "goalScored" -> {
                    String player = o.getString("playerName");

                    if (player.equals(player1Name)) {
                        score[0] += 1;
                    }
                    else if (player.equals(player2Name)) {
                        score[1] += 1;
                    }
                }

                case "gameOver" -> {
                    String winner = o.getString("winner");
                    winnerName = winner;
                    state = States.ENDGAME;
                }

                default -> {
                    // ignore
                    System.out.println("[client] Unknown message type: " + t);

                }
            }
        } catch (Exception ignored) {}
    }

    public double[] denormalizePositions(double x, double y) {
        double denormX = x * WIDTH;
        double denormY = y * HEIGHT;
        return new double[] {denormX, denormY};
    }

    public void run() {
        PioMatter pm = null;
        PioMatter.FB fb = null;
        BufferedImage back = null;
        Graphics2D g = null;

        double scrollX = 1;

        final UtilsFPS fps = new UtilsFPS();

        try {
            pm = new PioMatter(WIDTH, HEIGHT, ADDR, LANES, BRIGHTNESS, 0);
            fb = pm.mapFramebuffer();

            back = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            g = back.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            // final Font font = new Font("SansSerif", Font.PLAIN, 12);
            final Font font = loadCustomFont("/fonts/Doto-Black.ttf", 16f);
            // final Font font = loadCustomFont("/fonts/0xProtoNerdFont-Bold.ttf", 16f);

            // Neteja inicial
            PioMatter.flushBlack(pm, fb, 2, 10);

            double timerWinner = 0;
            while (true) {
                fps.beginFrame();

                // Fons negre
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, WIDTH, HEIGHT);

                // Zona de dibuix de text (evitant l'overlay d'FPS)
                int startY = Math.max(0, RESERVED_TOP + TEXT_TOP_PAD);
                int availH = Math.max(0, HEIGHT - startY);
                int availW = Math.max(0, WIDTH - TEXT_X);

                // Pinta segons mode si no ha caducat
                boolean alive = System.currentTimeMillis() < expireAtMs;
                if (alive) {
                    if (mode == Mode.TEXT && text != null) {
                        g.setFont(font);
                        g.setColor(Color.WHITE);
                        FontMetrics fm = g.getFontMetrics();

                        switch (state) {
                            case INIT:
                                g.drawString(text, (int)(WIDTH * scrollX), 16);
                                scrollX -= 0.009;
                                if (scrollX * WIDTH + fm.stringWidth(text) < 0) {
                                    scrollX = 1;
                                }

                                String qrText = "https://matrixplay.ieti.site/grup3_apk";
                                int[][] qrMatrix = QR.generateQR(qrText, 36);

                                int initPosX = (WIDTH / 2) - 19;
                                int initPosY = HEIGHT - 41;

                                for (int i = 0; i < 36; i++) {
                                    for (int j = 0; j < 36; j++) {
                                        Color qrColor = qrMatrix[i][j] % 2 == 0 ? Color.BLACK : Color.WHITE;
                                        g.setColor(qrColor);
                                        g.drawRect(initPosX + j, initPosY + i, 1, 1);
                                    }
                                }

                                break;
                            
                            case COUNTDOWN:
                                String cntdwnStr = String.valueOf(countdownNumber != 0 ? countdownNumber : "GO!");
                                g.drawString(cntdwnStr, WIDTH / 2 - 8, HEIGHT / 2 + 8);
                                if (countdownNumber == 0) {
                                    state = States.PLAY;
                                }
                                break;

                            case PLAY:
                                // Dibuixa jugadors
                                // System.out.println("Playing state");
                                // System.out.println("Player 1 position: (" + player1X + ", " + player1Y + ")");
                                // System.out.println("Player 2 position: (" + player2X + ", " + player2Y + ")");
                                // Draw middle line
                                g.setColor(Color.getHSBColor(Color.RGBtoHSB(30, 30, 30, null)[0],
                                                             Color.RGBtoHSB(30, 30, 30, null)[1],
                                                             Color.RGBtoHSB(30, 30, 30, null)[2]));
                                g.drawLine(WIDTH / 2, 0, WIDTH / 2, HEIGHT);
                                // Draw puntuations
                                g.setFont(font.deriveFont(12f));
                                // Draw players and ball
                                g.setColor(player1Color);
                                g.fillRect((int)player1X, (int)player1Y - player_height/2, player_width, player_height);
                                g.drawString(String.valueOf(score[0]), 64/3, 10);
                                g.setColor(player2Color);
                                g.fillRect((int)player2X - player_width, (int)player2Y - player_height/2, player_width, player_height);
                                g.drawString(String.valueOf(score[1]), 64/2 + 5, 10);
                                g.setColor(ballColor);
                                g.fillOval((int)ballX, (int)ballY, ballRadius, ballRadius);
                                break;

                            case ENDGAME:
                                
                                score[0] = 0;
                                score[1] = 0;
                                g.setFont(font.deriveFont(12f));
                                fm = g.getFontMetrics();
                                g.drawString(winnerName, WIDTH / 2 - fm.stringWidth(winnerName) / 2, HEIGHT / 2 + 8);
                                g.drawString("Wins!", WIDTH / 2 - fm.stringWidth("Wins!") / 2, HEIGHT / 2 + 20);

                                timerWinner += fps.getDeltaSeconds();
                                if (timerWinner >= 5) {
                                    state = States.INIT;
                                    timerWinner = 0;
                                }
                                break;

                        
                            default:
                            //InitView(g, fm, scrollX, text);
                                break;
                        }

                    } else if (mode == Mode.IMAGE && image != null) {
                        // Mostra la imatge amb CONTAIN dins tota la pantalla
                        UtilsImage.drawImageFit(g, image, 0, 0, WIDTH, HEIGHT, FitMode.CONTAIN);
                    }
                } else {
                    // caducat
                    mode = Mode.NONE;
                    text = null;
                    image = null;
                }

                // FPS overlay (queda per sobre)
                // fps.drawOverlay(g, 1, 9);

                // Volcat framebuffer
                PioMatter.copyBufferedImageToRGB888(back, fb.data, fb.strideBytes, WIDTH, HEIGHT, BRIGHTNESS);
                pm.swap();

                // Cap FPS
                fps.endFrameAndCap(FPS_CAP);
            }

        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            if (g != null) g.dispose();
            try { if (pm != null && fb != null) PioMatter.flushBlack(pm, fb, 2, 10); } catch (InterruptedException ignored) {}
            if (pm != null) pm.close();
            ws.forceExit();
        }
    }

    public void InitView(Graphics2D g, FontMetrics fm, double scrollX, String text) {
        System.out.println("[*] Init View entered");
        
    }

    /**
     * Fa word-wrap amb mètriques (FontMetrics) respectant amplada i alçada disponibles.
     * Trunca l'última línia amb ‘…’ si no hi cap tot el text.
     */
    private static List<String> wrapText(String s, FontMetrics fm, int maxW, int maxH) {
        ArrayList<String> out = new ArrayList<>();
        if (s == null || s.isEmpty() || maxW <= 0 || maxH <= 0) return out;

        int lineH = fm.getHeight();
        int maxLines = Math.max(1, maxH / lineH);

        // Split per espais (preserva paraules); també tracta salts de línia explícits
        String[] paragraphs = s.split("\\R"); // \R = qualsevol salt de línia
        for (String para : paragraphs) {
            // Si el paràgraf és buit, afegim línia en blanc si queda espai
            if (para.isEmpty()) {
                if (out.size() < maxLines) out.add("");
                else break;
                continue;
            }

            String[] words = para.split("\\s+");
            StringBuilder line = new StringBuilder();

            for (int i = 0; i < words.length; i++) {
                String w = words[i];
                String candidate = line.length() == 0 ? w : (line + " " + w);
                if (fm.stringWidth(candidate) <= maxW) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    // si la paraula sola ja és massa llarga, trenquem-la amb ellipsi
                    if (line.length() == 0) {
                        out.add(truncateWithEllipsis(w, fm, maxW));
                    } else {
                        out.add(line.toString());
                        // revalorem w a la següent línia
                        i--; // tornem a intentar afegir ‘w’ en una línia nova
                    }
                    line.setLength(0);
                    // si ja no hi ha espai vertical, sortim
                    if (out.size() >= maxLines) break;
                }
                if (out.size() >= maxLines) break;
            }

            if (out.size() >= maxLines) break;
            if (line.length() > 0) {
                out.add(line.toString());
            }

            if (out.size() >= maxLines) break;
        }

        // Si hem excedit l’alçada, trunquem l’última línia amb ‘…’
        if (out.size() > maxLines) {
            while (out.size() > maxLines) out.remove(out.size() - 1);
            String last = out.get(out.size() - 1);
            out.set(out.size() - 1, truncateWithEllipsis(last, fm, maxW));
        } else if (out.size() == maxLines) {
            // potser queda contingut pendent (no podem saber-ho fàcilment); marquem amb ‘…’ si la última ja és molt plena
            // (opcional; si no ho vols, comenta aquesta part)
            // out.set(out.size() - 1, truncateWithEllipsis(out.get(out.size() - 1), fm, maxW));
        }
        
        
        return out;
    }

    /** Trunca una cadena a maxW i hi afegeix ‘…’ si cal. */
    private static String truncateWithEllipsis(String s, FontMetrics fm, int maxW) {
        if (fm.stringWidth(s) <= maxW) return s;
        String ell = "…";
        int ellW = fm.stringWidth(ell);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            int w = fm.stringWidth(sb.toString() + s.charAt(i));
            if (w + ellW > maxW) break;
            sb.append(s.charAt(i));
        }
        sb.append(ell);
        return sb.toString();
    }

    public static void main(String[] args) {
        String filePath = "target/configuration/configuration.json";

        // Read entire file as a String
        String content;
        try {
            content = new String(Files.readAllBytes(Paths.get(filePath)));
            // Parse JSON
            JSONObject json = new JSONObject(content);
            // Get the URL
            String url = json.optString("url", "URL NOT FOUND");

            System.out.println("URL from file: " + url);
            
            String serverURI = (args.length > 0) ? args[0] : url;
            Main app = new Main(serverURI);
            app.run();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        

        
    }

    public void askConfiguration() {
        JSONObject json = new JSONObject();
        json.put("type", "configuration");
        
        ws.safeSend(json.toString());
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
