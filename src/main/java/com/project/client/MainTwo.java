package com.project.client;

import org.json.JSONObject;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.util.Duration;

public class MainTwo extends Application {
    public static UtilsWS wsClient;

    public static String clientName = "desktop_" + (int)(Math.random() * 1000);

    
    public static void main(String[] args) {
        
        launch(args);
        connectToServer();
        
    }    

    @Override
    public void start(Stage stage) throws Exception {
        final int windowWidth = 900;
        final int windowHeight = 600;
        connectToServer();
        
        
    }

    /***** Conexión al servidor (se llama en ViewConfig) *****/
    public static void connectToServer() {


        pauseDuring(100, () -> { // Give time to show connecting message ...
            System.out.println("Connecting to server...");
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
