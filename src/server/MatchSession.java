package server;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

public class MatchSession {

    private final TicTacToeServer server;
    private final ScoreManager score;
    private final GameStore store;
    private final MatchHistoryStore history;

    private final ClientHandler x;
    private final ClientHandler o; // kann null sein bei Bot
    private final boolean botMode;
    private final BotPlayer bot = new BotPlayer();

    private final TicTacToeGame game = new TicTacToeGame();
    private final String mode;
    private final int timerSec;
    private final String sessionId;

    // Spectators
    private final List<ClientHandler> spectators = new CopyOnWriteArrayList<>();

    // Optional callback when match finishes: (session, winnerSymbol)
    private BiConsumer<MatchSession, Character> finishListener;

    private volatile boolean finished = false;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MatchTimer");
        t.setDaemon(true);
        return t;
    });

    private ScheduledFuture<?> timeoutTask;

    public MatchSession(TicTacToeServer server, ScoreManager score, GameStore store, MatchHistoryStore history,
                        ClientHandler x, ClientHandler o, String mode, int timerSec, String sessionId) {
        this.server = server;
        this.score = score;
        this.store = store;
        this.history = history;
        this.x = x;
        this.o = o;
        this.mode = mode;
        this.timerSec = Math.max(3, timerSec);
        this.botMode = "BOT".equalsIgnoreCase(mode) && o == null;
        this.sessionId = sessionId;
    }

    // Legacy-Konstruktor (ohne sessionId) – kompatibel mit altem Code
    public MatchSession(TicTacToeServer server, ScoreManager score, GameStore store, MatchHistoryStore history,
                        ClientHandler x, ClientHandler o, String mode, int timerSec) {
        this(server, score, store, history, x, o, mode, timerSec, "");
    }

    public String getSessionId() { return sessionId; }

    public ClientHandler getPlayerX() { return x; }
    public ClientHandler getPlayerO() { return o; }

    public boolean isFinished() { return finished; }

    public void setFinishListener(BiConsumer<MatchSession, Character> listener) {
        this.finishListener = listener;
    }

    // Spectator hinzufügen: sendet aktuellen Board-State und Turn
    public synchronized void addSpectator(ClientHandler spec) {
        spectators.add(spec);

        String px = x.getPlayerName();
        String po = botMode ? "BOT" : (o != null ? o.getPlayerName() : "?");

        // Spectator bekommt START-Nachricht mit Symbol 'S'
        spec.sendMessage(Protocol.SRV_SPECTATE_START + Protocol.SEPARATOR
                + sessionId + Protocol.SEPARATOR + px + Protocol.SEPARATOR + po);

        // Board-State verzögert senden, damit Client Zeit hat die Scene zu wechseln
        final char[][] snap = game.snapshot();
        final char winner = game.checkWinner();
        final char currentPlayer = game.getCurrentPlayer();

        scheduler.schedule(() -> {
            // Aktuellen Board-State senden
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    if (snap[r][c] != '\0') {
                        spec.sendMessage(Protocol.SRV_VALID_MOVE + Protocol.SEPARATOR
                                + r + Protocol.SEPARATOR + c + Protocol.SEPARATOR + snap[r][c]);
                    }
                }
            }

            // Aktuellen Turn senden
            if (winner != ' ') {
                spec.sendMessage(Protocol.SRV_GAME_OVER + Protocol.SEPARATOR + winner);
            } else {
                spec.sendMessage(Protocol.SRV_TURN + Protocol.SEPARATOR + currentPlayer);
            }

            // Mitspieler informieren
            broadcast(Protocol.SRV_SPECTATOR_JOINED + Protocol.SEPARATOR + spec.getPlayerName());
        }, 500, TimeUnit.MILLISECONDS);
    }

    public void removeSpectator(ClientHandler spec) {
        spectators.remove(spec);
    }

    public void start() {
        String px = x.getPlayerName();
        String po = botMode ? "BOT" : (o != null ? o.getPlayerName() : "?");

        List<String[]> moves = store.loadMoves(px, po);
        for (String[] m : moves) {
            try {
                int r = Integer.parseInt(m[0]);
                int c = Integer.parseInt(m[1]);
                char s = m[2].charAt(0);

                game.makeMove(r, c, s);
                broadcast(Protocol.SRV_VALID_MOVE + Protocol.SEPARATOR + r + Protocol.SEPARATOR + c + Protocol.SEPARATOR + s);
            } catch (Exception e) {
                System.out.println("[MatchSession] Fehler beim Laden eines Zuges: " + e.getMessage());
            }
        }

        char turn = game.getCurrentPlayer();
        broadcast(Protocol.SRV_TURN + Protocol.SEPARATOR + turn);
        resetTimer(turn);

        if (botMode && turn == 'O') scheduleBotMove();
    }

    public void onChat(ClientHandler from, String msg) {
        String out = Protocol.SRV_CHAT + Protocol.SEPARATOR + from.getPlayerName() + Protocol.SEPARATOR + msg;
        broadcast(out);
    }

    public synchronized void onMove(ClientHandler from, int row, int col) {
        boolean isBot = isBotSentinel(from);

        // Spectators dürfen keine Züge machen
        if (!isBot && from != x && from != o) {
            if (from != null) from.sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Zuschauer dürfen nicht ziehen.");
            return;
        }

        if (botMode && !isBot && from != x) return;
        if (game.checkWinner() != ' ') return;

        char sym = isBot ? 'O' : (from == x ? 'X' : 'O');

        if (!game.makeMove(row, col, sym)) {
            if (!isBot && from != null) {
                from.sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Ungültiger Zug.");
            }
            return;
        }

        String px = x.getPlayerName();
        String po = botMode ? "BOT" : (o != null ? o.getPlayerName() : "?");
        store.appendMove(px, po, row, col, sym);

        broadcast(Protocol.SRV_VALID_MOVE + Protocol.SEPARATOR + row + Protocol.SEPARATOR + col + Protocol.SEPARATOR + sym);

        char winner = game.checkWinner();
        if (winner != ' ') {
            finish(winner, "normal");
            return;
        }

        char turn = game.getCurrentPlayer();
        broadcast(Protocol.SRV_TURN + Protocol.SEPARATOR + turn);
        resetTimer(turn);

        if (botMode && turn == 'O') scheduleBotMove();
    }

    private void scheduleBotMove() {
        scheduler.schedule(() -> {
            synchronized (MatchSession.this) {
                if (game.checkWinner() != ' ') return;

                char[][] b = game.snapshot();
                int[] mv = bot.pickMove(b, 'O', 'X');
                if (mv == null) return;

                onMove(new ClientHandler.BotSentinel(), mv[0], mv[1]);
            }
        }, 400, TimeUnit.MILLISECONDS);
    }

    private void resetTimer(char turn) {
        if (timeoutTask != null) timeoutTask.cancel(false);

        timeoutTask = scheduler.schedule(() -> {
            synchronized (MatchSession.this) {
                if (game.checkWinner() != ' ') return;
                char winner = (turn == 'X') ? 'O' : 'X';
                finish(winner, "timeout");
            }
        }, timerSec, TimeUnit.SECONDS);
    }

    private void broadcast(String msg) {
        x.sendMessage(msg);
        if (o != null) o.sendMessage(msg);
        for (ClientHandler s : spectators) s.sendMessage(msg);
    }

    private void finish(char winner, String reason) {
        if (timeoutTask != null) timeoutTask.cancel(false);
        finished = true;

        String px = x.getPlayerName();
        String po = botMode ? "BOT" : (o != null ? o.getPlayerName() : "?");

        if (winner == 'D') {
            score.recordDraw(px, po);
        } else if (winner == 'X') {
            score.recordGameResult(px, po);
        } else { // 'O'
            score.recordGameResult(po, px);
        }

        // Züge für die History zusammenbauen
        List<String[]> savedMoves = store.loadMoves(px, po);
        StringBuilder movesSb = new StringBuilder();
        for (String[] m : savedMoves) {
            if (movesSb.length() > 0) movesSb.append(",");
            movesSb.append(m[0]).append(":").append(m[1]).append(":").append(m[2]);
        }
        history.append(px, po, String.valueOf(winner), movesSb.toString());

        store.deleteSave(px, po);

        broadcast(Protocol.SRV_GAME_OVER + Protocol.SEPARATOR + winner);

        // Spectators aufräumen
        for (ClientHandler s : spectators) s.clearSession();
        spectators.clear();

        // Rematch Fix
        x.endMatchKeepOpponent();
        if (o != null) o.endMatchKeepOpponent();

        // Notify finish listener (für Turniere)
        if (finishListener != null) {
            finishListener.accept(this, winner);
        }

        // Raum automatisch nach 5 Sekunden auflösen
        scheduler.schedule(() -> {
            server.removeActiveSession(sessionId);
        }, 5, TimeUnit.SECONDS);
    }

    public static boolean isBotSentinel(Object h) {
        return h instanceof ClientHandler.BotSentinel;
    }
}
