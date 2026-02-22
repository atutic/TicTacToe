package server;

import java.io.*;
import java.net.Socket;

public class ClientHandler extends Thread {

    private final Socket socket;
    private final TicTacToeServer server;

    private PrintWriter out;
    private BufferedReader in;

    private String playerName = "";
    private String mode = "HUMAN";
    private int timerSec = 15;

    // session
    private volatile MatchSession session;
    private volatile char symbol = '?';
    private volatile ClientHandler opponent;

    // rematch
    private boolean rematchOfferReceived = false;

    public ClientHandler(Socket socket, TicTacToeServer server) {
        this.socket = socket;
        this.server = server;

        if (socket != null) {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                sendMessage(Protocol.SRV_MESSAGE + Protocol.SEPARATOR + "Verbunden. Bitte LOGIN;DeinName senden.");
            } catch (IOException e) {
                System.out.println("Init-Fehler: " + e.getMessage());
            }
        }
    }

    // BotSentinel
    public static final class BotSentinel extends ClientHandler {
        public BotSentinel() { super(null, null); }
    }

    public void sendMessage(String msg) {
        if (out != null) out.println(msg);
    }

    public String getPlayerName() {
        return (playerName == null || playerName.isBlank()) ? "Spieler" : playerName;
    }

    public String getMode() { return mode; }
    public int getTimerSec() { return timerSec; }
    public char getSymbol() { return symbol; }

    public void setSession(MatchSession s, char sym, ClientHandler opp) {
        this.session = s;
        this.symbol = sym;
        this.opponent = opp;
        this.rematchOfferReceived = false;
    }

    // komplett reset (Lobby / Disconnect)
    public void clearSession() {
        this.session = null;
        this.symbol = '?';
        this.opponent = null;
        this.rematchOfferReceived = false;
    }

    // Match vorbei aber Rematch soll noch gehen
    public void endMatchKeepOpponent() {
        this.session = null;
        this.rematchOfferReceived = false;
        // symbol + opponent bleiben absichtlich
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;

                try {
                    String[] parts = line.split(Protocol.SEPARATOR, -1);
                    String cmd = parts[0];

                    switch (cmd) {
                        case Protocol.CMD_LOGIN -> handleLogin(parts);
                        case Protocol.CMD_SETTINGS -> handleSettings(parts);

                        case Protocol.CMD_LIST -> server.sendRoomsTo(this);
                        case Protocol.CMD_HOST -> server.hostRoom(this, parts.length >= 2 ? parts[1] : "");
                        case Protocol.CMD_JOIN -> {
                            if (parts.length < 2) sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "JOIN braucht Room-ID.");
                            else server.joinRoom(this, parts[1].trim());
                        }

                        case Protocol.CMD_SPECTATE -> {
                            if (parts.length < 2) sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "SPECTATE braucht Session-ID.");
                            else server.spectateSession(this, parts[1].trim());
                        }

                        case Protocol.CMD_SCORE_REQ -> server.sendScoreboardTo(this);

                        case Protocol.CMD_HISTORY_REQ -> handleHistory(parts);

                        case Protocol.CMD_MOVE -> handleMove(parts);
                        case Protocol.CMD_CHAT -> handleChat(parts);

                        case Protocol.CMD_REMATCH -> handleRematchOffer();
                        case Protocol.CMD_REMATCH_ACCEPT -> handleRematchAccept();
                        case Protocol.CMD_REMATCH_DECLINE -> handleRematchDecline("abgelehnt");

                        case Protocol.CMD_LEAVE -> handleLeaveToLobby();

                        // Tournament commands
                        case Protocol.CMD_HOST_TOURNAMENT -> {
                            String tName = parts.length >= 2 ? parts[1] : "Turnier";
                            int maxP = 4;
                            if (parts.length >= 3) {
                                try { maxP = Integer.parseInt(parts[2].trim()); } catch (NumberFormatException ignored) {}
                            }
                            server.hostTournament(this, tName, maxP);
                        }
                        case Protocol.CMD_JOIN_TOURNAMENT -> {
                            if (parts.length < 2) sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "TJOIN braucht Turnier-ID.");
                            else server.joinTournament(this, parts[1].trim());
                        }
                        case Protocol.CMD_START_TOURNAMENT -> {
                            if (parts.length < 2) sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "TSTART braucht Turnier-ID.");
                            else server.startTournament(this, parts[1].trim());
                        }
                        case Protocol.CMD_LIST_TOURNAMENTS -> server.sendTournamentsTo(this);

                        case Protocol.CMD_QUIT -> { close(); return; }
                        default -> { /* ignore */ }
                    }
                } catch (Exception e) {
                    System.out.println("[ClientHandler] Fehler bei Verarbeitung von '" + line + "': " + e.getMessage());
                    sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Interner Fehler bei der Verarbeitung.");
                }
            }
        } catch (IOException ignored) {
        } finally {
            // disconnect: Spectator oder Spieler?
            if (symbol == 'S' && session != null) {
                // Spectator: einfach entfernen
                session.removeSpectator(this);
            } else if (opponent != null) {
                opponent.sendMessage(Protocol.SRV_OPPONENT_LEFT);
                opponent.handleRematchDecline("Gegner getrennt");
                opponent.clearSession();
            }
            close();
            if (server != null) server.unregister(this);
        }
    }

    private void handleLogin(String[] parts) {
        if (parts.length < 2 || parts[1].isBlank()) {
            playerName = "Spieler";
            sendMessage(Protocol.SRV_MESSAGE + Protocol.SEPARATOR + "Login ungültig. Du bist '" + playerName + "'.");
            return;
        }
        playerName = parts[1].trim();
        sendMessage(Protocol.SRV_MESSAGE + Protocol.SEPARATOR + "Login erfolgreich: " + playerName);
    }

    private void handleSettings(String[] parts) {
        if (parts.length >= 2) mode = parts[1].trim();
        if (parts.length >= 3) {
            try { timerSec = Math.max(3, Integer.parseInt(parts[2].trim())); }
            catch (NumberFormatException ignored) {}
        }
        sendMessage(Protocol.SRV_MESSAGE + Protocol.SEPARATOR + "Settings: " + mode + ", " + timerSec + "s");
    }

    private void handleHistory(String[] parts) {
        // HISTORY;player;from;to
        String player = parts.length >= 2 ? parts[1] : "";
        String from = parts.length >= 3 ? parts[2] : "";
        String to = parts.length >= 4 ? parts[3] : "";
        server.sendHistoryTo(this, player, from, to);
    }

    private void handleMove(String[] parts) {
        if (session == null) {
            sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Du bist in keinem Match.");
            return;
        }
        if (parts.length < 3) return;
        try {
            int r = Integer.parseInt(parts[1]);
            int c = Integer.parseInt(parts[2]);
            session.onMove(this, r, c);
        } catch (NumberFormatException e) {
            sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Ungültiges Koordinatenformat.");
        }
    }

    private void handleChat(String[] parts) {
        if (parts.length < 2) return;
        String msg = parts[1].replace(";", ",").trim();
        if (msg.isEmpty()) return;

        // Session aktiv? -> Chat über Session (inkl. Spectators)
        if (session != null) {
            session.onChat(this, msg);
            return;
        }

        // Session vorbei, aber Opponent noch da -> Chat direkt routen
        if (opponent != null) {
            String chatMsg = Protocol.SRV_CHAT + Protocol.SEPARATOR + getPlayerName() + Protocol.SEPARATOR + msg;
            sendMessage(chatMsg);
            opponent.sendMessage(chatMsg);
        }
    }

    private void handleLeaveToLobby() {
        // Lobby = Rematch ablehnen
        handleRematchDecline("Gegner ist in die Lobby gegangen.");

        // Active session aufräumen (falls Spiel noch läuft)
        if (session != null && !session.isFinished()) {
            server.removeActiveSession(session.getSessionId());
        }

        // Gegner informieren, damit der nur noch "Zurück" hat
        if (opponent != null) {
            opponent.sendMessage(Protocol.SRV_OPPONENT_LEFT);
            opponent.clearSession();
        }

        clearSession();
        sendMessage(Protocol.SRV_MESSAGE + Protocol.SEPARATOR + "Zurück in Lobby.");
        server.sendRoomsTo(this);
    }

    private void handleRematchOffer() {
        if (opponent == null) {
            sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Kein Gegner für Rematch.");
            return;
        }
        opponent.rematchOfferReceived = true;
        opponent.sendMessage(Protocol.SRV_REMATCH_OFFER + Protocol.SEPARATOR + getPlayerName());
        sendMessage(Protocol.SRV_MESSAGE + Protocol.SEPARATOR + "Rematch-Angebot gesendet.");
    }

    private void handleRematchAccept() {
        if (opponent == null) {
            sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Kein Gegner für Rematch.");
            return;
        }
        if (!rematchOfferReceived) {
            sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Kein Rematch-Angebot vorhanden.");
            return;
        }
        server.startRematchSwapped(this, opponent);
    }

    void handleRematchDecline(String reason) {
        if (opponent != null) {
            opponent.sendMessage(Protocol.SRV_REMATCH_DECLINED + Protocol.SEPARATOR + reason);
        }
        rematchOfferReceived = false;
    }

    private void close() {
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        if (out != null) out.close();
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }
}
