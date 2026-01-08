package client;

import server.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.function.Consumer;

public class NetworkClient {
    private static NetworkClient instance;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Consumer<String> onMessageReceived;

    private NetworkClient() {}

    public static synchronized NetworkClient getInstance() {
        if (instance == null) {
            instance = new NetworkClient();
        }
        return instance;
    }

    public void connect(String host, int port, String username) throws IOException {
        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Sende sofort die Login-Nachricht
        sendMessage(Protocol.CMD_LOGIN + Protocol.SEPARATOR + username);
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    public void listen(Consumer<String> onMessageReceived) {
        this.onMessageReceived = onMessageReceived;
        new Thread(() -> {
            try {
                String fromServer;
                while ((fromServer = in.readLine()) != null) {
                    if (this.onMessageReceived != null) {
                        this.onMessageReceived.accept(fromServer);
                    }
                }
            } catch (IOException e) {
                // Hier könnte man einen Verbindungsabbruch an die GUI melden
                System.err.println("Verbindung zum Server verloren: " + e.getMessage());
            } finally {
                close();
            }
        }).start();
    }

    public void close() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
