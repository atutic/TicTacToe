package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TicTacToeServer {

    private final int port = 8088;

    private final ScoreManager scoreManager = new ScoreManager();
    private final GameStore gameStore = new GameStore();
    private final MatchHistoryStore historyStore = new MatchHistoryStore();

    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final AtomicInteger roomSeq = new AtomicInteger(100);

    public static void main(String[] args) {
        new TicTacToeServer().run();
    }

    private void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server gestartet. Warte auf Spieler auf Port " + port + "...");

            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                clients.add(handler);
                handler.start();
            }
        } catch (IOException e) {
            System.err.println("Serverfehler: " + e.getMessage());
        }
    }

    // Rooms
    public void sendRoomsTo(ClientHandler who) {
        who.sendMessage(Protocol.SRV_ROOMS + Protocol.SEPARATOR + buildRoomsPayload());
    }

    public void broadcastRooms() {
        String msg = Protocol.SRV_ROOMS + Protocol.SEPARATOR + buildRoomsPayload();
        for (ClientHandler c : clients) c.sendMessage(msg);
    }

    private String buildRoomsPayload() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Room r : rooms.values()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(r.id).append("|")
                    .append(r.name).append("|")
                    .append(safe(r.host.getPlayerName())).append("|")
                    .append(r.mode).append("|")
                    .append(r.timerSec);
        }
        return sb.toString();
    }

    public void hostRoom(ClientHandler host, String roomName) {
        String id = String.valueOf(roomSeq.getAndIncrement());
        Room r = new Room();
        r.id = id;
        r.name = (roomName == null || roomName.isBlank()) ? ("Room-" + id) : roomName.trim();
        r.host = host;
        r.mode = host.getMode();
        r.timerSec = host.getTimerSec();

        rooms.put(id, r);

        host.sendMessage(Protocol.SRV_HOSTED + Protocol.SEPARATOR + id);
        host.sendMessage(Protocol.SRV_MESSAGE + Protocol.SEPARATOR + "Room erstellt: " + r.name + " (ID " + id + ")");
        broadcastRooms();

        // BOT: sofort starten gegen Bot (kein 2. Client nötig)
        if ("BOT".equalsIgnoreCase(r.mode)) {
            rooms.remove(id);
            broadcastRooms();
            startBotMatch(host, r.timerSec);
        }
    }

    public void joinRoom(ClientHandler guest, String roomId) {
        Room r = rooms.get(roomId);
        if (r == null) {
            guest.sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Room nicht gefunden.");
            return;
        }
        if (r.host == guest) {
            guest.sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Du kannst deinem eigenen Room nicht joinen.");
            return;
        }

        rooms.remove(roomId);
        broadcastRooms();

        startMatch(r.host, guest, r.mode, r.timerSec);
    }

    public void startMatch(ClientHandler pX, ClientHandler pO, String mode, int timerSec) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);

        pX.sendMessage(Protocol.SRV_START + Protocol.SEPARATOR + sessionId + Protocol.SEPARATOR + "X" +
                Protocol.SEPARATOR + safe(pO.getPlayerName()) + Protocol.SEPARATOR + mode + Protocol.SEPARATOR + timerSec);

        pO.sendMessage(Protocol.SRV_START + Protocol.SEPARATOR + sessionId + Protocol.SEPARATOR + "O" +
                Protocol.SEPARATOR + safe(pX.getPlayerName()) + Protocol.SEPARATOR + mode + Protocol.SEPARATOR + timerSec);

        pX.sendMessage(Protocol.SRV_WELCOME + Protocol.SEPARATOR + "X");
        pO.sendMessage(Protocol.SRV_WELCOME + Protocol.SEPARATOR + "O");

        MatchSession session = new MatchSession(this, scoreManager, gameStore, historyStore, pX, pO, mode, timerSec);
        pX.setSession(session, 'X', pO);
        pO.setSession(session, 'O', pX);

        session.start();
    }

    public void startBotMatch(ClientHandler human, int timerSec) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);

        human.sendMessage(Protocol.SRV_START + Protocol.SEPARATOR + sessionId + Protocol.SEPARATOR + "X" +
                Protocol.SEPARATOR + "BOT" + Protocol.SEPARATOR + "BOT" + Protocol.SEPARATOR + timerSec);
        human.sendMessage(Protocol.SRV_WELCOME + Protocol.SEPARATOR + "X");

        MatchSession session = new MatchSession(this, scoreManager, gameStore, historyStore, human, null, "BOT", timerSec);
        human.setSession(session, 'X', null);

        session.start();
    }

    // Rematch sides swapped (old O starts)
    public void startRematchSwapped(ClientHandler a, ClientHandler b) {
        if (a == null || b == null) return;
        ClientHandler oldX = (a.getSymbol() == 'X') ? a : b;
        ClientHandler oldO = (oldX == a) ? b : a;
        startMatch(oldO, oldX, oldX.getMode(), oldX.getTimerSec());
    }

    public void sendScoreboardTo(ClientHandler who) {
        who.sendMessage(Protocol.SRV_SCOREBOARD + Protocol.SEPARATOR + scoreManager.getScoreboardPayload());
    }

    public void sendHistoryTo(ClientHandler who, String playerFilter, String from, String to) {
        for (String line : historyStore.query(playerFilter, from, to)) {
            who.sendMessage(Protocol.SRV_HISTORY_LINE + Protocol.SEPARATOR + line);
        }
        who.sendMessage(Protocol.SRV_HISTORY_END);
    }

    public void unregister(ClientHandler c) {
        clients.remove(c);
        rooms.values().removeIf(r -> r.host == c);
        broadcastRooms();
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "?" : s.trim();
    }
}
