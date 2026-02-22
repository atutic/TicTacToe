package client;

public class ThirdClientMain {

    public static void main(String[] args) {
        System.setProperty("tictactoe.settings.file", "client_settings_3.properties");
        System.setProperty("tictactoe.window.title", "Tic-Tac-Toe Lobby (Client #3)");

        // startet die gleiche JavaFX App wie Client #1
        javafx.application.Application.launch(Main.class, args);
    }
}
