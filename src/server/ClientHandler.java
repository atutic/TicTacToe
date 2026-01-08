package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler extends Thread {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private ClientHandler opponent;
    private TicTacToeGame game;
    private char playerSymbol;

    public ClientHandler(Socket socket, TicTacToeGame game, char playerSymbol) {
        this.socket = socket;
        this.game = game;
        this.playerSymbol = playerSymbol;
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            sendMessage("WELCOME;" + playerSymbol);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Setter, um den Gegner zuzuweisen (damit wir wissen, an wen wir senden müssen)
    public void setOpponent(ClientHandler opponent) {
        this.opponent = opponent;
    }

    // Methode, um eine Nachricht an DIESEN Spieler zu senden
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    // Der Code in run() läuft permanent im Hintergrund
    @Override
    public void run() {
        try {
            String inputLine;
            // Warte auf Nachrichten von DIESEM Spieler
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Empfangen von " + playerSymbol + ": " + inputLine);
                String[] parts = inputLine.split(";");
                String command = parts[0];

                if ("MOVE".equals(command)) {
                    handleMove(parts);
                }
                // Weitere Befehle wie LOGIN könnten hier behandelt werden
            }
        } catch (IOException e) {
            System.out.println("Verbindung unterbrochen.");
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleMove(String[] parts) {
        if (parts.length < 3) return; // Ungültiges Format

        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);

            if (game.makeMove(x, y, playerSymbol)) {
                // Gültiger Zug
                opponent.sendMessage("VALID_MOVE;" + x + ";" + y + ";" + playerSymbol);
                this.sendMessage("VALID_MOVE;" + x + ";" + y + ";" + playerSymbol);

                char winner = game.checkWinner();
                if (winner != ' ') { // Spiel ist vorbei
                    String gameOverMsg = "GAME_OVER;" + winner;
                    opponent.sendMessage(gameOverMsg);
                    this.sendMessage(gameOverMsg);
                } else {
                    // Nächster Zug
                    String turnMsg = "TURN;" + game.getCurrentPlayer();
                    opponent.sendMessage(turnMsg);
                    this.sendMessage(turnMsg);
                }
            } else {
                // Ungültiger Zug
                this.sendMessage("ERROR;Ungültiger Zug. Du bist nicht am Zug oder das Feld ist belegt.");
            }
        } catch (NumberFormatException e) {
            this.sendMessage("ERROR;Ungültiges Koordinatenformat.");
        }
    }
}
