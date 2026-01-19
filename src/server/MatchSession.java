package server;

import java.util.List;
import java.util.concurrent.*;

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

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MatchTimer");
        t.setDaemon(true);
        return t;
    });

    private ScheduledFuture<?> timeoutTask;

    public MatchSession(TicTacToeServer server, ScoreManager score, GameStore store, MatchHistoryStore history,
                        ClientHandler x, ClientHandler o, String mode, int timerSec) {
        this.server = server;
        this.score = score;
        this.store = store;
        this.history = history;
        this.x = x;
        this.o = o;
        this.mode = mode;
        this.timerSec = Math.max(3, timerSec);
        this.botMode = "BOT".equalsIgnoreCase(mode) && o == null;
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
            } catch (Exception ignored) {}
        }

        char turn = game.getCurrentPlayer();
        broadcast(Protocol.SRV_TURN + Protocol.SEPARATOR + turn);
        resetTimer(turn);

        if (botMode && turn == 'O') scheduleBotMove();
    }

    public void onChat(ClientHandler from, String msg) {
        String out = Protocol.SRV_CHAT + Protocol.SEPARATOR + from.getPlayerName() + Protocol.SEPARATOR + msg;
        x.sendMessage(out);
        if (o != null) o.sendMessage(out);
    }

    public synchronized void onMove(ClientHandler from, int row, int col) {
        boolean isBot = isBotSentinel(from);

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
    }

    private void finish(char winner, String reason) {
        if (timeoutTask != null) timeoutTask.cancel(false);

        String px = x.getPlayerName();
        String po = botMode ? "BOT" : (o != null ? o.getPlayerName() : "?");

        if (winner == 'D') {
            score.recordDraw(px, po);
        } else if (winner == 'X') {
            score.recordGameResult(px, po);
        } else { // 'O'
            score.recordGameResult(po, px);
        }

        String movesCompact = store.hasSave(px, po) ? "saved" : "";
        history.append(px, po, String.valueOf(winner), movesCompact);

        store.deleteSave(px, po);

        broadcast(Protocol.SRV_GAME_OVER + Protocol.SEPARATOR + winner);

        scheduler.shutdownNow();

        // Remtach Fix oder so
        x.endMatchKeepOpponent();
        if (o != null) o.endMatchKeepOpponent();
    }

    public static boolean isBotSentinel(Object h) {
        return h instanceof ClientHandler.BotSentinel;
    }
}
