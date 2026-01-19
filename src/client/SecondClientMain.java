package client;

public class SecondClientMain {

    public static void main(String[] args) {
        System.setProperty("tictactoe.settings.file", "client_settings_2.properties");
        System.setProperty("tictactoe.window.title", "Tic-Tac-Toe Lobby (Client #2)");

        // startet die gleiche JavaFX App wie Client #1
        javafx.application.Application.launch(Main.class, args);
    }
}
