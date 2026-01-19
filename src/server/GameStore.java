package server;


import java.io.*;
import java.nio.file.*;
import java.util.*;

public class GameStore {
    private final Path dir = Paths.get("saves");

    public GameStore() {
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
    }

    private String key(String a, String b) {
        String p1 = (a == null ? "" : a.trim());
        String p2 = (b == null ? "" : b.trim());
        if (p1.compareToIgnoreCase(p2) <= 0) return p1 + "__" + p2;
        return p2 + "__" + p1;
    }

    private Path file(String a, String b) {
        return dir.resolve(key(a, b) + ".csv");
    }

    public synchronized List<String[]> loadMoves(String a, String b) {
        Path f = file(a, b);
        if (!Files.exists(f)) return Collections.emptyList();

        List<String[]> moves = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(f)) {
            String line;
            while ((line = br.readLine()) != null) {
                // row;col;symbol
                String[] p = line.split(";");
                if (p.length >= 3) moves.add(new String[]{p[0], p[1], p[2]});
            }
        } catch (IOException ignored) {}
        return moves;
    }

    public synchronized void appendMove(String a, String b, int row, int col, char sym) {
        Path f = file(a, b);
        try (PrintWriter pw = new PrintWriter(new FileWriter(f.toFile(), true))) {
            pw.println(row + ";" + col + ";" + sym);
        } catch (IOException ignored) {}
    }

    public synchronized void deleteSave(String a, String b) {
        try { Files.deleteIfExists(file(a, b)); } catch (IOException ignored) {}
    }

    public synchronized boolean hasSave(String a, String b) {
        return Files.exists(file(a, b));
    }
}
