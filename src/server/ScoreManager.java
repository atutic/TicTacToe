package server;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreManager {

    private static class ScoreEntry {
        String name;
        int wins;
        int losses;

        ScoreEntry(String name, int wins, int losses) {
            this.name = name;
            this.wins = wins;
            this.losses = losses;
        }
    }

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
                if (parts.length == 3) {
                    String name = parts[0];
                    int wins = Integer.parseInt(parts[1]);
                    int losses = Integer.parseInt(parts[2]);
                    scores.put(name, new ScoreEntry(name, wins, losses));
                }
            }
            System.out.println("Scores erfolgreich geladen.");
        } catch (IOException | NumberFormatException e) {
            System.err.println("Fehler beim Laden der Scores: " + e.getMessage());
        }
    }

    public synchronized void saveScores() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(scoreFile))) {
            for (ScoreEntry entry : scores.values()) {
                writer.println(entry.name + ";" + entry.wins + ";" + entry.losses);
            }
        } catch (IOException e) {
            System.err.println("Fehler beim Speichern der Scores: " + e.getMessage());
        }
    }

    public synchronized void recordGameResult(String winnerName, String loserName) {
        // Sieger aktualisieren
        ScoreEntry winner = scores.computeIfAbsent(winnerName, name -> new ScoreEntry(name, 0, 0));
        winner.wins++;

        // Verlierer aktualisieren
        ScoreEntry loser = scores.computeIfAbsent(loserName, name -> new ScoreEntry(name, 0, 0));
        loser.losses++;

        saveScores();
        System.out.println("Spielergebnis gespeichert: Gewinner=" + winnerName + ", Verlierer=" + loserName);
    }
    public synchronized void recordDraw(String player1Name, String player2Name) {
        // Bei Unentschieden werden keine Wins/Losses vergeben, aber die Spieler werden ggf. angelegt.
        scores.computeIfAbsent(player1Name, name -> new ScoreEntry(name, 0, 0));
        scores.computeIfAbsent(player2Name, name -> new ScoreEntry(name, 0, 0));
        saveScores();
        System.out.println("Spielergebnis gespeichert: Unentschieden zwischen " + player1Name + " und " + player2Name);
    }
}
