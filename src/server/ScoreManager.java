package server;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreManager {

    private static class ScoreEntry {
        String name;
        int wins;
        int losses;
        int draws;
        int elo;
        int tournamentWins;

        ScoreEntry(String name, int wins, int losses, int draws, int elo, int tournamentWins) {
            this.name = name;
            this.wins = wins;
            this.losses = losses;
            this.draws = draws;
            this.elo = elo;
            this.tournamentWins = tournamentWins;
        }
    }

    private static final int K = 32;
    private static final int DEFAULT_ELO = 1000;

    private final Map<String, ScoreEntry> scores = new ConcurrentHashMap<>();
    private final File scoreFile = new File("scores.csv");

    public ScoreManager() {
        loadScores();
    }

    public synchronized void loadScores() {
        if (!scoreFile.exists()) {
            System.out.println("scores.csv nicht gefunden. Wird neu erstellt.");
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(scoreFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(";");
                if (parts.length >= 3) {
                    String name = parts[0];
                    int wins = Integer.parseInt(parts[1]);
                    int losses = Integer.parseInt(parts[2]);
                    int elo = DEFAULT_ELO;
                    int draws = 0;
                    int tournamentWins = 0;
                    if (parts.length >= 4) {
                        // Abwärtskompatibilität:
                        // Altes Format: name;wins;losses;elo
                        // Neues Format: name;wins;losses;draws;elo
                        // Neuestes Format: name;wins;losses;draws;elo;tournamentWins
                        if (parts.length >= 6) {
                            draws = Integer.parseInt(parts[3]);
                            elo = Integer.parseInt(parts[4]);
                            tournamentWins = Integer.parseInt(parts[5]);
                        } else if (parts.length >= 5) {
                            draws = Integer.parseInt(parts[3]);
                            elo = Integer.parseInt(parts[4]);
                        } else {
                            elo = Integer.parseInt(parts[3]);
                        }
                    }
                    scores.put(name, new ScoreEntry(name, wins, losses, draws, elo, tournamentWins));
                }
            }
            System.out.println("Scores erfolgreich geladen.");
        } catch (IOException | NumberFormatException e) {
            System.out.println("[ScoreManager] Fehler beim Laden der Scores: " + e.getMessage());
        }
    }

    public synchronized void saveScores() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(scoreFile))) {
            for (ScoreEntry entry : scores.values()) {
                writer.println(entry.name + ";" + entry.wins + ";" + entry.losses + ";" + entry.draws + ";" + entry.elo + ";" + entry.tournamentWins);
            }
        } catch (IOException e) {
            System.out.println("[ScoreManager] Fehler beim Speichern der Scores: " + e.getMessage());
        }
    }

    // Elo-Berechnung: expected score
    private double expectedScore(int eloA, int eloB) {
        return 1.0 / (1.0 + Math.pow(10.0, (eloB - eloA) / 400.0));
    }

    public synchronized void recordGameResult(String winnerName, String loserName) {
        ScoreEntry winner = scores.computeIfAbsent(winnerName, name -> new ScoreEntry(name, 0, 0, 0, DEFAULT_ELO, 0));
        ScoreEntry loser = scores.computeIfAbsent(loserName, name -> new ScoreEntry(name, 0, 0, 0, DEFAULT_ELO, 0));

        double expWin = expectedScore(winner.elo, loser.elo);
        double expLose = expectedScore(loser.elo, winner.elo);

        winner.elo += (int) Math.round(K * (1.0 - expWin));
        loser.elo  += (int) Math.round(K * (0.0 - expLose));

        winner.wins++;
        loser.losses++;

        saveScores();
        System.out.println("Spielergebnis gespeichert: Gewinner=" + winnerName
                + " (Elo " + winner.elo + "), Verlierer=" + loserName + " (Elo " + loser.elo + ")");
    }

    public synchronized void recordDraw(String player1Name, String player2Name) {
        ScoreEntry p1 = scores.computeIfAbsent(player1Name, name -> new ScoreEntry(name, 0, 0, 0, DEFAULT_ELO, 0));
        ScoreEntry p2 = scores.computeIfAbsent(player2Name, name -> new ScoreEntry(name, 0, 0, 0, DEFAULT_ELO, 0));

        double exp1 = expectedScore(p1.elo, p2.elo);
        double exp2 = expectedScore(p2.elo, p1.elo);

        p1.elo += (int) Math.round(K * (0.5 - exp1));
        p2.elo += (int) Math.round(K * (0.5 - exp2));

        p1.draws++;
        p2.draws++;

        saveScores();
        System.out.println("Unentschieden: " + player1Name + " (Elo " + p1.elo + "), "
                + player2Name + " (Elo " + p2.elo + ")");
    }

    // PROTOCOL PAYLOAD: name|wins|losses|draws|elo|tournamentWins
    public synchronized String getScoreboardPayload() {
        List<ScoreEntry> list = new ArrayList<>(scores.values());
        list.sort(Comparator.comparingInt((ScoreEntry e) -> e.elo).reversed().thenComparing(e -> e.name));

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (ScoreEntry e : list) {
            if (!first) sb.append(",");
            first = false;
            sb.append(e.name).append("|").append(e.wins).append("|").append(e.losses)
                    .append("|").append(e.draws).append("|").append(e.elo).append("|").append(e.tournamentWins);
        }
        return sb.toString();
    }

    public synchronized void recordTournamentWin(String winnerName) {
        ScoreEntry winner = scores.computeIfAbsent(winnerName, name -> new ScoreEntry(name, 0, 0, 0, DEFAULT_ELO, 0));
        winner.tournamentWins++;
        saveScores();
        System.out.println("Turnier-Sieg für: " + winnerName + " (Gesamt: " + winner.tournamentWins + ")");
    }
}
