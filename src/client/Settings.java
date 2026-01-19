package client;

import java.io.*;
import java.util.Properties;

public class Settings {

    private static final String FILE_NAME =
            System.getProperty("tictactoe.settings.file", "client_settings.properties");

    public enum Mode { HUMAN, BOT }

    public String host = "localhost";
    public int port = 8088;
    public String username = "";
    public Mode mode = Mode.HUMAN;
    public int timerSec = 15;

    public static Settings load() {
        Settings s = new Settings();
        File f = new File(FILE_NAME);
        if (!f.exists()) return s;

        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            p.load(in);
            s.host = p.getProperty("host", s.host);
            s.port = Integer.parseInt(p.getProperty("port", String.valueOf(s.port)));
            s.username = p.getProperty("username", s.username);
            s.mode = Mode.valueOf(p.getProperty("mode", s.mode.name()));
            s.timerSec = Integer.parseInt(p.getProperty("timerSec", String.valueOf(s.timerSec)));
        } catch (Exception ignored) {}
        if (s.timerSec < 3) s.timerSec = 3;
        return s;
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("host", host);
        p.setProperty("port", String.valueOf(port));
        p.setProperty("username", username);
        p.setProperty("mode", mode.name());
        p.setProperty("timerSec", String.valueOf(timerSec));
        try (FileOutputStream out = new FileOutputStream(FILE_NAME)) {
            p.store(out, "TicTacToe Client Settings");
        } catch (IOException ignored) {}
    }
}
