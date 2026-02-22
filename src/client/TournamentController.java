package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import server.Protocol;

public class TournamentController {

    @FXML private Label statusLabel;
    @FXML private ListView<String> tournamentList;
    @FXML private TextField tournamentNameField;
    @FXML private ChoiceBox<String> maxPlayersChoice;
    @FXML private TextArea logArea;

    private static String pendingStartMsg = null;

    @FXML
    public void initialize() {
        maxPlayersChoice.getItems().addAll("2", "4", "8");
        maxPlayersChoice.setValue("4");

        NetworkClient.getInstance().clearListeners();
        NetworkClient.getInstance().addListener(this::onMsg);

        refresh();
    }

    @FXML
    public void refresh() {
        tournamentList.getItems().clear();
        NetworkClient.getInstance().sendMessage(Protocol.CMD_LIST_TOURNAMENTS);
    }

    @FXML
    public void hostTournament() {
        String name = tournamentNameField.getText();
        if (name == null || name.trim().isEmpty()) name = "Turnier";
        String maxP = maxPlayersChoice.getValue();
        NetworkClient.getInstance().sendMessage(
                Protocol.CMD_HOST_TOURNAMENT + Protocol.SEPARATOR + name.trim() + Protocol.SEPARATOR + maxP
        );
    }

    @FXML
    public void joinTournament() {
        String sel = tournamentList.getSelectionModel().getSelectedItem();
        if (sel == null || sel.isBlank()) {
            statusLabel.setText("Bitte ein Turnier auswählen.");
            return;
        }
        String tournamentId = sel.split("\\|")[0].trim();
        NetworkClient.getInstance().sendMessage(
                Protocol.CMD_JOIN_TOURNAMENT + Protocol.SEPARATOR + tournamentId
        );
    }

    @FXML
    public void startTournament() {
        String sel = tournamentList.getSelectionModel().getSelectedItem();
        if (sel == null || sel.isBlank()) {
            statusLabel.setText("Bitte ein Turnier auswählen.");
            return;
        }
        String tournamentId = sel.split("\\|")[0].trim();
        NetworkClient.getInstance().sendMessage(
                Protocol.CMD_START_TOURNAMENT + Protocol.SEPARATOR + tournamentId
        );
    }

    @FXML
    public void back() {
        try {
            Main.changeScene("/client/lobby.fxml");
        } catch (Exception e) {
            System.out.println("[Tournament] Scene-Wechsel fehlgeschlagen: " + e.getMessage());
        }
    }

    private void onMsg(String msg) {
        Platform.runLater(() -> {
            if (msg.startsWith(Protocol.SRV_TOURNAMENTS + Protocol.SEPARATOR)) {
                String payload = msg.substring((Protocol.SRV_TOURNAMENTS + Protocol.SEPARATOR).length());
                tournamentList.getItems().clear();
                if (!payload.isBlank()) {
                    for (String entry : payload.split(",")) {
                        tournamentList.getItems().add(entry);
                    }
                }
                statusLabel.setText("Turniere: " + tournamentList.getItems().size());

            } else if (msg.startsWith(Protocol.SRV_TOURNAMENT_HOSTED + Protocol.SEPARATOR)) {
                String tId = msg.substring((Protocol.SRV_TOURNAMENT_HOSTED + Protocol.SEPARATOR).length());
                logArea.appendText("[Turnier] Erstellt: " + tId + "\n");
                refresh();

            } else if (msg.startsWith(Protocol.SRV_TOURNAMENT_STARTED + Protocol.SEPARATOR)) {
                logArea.appendText("[Turnier] Gestartet!\n");

            } else if (msg.startsWith(Protocol.SRV_TOURNAMENT_MATCH + Protocol.SEPARATOR)) {
                // TMATCH;round;matchIndex;playerX;playerO
                String[] p = msg.substring((Protocol.SRV_TOURNAMENT_MATCH + Protocol.SEPARATOR).length()).split(Protocol.SEPARATOR);
                if (p.length >= 4) {
                    logArea.appendText("[Runde " + p[0] + "] Match " + p[1] + ": " + p[2] + " vs " + p[3] + "\n");
                }

            } else if (msg.startsWith(Protocol.SRV_TOURNAMENT_RESULT + Protocol.SEPARATOR)) {
                // TRESULT;round;matchIndex;winner
                String[] p = msg.substring((Protocol.SRV_TOURNAMENT_RESULT + Protocol.SEPARATOR).length()).split(Protocol.SEPARATOR);
                if (p.length >= 3) {
                    logArea.appendText("[Runde " + p[0] + "] Gewinner Match " + p[1] + ": " + p[2] + "\n");
                }

            } else if (msg.startsWith(Protocol.SRV_TOURNAMENT_OVER + Protocol.SEPARATOR)) {
                String winner = msg.substring((Protocol.SRV_TOURNAMENT_OVER + Protocol.SEPARATOR).length());
                logArea.appendText("\n🏆 TURNIER-SIEGER: " + winner + " 🏆\n\n");
                statusLabel.setText("Turnier beendet! Sieger: " + winner);

            } else if (msg.startsWith(Protocol.SRV_TOURNAMENT_MSG + Protocol.SEPARATOR)) {
                String text = msg.substring((Protocol.SRV_TOURNAMENT_MSG + Protocol.SEPARATOR).length());
                logArea.appendText(text + "\n");

            } else if (msg.startsWith(Protocol.SRV_START + Protocol.SEPARATOR)) {
                // Turnier-Match startet -> zum Game wechseln
                LobbyController.consumePendingSpectateMsg(); // clear
                pendingStartMsg = msg;
                try {
                    NetworkClient.getInstance().clearListeners();
                    // Set pending start msg so GameController picks it up
                    // We need to set it via LobbyController's static field
                    setPendingStartForGame(msg);
                    Main.changeScene("/client/game.fxml");
                } catch (Exception e) {
                    System.out.println("[Tournament] Scene-Wechsel fehlgeschlagen: " + e.getMessage());
                    statusLabel.setText("Scene-Wechsel fehlgeschlagen: " + e.getMessage());
                }

            } else if (msg.startsWith(Protocol.SRV_ERROR + Protocol.SEPARATOR)) {
                statusLabel.setText("Fehler: " + msg.substring((Protocol.SRV_ERROR + Protocol.SEPARATOR).length()));

            } else if (msg.startsWith(Protocol.SRV_MESSAGE + Protocol.SEPARATOR)) {
                statusLabel.setText(msg.substring((Protocol.SRV_MESSAGE + Protocol.SEPARATOR).length()));
            }
        });
    }

    /**
     * Setzt die pendingStartMsg im LobbyController, damit der GameController sie auslesen kann.
     */
    private void setPendingStartForGame(String msg) {
        LobbyController.setPendingStartMsg(msg);
    }
}
