package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import server.Protocol;

public class GameController {

    @FXML
    private GridPane gameGrid;
    @FXML
    private Label statusLabel;
    @FXML
    private Label infoLabel;

    private Button[][] buttons = new Button[3][3];
    private char mySymbol;

    @FXML
    public void initialize() {
        // Erstelle das 3x3 Grid mit Buttons
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                Button button = new Button();
                button.setPrefSize(100, 100);
                button.setFont(new Font("Arial Bold", 40));
                final int r = row;
                final int c = col;
                button.setOnAction(event -> handleMove(r, c));
                buttons[row][col] = button;
                gameGrid.add(button, c, row);
            }
        }

        // Lausche auf Nachrichten vom Server
        NetworkClient.getInstance().listen(this::processServerMessage);
    }

    private void handleMove(int row, int col) {
        // Sende den Zug an den Server
        NetworkClient.getInstance().sendMessage(Protocol.CMD_MOVE + Protocol.SEPARATOR + row + Protocol.SEPARATOR + col);
    }

    private void processServerMessage(String message) {
        Platform.runLater(() -> {
            System.out.println("GUI empfängt: " + message);
            String[] parts = message.split(Protocol.SEPARATOR);
            String command = parts[0];

            switch (command) {
                case Protocol.SRV_WELCOME:
                    mySymbol = parts[1].charAt(0);
                    infoLabel.setText("Du bist: " + mySymbol);
                    break;
                case Protocol.SRV_MESSAGE:
                    statusLabel.setText(parts[1]);
                    break;
                case Protocol.SRV_TURN:
                    infoLabel.setText("Du bist: " + mySymbol + " | Am Zug: " + parts[1]);
                    break;
                case Protocol.SRV_VALID_MOVE:
                    int row = Integer.parseInt(parts[1]);
                    int col = Integer.parseInt(parts[2]);
                    String symbol = parts[3];
                    buttons[row][col].setText(symbol);
                    buttons[row][col].setDisable(true);
                    break;
                case Protocol.SRV_GAME_OVER:
                    String winner = parts[1];
                    if (winner.equals("D")) {
                        statusLabel.setText("Unentschieden!");
                    } else {
                        statusLabel.setText("Spiel vorbei! Gewinner ist " + winner);
                    }
                    disableAllButtons();
                    break;
                case Protocol.SRV_ERROR:
                    statusLabel.setText("Fehler: " + parts[1]);
                    break;
            }
        });
    }

    private void disableAllButtons() {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                buttons[row][col].setDisable(true);
            }
        }
    }
}
