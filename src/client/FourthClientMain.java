package client;

public class FourthClientMain {

    public static void main(String[] args) {
        System.setProperty("tictactoe.settings.file", "client_settings_4.properties");
        System.setProperty("tictactoe.window.title", "Tic-Tac-Toe Lobby (Client #4)");

        // startet die gleiche JavaFX App wie Client #1
        javafx.application.Application.launch(Main.class, args);
    }
}
