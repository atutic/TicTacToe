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

    // Aktive Sessions für Spectator-Zugriff
    private final Map<String, MatchSession> activeSessions = new ConcurrentHashMap<>();

    // Turniere
    private final Map<String, Tournament> tournaments = new ConcurrentHashMap<>();
    private final AtomicInteger tournamentSeq = new AtomicInteger(1);

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
            System.out.println("[Server] Serverfehler: " + e.getMessage());
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
        // Offene Räume (wartend)
        for (Room r : rooms.values()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(r.id).append("|")
                    .append(r.name).append("|")
                    .append(safe(r.host.getPlayerName())).append("|")
                    .append(r.mode).append("|")
                    .append(r.timerSec).append("|")
                    .append("WAITING");
        }
        // Laufende Sessions (spectatable)
        for (Map.Entry<String, MatchSession> e : activeSessions.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            MatchSession s = e.getValue();
            String px = s.getPlayerX().getPlayerName();
            ClientHandler po = s.getPlayerO();
            String poName = po != null ? po.getPlayerName() : "BOT";
            sb.append(e.getKey()).append("|")
                    .append(px + " vs " + poName).append("|")
                    .append(px).append("|")
                    .append("HUMAN").append("|")
                    .append("0").append("|")
                    .append("INGAME");
        }
        return sb.toString();
    }

    public void hostRoom(ClientHandler host, String roomName) {
        // Prüfen ob der Spieler bereits einen Raum hostet
        for (Room existing : rooms.values()) {
            if (existing.host == host) {
                host.sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Du hostest bereits einen Raum.");
                return;
            }
        }

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

        // BOT: sofort starten gegen Bot weil eh kein zweiter notwendig
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

    // Spectator
    public void spectateSession(ClientHandler spectator, String sessionId) {
        MatchSession session = activeSessions.get(sessionId);
        if (session == null) {
            spectator.sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Kein laufendes Spiel mit dieser ID gefunden.");
            return;
        }
        spectator.setSession(session, 'S', null);
        session.addSpectator(spectator);
    }

    public void startMatch(ClientHandler pX, ClientHandler pO, String mode, int timerSec) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);

        pX.sendMessage(Protocol.SRV_START + Protocol.SEPARATOR + sessionId + Protocol.SEPARATOR + "X" +
                Protocol.SEPARATOR + safe(pO.getPlayerName()) + Protocol.SEPARATOR + mode + Protocol.SEPARATOR + timerSec);

        pO.sendMessage(Protocol.SRV_START + Protocol.SEPARATOR + sessionId + Protocol.SEPARATOR + "O" +
                Protocol.SEPARATOR + safe(pX.getPlayerName()) + Protocol.SEPARATOR + mode + Protocol.SEPARATOR + timerSec);

        pX.sendMessage(Protocol.SRV_WELCOME + Protocol.SEPARATOR + "X");
        pO.sendMessage(Protocol.SRV_WELCOME + Protocol.SEPARATOR + "O");

        MatchSession session = new MatchSession(this, scoreManager, gameStore, historyStore, pX, pO, mode, timerSec, sessionId);
        pX.setSession(session, 'X', pO);
        pO.setSession(session, 'O', pX);

        activeSessions.put(sessionId, session);
        broadcastRooms(); // Room-Liste aktualisieren (INGAME Entry hinzufügen)

        session.start();
    }

    public void startBotMatch(ClientHandler human, int timerSec) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);

        human.sendMessage(Protocol.SRV_START + Protocol.SEPARATOR + sessionId + Protocol.SEPARATOR + "X" +
                Protocol.SEPARATOR + "BOT" + Protocol.SEPARATOR + "BOT" + Protocol.SEPARATOR + timerSec);
        human.sendMessage(Protocol.SRV_WELCOME + Protocol.SEPARATOR + "X");

        MatchSession session = new MatchSession(this, scoreManager, gameStore, historyStore, human, null, "BOT", timerSec, sessionId);
        human.setSession(session, 'X', null);

        activeSessions.put(sessionId, session);
        broadcastRooms();

        session.start();
    }

    // Rematch sides swapped (old O starts)
    public void startRematchSwapped(ClientHandler a, ClientHandler b) {
        if (a == null || b == null) return;
        ClientHandler oldX = (a.getSymbol() == 'X') ? a : b;
        ClientHandler oldO = (oldX == a) ? b : a;
        startMatch(oldO, oldX, oldX.getMode(), oldX.getTimerSec());
    }

    public void removeActiveSession(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            activeSessions.remove(sessionId);
            broadcastRooms();
        }
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

    // ---- Tournament ----
    public void hostTournament(ClientHandler host, String name, int maxPlayers) {
        String id = "T" + tournamentSeq.getAndIncrement();
        Tournament t = new Tournament(id, name, host, maxPlayers, this);
        tournaments.put(id, t);

        host.sendMessage(Protocol.SRV_TOURNAMENT_HOSTED + Protocol.SEPARATOR + id);
        host.sendMessage(Protocol.SRV_TOURNAMENT_MSG + Protocol.SEPARATOR + "Turnier '" + name + "' erstellt (ID " + id + "). Warte auf Spieler...");
        broadcastTournaments();
    }

    public void joinTournament(ClientHandler guest, String tournamentId) {
        Tournament t = tournaments.get(tournamentId);
        if (t == null) {
            guest.sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Turnier nicht gefunden.");
            return;
        }
        t.addPlayer(guest);
        broadcastTournaments();
    }

    public void startTournament(ClientHandler requester, String tournamentId) {
        Tournament t = tournaments.get(tournamentId);
        if (t == null) {
            requester.sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Turnier nicht gefunden.");
            return;
        }
        if (t.getHost() != requester) {
            requester.sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Nur der Host kann das Turnier starten.");
            return;
        }
        t.start();
    }

    public void sendTournamentsTo(ClientHandler who) {
        who.sendMessage(Protocol.SRV_TOURNAMENTS + Protocol.SEPARATOR + buildTournamentsPayload());
    }

    public void broadcastTournaments() {
        String msg = Protocol.SRV_TOURNAMENTS + Protocol.SEPARATOR + buildTournamentsPayload();
        for (ClientHandler c : clients) c.sendMessage(msg);
    }

    private String buildTournamentsPayload() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Tournament t : tournaments.values()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(t.getId()).append("|")
                    .append(t.getName()).append("|")
                    .append(safe(t.getHost().getPlayerName())).append("|")
                    .append(t.getMaxPlayers()).append("|")
                    .append(t.getCurrentPlayerCount()).append("|")
                    .append(t.isStarted() ? "RUNNING" : "WAITING");
        }
        return sb.toString();
    }

    public void removeTournament(String id) {
        tournaments.remove(id);
        broadcastTournaments();
    }

    public ScoreManager getScoreManager() { return scoreManager; }
    public GameStore getGameStore() { return gameStore; }
    public MatchHistoryStore getHistoryStore() { return historyStore; }

    public void unregister(ClientHandler c) {
        clients.remove(c);
        rooms.values().removeIf(r -> r.host == c);
        // Aus aktiven Turnieren entfernen
        for (Tournament t : tournaments.values()) {
            t.removePlayer(c);
        }
        broadcastRooms();
        broadcastTournaments();
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "?" : s.trim();
    }
}
