package client;

import server.Protocol;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class NetworkClient {
    private static NetworkClient instance;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean listening = false;

    private String username = "";
    private Settings.Mode mode = Settings.Mode.HUMAN;
    private int timerSec = 15;

    private NetworkClient() {}

    public static synchronized NetworkClient getInstance() {
        if (instance == null) instance = new NetworkClient();
        return instance;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void connect(String host, int port) throws IOException {
        if (isConnected()) return;

        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        startListenerIfNeeded();
    }

    public void setIdentity(String username, Settings.Mode mode, int timerSec) {
        this.username = username == null ? "" : username.trim();
        this.mode = mode == null ? Settings.Mode.HUMAN : mode;
        this.timerSec = Math.max(3, timerSec);
    }

    public void sendLoginAndSettings() {
        sendMessage(Protocol.CMD_LOGIN + Protocol.SEPARATOR + username);
        sendMessage(Protocol.CMD_SETTINGS + Protocol.SEPARATOR + mode.name() + Protocol.SEPARATOR + timerSec);
    }

    public void addListener(Consumer<String> listener) {
        if (listener != null) listeners.add(listener);
    }

    public void clearListeners() {
        listeners.clear();
    }

    public void sendMessage(String message) {
        if (out != null && message != null) {
            System.out.println("[CLIENT->SERVER] " + message);
            out.println(message);
        }
    }

    private void startListenerIfNeeded() {
        if (listening) return;
        listening = true;

        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("[SERVER->CLIENT] " + line);
                    for (Consumer<String> l : listeners) l.accept(line);
                }
            } catch (IOException ignored) {
            } finally {
                close();
            }
        }, "NetworkClient-Listener");

        t.setDaemon(true);
        t.start();
    }

    public void close() {
        listening = false;
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        if (out != null) out.close();
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        socket = null;
        out = null;
        in = null;
    }
}
