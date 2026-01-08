package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TicTacToeServer {
    public static void main(String[] args) {
        int port = 8088; // Portnummer (muss > 1024 sein)

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server gestartet. Warte auf Spieler auf Port " + port + "...");

            // 1. Auf Spieler 1 warten (blockiert, bis Verbindung kommt)
            Socket socket1 = serverSocket.accept();
            System.out.println("Spieler 1 verbunden!");
            TicTacToeGame game = new TicTacToeGame();
            ClientHandler player1 = new ClientHandler(socket1, game, 'X');
            player1.sendMessage("MESSAGE;Warte auf Spieler 2...");

            // 2. Auf Spieler 2 warten
            Socket socket2 = serverSocket.accept();
            System.out.println("Spieler 2 verbunden!");
            ClientHandler player2 = new ClientHandler(socket2, game, 'O');

            // 3. Die beiden Spieler einander vorstellen (Verknüpfen)
            player1.setOpponent(player2);
            player2.setOpponent(player1);

            // 4. Threads starten (Damit beide gleichzeitig senden können)
            player1.start();
            player2.start();

            // 5. Spielstart signalisieren
            player1.sendMessage("MESSAGE;Spiel startet. Du bist X.");
            player2.sendMessage("MESSAGE;Spiel startet. Du bist O.");
            player1.sendMessage("TURN;X"); // Spieler X beginnt
            player2.sendMessage("TURN;X");

            System.out.println("Spiel läuft. Server leitet Nachrichten weiter.");

        } catch (IOException e) {
            System.err.println("Serverfehler: " + e.getMessage());
        }
    }
}