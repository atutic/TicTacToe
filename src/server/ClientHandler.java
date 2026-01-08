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
    private ScoreManager scoreManager;
    private String playerName;

    public ClientHandler(Socket socket, TicTacToeGame game, char playerSymbol, ScoreManager scoreManager) {
        this.socket = socket;
        this.game = game;
        this.playerSymbol = playerSymbol;
        this.scoreManager = scoreManager;
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            sendMessage(Protocol.SRV_WELCOME + Protocol.SEPARATOR + playerSymbol);
            sendMessage(Protocol.SRV_MESSAGE + Protocol.SEPARATOR + "Bitte mit LOGIN;DeinName anmelden.");
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
    public String getPlayerName() {
        return playerName;
    }

    @Override
    public void run() {
        try {
            // 1. Auf LOGIN warten
            String loginLine = in.readLine();
            if (loginLine != null && loginLine.startsWith(Protocol.CMD_LOGIN + Protocol.SEPARATOR)) {
                String[] parts = loginLine.split(";");
                if (parts.length > 1) {
                    this.playerName = parts[1];
                    System.out.println("Spieler " + playerSymbol + " hat sich als '" + this.playerName + "' angemeldet.");
                    sendMessage(Protocol.SRV_MESSAGE + Protocol.SEPARATOR + "Login erfolgreich, " + this.playerName);
                } else {
                    this.playerName = "Spieler_" + playerSymbol;
                     sendMessage(Protocol.SRV_MESSAGE + Protocol.SEPARATOR + "Login ungültig. Du wirst '" + this.playerName + "' genannt.");
                }
            } else {
                // Fallback, wenn kein korrekter Login kommt
                this.playerName = "Spieler_" + playerSymbol;
                 sendMessage(Protocol.SRV_MESSAGE + Protocol.SEPARATOR + "Kein Login. Du wirst '" + this.playerName + "' genannt.");
            }

            // 2. Auf Spielzüge warten
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Empfangen von " + playerName + ": " + inputLine);
                String[] parts = inputLine.split(";");
                String command = parts[0];

                if (Protocol.CMD_MOVE.equals(command)) {
                    handleMove(parts);
                }
            }
        } catch (IOException e) {
            System.out.println("Verbindung zu " + (playerName != null ? playerName : "einem Spieler") + " unterbrochen.");
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
                opponent.sendMessage(Protocol.SRV_VALID_MOVE + Protocol.SEPARATOR + x + ";" + y + ";" + playerSymbol);
                this.sendMessage(Protocol.SRV_VALID_MOVE + Protocol.SEPARATOR + x + ";" + y + ";" + playerSymbol);

                char winner = game.checkWinner();
                if (winner != ' ') { // Spiel ist vorbei
                    if (winner == 'D') { // Unentschieden
                        scoreManager.recordDraw(this.playerName, opponent.getPlayerName());
                    } else if (winner == this.playerSymbol) { // Dieser Spieler hat gewonnen
                        scoreManager.recordGameResult(this.playerName, opponent.getPlayerName());
                    } else { // Der Gegner hat gewonnen
                        scoreManager.recordGameResult(opponent.getPlayerName(), this.playerName);
                    }

                    String gameOverMsg = Protocol.SRV_GAME_OVER + Protocol.SEPARATOR + winner;
                    opponent.sendMessage(gameOverMsg);
                    this.sendMessage(gameOverMsg);
                } else {
                    // Nächster Zug
                    String turnMsg = Protocol.SRV_TURN + Protocol.SEPARATOR + game.getCurrentPlayer();
                    opponent.sendMessage(turnMsg);
                    this.sendMessage(turnMsg);
                }
            } else {
                // Ungültiger Zug
                this.sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Ungültiger Zug. Du bist nicht am Zug oder das Feld ist belegt.");
            }
        } catch (NumberFormatException e) {
            this.sendMessage(Protocol.SRV_ERROR + Protocol.SEPARATOR + "Ungültiges Koordinatenformat.");
        }
    }
}
