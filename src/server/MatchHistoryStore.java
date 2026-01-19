package server;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MatchHistoryStore {

    private final File file = new File("match_history.csv");
    private final DateTimeFormatter tsFmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public synchronized void append(String playerX, String playerO, String winner, String movesCompact) {
        String ts = LocalDateTime.now().format(tsFmt);
        try (PrintWriter pw = new PrintWriter(new FileWriter(file, true))) {
            // timestamp;X;O;winner;moves
            pw.println(escape(ts) + ";" + escape(playerX) + ";" + escape(playerO) + ";" + escape(winner) + ";" + escape(movesCompact));
        } catch (IOException ignored) {}
    }

    public synchronized List<String> query(String playerFilter, String from, String to) {
        if (!file.exists()) return Collections.emptyList();

        LocalDate fromD = parseDate(from);
        LocalDate toD = parseDate(to);

        String pf = (playerFilter == null) ? "" : playerFilter.trim().toLowerCase(Locale.ROOT);

        List<String> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(";", -1);
                if (p.length < 5) continue;

                String ts = unescape(p[0]);
                String x = unescape(p[1]);
                String o = unescape(p[2]);

                if (!pf.isEmpty()) {
                    if (!(x.toLowerCase(Locale.ROOT).contains(pf) || o.toLowerCase(Locale.ROOT).contains(pf))) continue;
                }

                LocalDate d = parseDateFromTs(ts);
                if (fromD != null && d != null && d.isBefore(fromD)) continue;
                if (toD != null && d != null && d.isAfter(toD)) continue;

                out.add(line);
            }
        } catch (IOException ignored) {}
        return out;
    }

    private LocalDate parseDate(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            return LocalDate.parse(s.trim());
        } catch (Exception e) { return null; }
    }

    private LocalDate parseDateFromTs(String ts) {
        try {
            return LocalDateTime.parse(ts, tsFmt).toLocalDate();
        } catch (Exception e) { return null; }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\n", " ").replace("\r", " ");
    }
    private String unescape(String s) {
        return s == null ? "" : s;
    }
}
