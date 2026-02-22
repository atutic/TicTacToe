package server;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * K.O.-Turnier: Verwaltet Spieler, erzeugt Runden-Matches und leitet Gewinner weiter.
 * Unterstützt 2, 4 und 8 Spieler (wird auf nächste Zweierpotenz aufgerundet).
 */
public class Tournament {

    private final String id;
    private final String name;
    private final ClientHandler host;
    private final int maxPlayers;
    private final TicTacToeServer server;

    private final List<ClientHandler> players = new CopyOnWriteArrayList<>();
    private final List<ClientHandler> eliminatedPlayers = new CopyOnWriteArrayList<>();
    private boolean started = false;

    // Turnier-Baum
    private int currentRound = 0;
    private List<ClientHandler> currentRoundPlayers = new ArrayList<>();
    private final List<ClientHandler> roundWinners = Collections.synchronizedList(new ArrayList<>());
    private int matchesInRound = 0;
    private int matchesCompleted = 0;
    private final List<MatchSession> currentRoundSessions = new CopyOnWriteArrayList<>();

    public Tournament(String id, String name, ClientHandler host, int maxPlayers, TicTacToeServer server) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.maxPlayers = normalizeMax(maxPlayers);
        this.server = server;

        // Host ist automatisch Teilnehmer
        players.add(host);
    }

    private int normalizeMax(int n) {
        if (n <= 2) return 2;
        if (n <= 4) return 4;
        return 8;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public ClientHandler getHost() { return host; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getCurrentPlayerCount() { return players.size(); }
    public boolean isStarted() { return started; }

    public void addPlayer(ClientHandler player) {
        if (started) {
            player.sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Turnier läuft bereits.");
            return;
        }
        if (players.size() >= maxPlayers) {
            player.sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Turnier ist voll.");
            return;
        }
        if (players.contains(player)) {
            player.sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Du bist bereits im Turnier.");
            return;
        }

        players.add(player);
        broadcastToPlayers(Protocol.SRV_TOURNAMENT_MSG + Protocol.SEPARATOR
                + player.getPlayerName() + " ist dem Turnier beigetreten. ("
                + players.size() + "/" + maxPlayers + ")");
    }

    public void removePlayer(ClientHandler player) {
        players.remove(player);
    }

    public void start() {
        if (started) {
            host.sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Turnier läuft bereits.");
            return;
        }
        if (players.size() < maxPlayers) {
            host.sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR
                    + "Turnier benötigt " + maxPlayers + " Spieler (aktuell: " + players.size() + ").");
            return;
        }

        started = true;
        broadcastToPlayers(Protocol.SRV_TOURNAMENT_STARTED + Protocol.SEPARATOR + id);

        // Shuffle für faire Paarungen
        currentRoundPlayers = new ArrayList<>(players);
        Collections.shuffle(currentRoundPlayers);

        // Falls ungerade Anzahl -> letzter bekommt ein Freilos
        if (currentRoundPlayers.size() % 2 != 0) {
            ClientHandler bye = currentRoundPlayers.remove(currentRoundPlayers.size() - 1);
            roundWinners.add(bye);
            bye.sendMessage(Protocol.SRV_TOURNAMENT_MSG + Protocol.SEPARATOR + "Du hast ein Freilos in dieser Runde!");
        }

        currentRound = 1;
        startNextRoundMatches();
    }

    private void startNextRoundMatches() {
        matchesInRound = currentRoundPlayers.size() / 2;
        matchesCompleted = 0;
        roundWinners.clear();
        currentRoundSessions.clear();

        // Wenn Freilos-Gewinner existiert, den hinzufügen
        // (wird schon vor dem Aufruf in roundWinners eingefügt)

        String roundName = getRoundName();
        broadcastToPlayers(Protocol.SRV_TOURNAMENT_MSG + Protocol.SEPARATOR
                + "=== " + roundName + " ===");

        for (int i = 0; i < currentRoundPlayers.size(); i += 2) {
            ClientHandler pX = currentRoundPlayers.get(i);
            ClientHandler pO = currentRoundPlayers.get(i + 1);

            int matchIdx = i / 2;

            broadcastToPlayers(Protocol.SRV_TOURNAMENT_MATCH + Protocol.SEPARATOR
                    + currentRound + Protocol.SEPARATOR + matchIdx + Protocol.SEPARATOR
                    + pX.getPlayerName() + Protocol.SEPARATOR + pO.getPlayerName());

            // Match starten mit finishListener
            String sessionId = UUID.randomUUID().toString().substring(0, 8);

            MatchSession session = new MatchSession(
                    server, server.getScoreManager(), server.getGameStore(), server.getHistoryStore(),
                    pX, pO, "HUMAN", 15, sessionId
            );
            currentRoundSessions.add(session);

            pX.sendMessage(Protocol.SRV_START + Protocol.SEPARATOR + sessionId + Protocol.SEPARATOR + "X" +
                    Protocol.SEPARATOR + pO.getPlayerName() + Protocol.SEPARATOR + "HUMAN" + Protocol.SEPARATOR + 15);
            pO.sendMessage(Protocol.SRV_START + Protocol.SEPARATOR + sessionId + Protocol.SEPARATOR + "O" +
                    Protocol.SEPARATOR + pX.getPlayerName() + Protocol.SEPARATOR + "HUMAN" + Protocol.SEPARATOR + 15);

            pX.sendMessage(Protocol.SRV_WELCOME + Protocol.SEPARATOR + "X");
            pO.sendMessage(Protocol.SRV_WELCOME + Protocol.SEPARATOR + "O");

            pX.setSession(session, 'X', pO);
            pO.setSession(session, 'O', pX);

            final int mIdx = matchIdx;
            session.setFinishListener((s, winnerChar) -> onMatchFinished(s, winnerChar, mIdx));

            session.start();
        }

        // Eliminierte Spieler als Zuschauer in das erste Match der Runde setzen
        if (!currentRoundSessions.isEmpty() && !eliminatedPlayers.isEmpty()) {
            MatchSession firstSession = currentRoundSessions.get(0);
            for (ClientHandler elim : eliminatedPlayers) {
                // Spectate-Start Nachricht senden
                elim.sendMessage(Protocol.SRV_SPECTATE_START + Protocol.SEPARATOR
                        + firstSession.getSessionId() + Protocol.SEPARATOR
                        + firstSession.getPlayerX().getPlayerName() + Protocol.SEPARATOR
                        + firstSession.getPlayerO().getPlayerName());
                firstSession.addSpectator(elim);
            }
        }
    }

    private synchronized void onMatchFinished(MatchSession session, char winnerSymbol, int matchIndex) {
        ClientHandler winner;
        ClientHandler loser;
        if (winnerSymbol == 'X') {
            winner = session.getPlayerX();
            loser = session.getPlayerO();
        } else if (winnerSymbol == 'O') {
            winner = session.getPlayerO();
            loser = session.getPlayerX();
        } else {
            // Unentschieden: X bekommt den Vorteil weiter
            winner = session.getPlayerX();
            loser = session.getPlayerO();
        }

        roundWinners.add(winner);
        eliminatedPlayers.add(loser);
        matchesCompleted++;

        broadcastToPlayers(Protocol.SRV_TOURNAMENT_RESULT + Protocol.SEPARATOR
                + currentRound + Protocol.SEPARATOR + matchIndex + Protocol.SEPARATOR + winner.getPlayerName());

        if (matchesCompleted >= matchesInRound) {
            // Runde beendet
            if (roundWinners.size() == 1) {
                // Turnier-Sieger! -> Turnier-Sieg im Scoreboard vermerken
                server.getScoreManager().recordTournamentWin(winner.getPlayerName());
                broadcastToPlayers(Protocol.SRV_TOURNAMENT_OVER + Protocol.SEPARATOR + winner.getPlayerName());
                server.removeTournament(id);
            } else {
                // Nächste Runde
                currentRound++;
                currentRoundPlayers = new ArrayList<>(roundWinners);

                // Freilos bei ungerader Anzahl
                if (currentRoundPlayers.size() % 2 != 0) {
                    ClientHandler bye = currentRoundPlayers.remove(currentRoundPlayers.size() - 1);
                    roundWinners.clear();
                    roundWinners.add(bye);
                    bye.sendMessage(Protocol.SRV_TOURNAMENT_MSG + Protocol.SEPARATOR + "Freilos in Runde " + currentRound + "!");
                } else {
                    roundWinners.clear();
                }

                // Kleine Pause bevor die nächste Runde startet
                new Thread(() -> {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    startNextRoundMatches();
                }).start();
            }
        }
    }

    private String getRoundName() {
        int totalPlayers = currentRoundPlayers.size() + roundWinners.size();
        if (totalPlayers <= 2) return "Finale";
        if (totalPlayers <= 4) return "Halbfinale";
        if (totalPlayers <= 8) return "Viertelfinale";
        return "Runde " + currentRound;
    }

    private void broadcastToPlayers(String msg) {
        for (ClientHandler p : players) {
            p.sendMessage(msg);
        }
    }
}
