package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import server.Protocol;

import java.io.IOException;

public class LobbyController {

    @FXML private TextField hostnameField;
    @FXML private TextField portField;
    @FXML private TextField usernameField;
    @FXML private Label statusLabel;

    @FXML private ChoiceBox<String> modeChoice;
    @FXML private Spinner<Integer> timerSpinner;

    @FXML private Button refreshBtn;
    @FXML private Button hostBtn;
    @FXML private Button joinBtn;
    @FXML private Button scoresBtn;
    @FXML private Button historyBtn;

    @FXML private ListView<String> roomsList;
    @FXML private TextField roomNameField;

    private Settings settings;
    private static String pendingStartMsg = null;

    @FXML
    public void initialize() {
        settings = Settings.load();

        hostnameField.setText(settings.host);
        portField.setText(String.valueOf(settings.port));
        usernameField.setText(settings.username);

        modeChoice.getItems().addAll("HUMAN", "BOT");
        modeChoice.setValue(settings.mode.name());

        timerSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(3, 120, settings.timerSec));

        NetworkClient.getInstance().clearListeners();
        NetworkClient.getInstance().addListener(this::onServerMessage);

        Platform.runLater(this::autoConnect);
    }

    private void autoConnect() {
        try {
            NetworkClient nc = NetworkClient.getInstance();
            if (!nc.isConnected()) nc.connect(settings.host, settings.port);
            nc.setIdentity(settings.username, settings.mode, settings.timerSec);
            nc.sendLoginAndSettings();

            statusLabel.setText("Verbunden als " + settings.username + " (" + settings.mode + ", " + settings.timerSec + "s)");

            refreshBtn.setDisable(false);
            hostBtn.setDisable(false);
            joinBtn.setDisable(false);
            scoresBtn.setDisable(false);
            historyBtn.setDisable(false);

            nc.sendMessage(Protocol.CMD_LIST);
        } catch (Exception e) {
            statusLabel.setText("Auto-Connect fehlgeschlagen: " + e.getMessage());
        }
    }

    @FXML
    public void connect() {
        String host = hostnameField.getText().trim();
        String portStr = portField.getText().trim();
        String user = usernameField.getText().trim();

        if (host.isEmpty() || portStr.isEmpty() || user.isEmpty()) {
            statusLabel.setText("Bitte Host/Port/Spielername setzen.");
            return;
        }

        int port;
        try { port = Integer.parseInt(portStr); }
        catch (NumberFormatException e) {
            statusLabel.setText("Port ist keine Zahl !");
            return;
        }

        Settings.Mode mode = Settings.Mode.valueOf(modeChoice.getValue());
        int timerSec = timerSpinner.getValue();

        settings.host = host;
        settings.port = port;
        settings.username = user;
        settings.mode = mode;
        settings.timerSec = timerSec;
        settings.save();

        try {
            NetworkClient nc = NetworkClient.getInstance();
            if (!nc.isConnected()) nc.connect(host, port);
            nc.setIdentity(user, mode, timerSec);
            nc.sendLoginAndSettings();

            statusLabel.setText("Verbunden als " + user + " (" + mode + ", " + timerSec + "s)");

            refreshBtn.setDisable(false);
            hostBtn.setDisable(false);
            joinBtn.setDisable(false);
            scoresBtn.setDisable(false);
            historyBtn.setDisable(false);

            nc.sendMessage(Protocol.CMD_LIST);

        } catch (IOException e) {
            statusLabel.setText("Verbindungsfehler: " + e.getMessage());
        }
    }

    @FXML
    public void refreshRooms() {
        NetworkClient.getInstance().sendMessage(Protocol.CMD_LIST);
    }

    @FXML
    public void hostRoom() {
        String rn = roomNameField.getText().trim();
        if (rn.isEmpty()) rn = "Room-" + System.currentTimeMillis();
        NetworkClient.getInstance().sendMessage(Protocol.CMD_HOST + Protocol.SEPARATOR + rn);
    }

    @FXML
    public void joinRoom() {
        String sel = roomsList.getSelectionModel().getSelectedItem();
        if (sel == null || sel.isBlank()) return;

        String roomId = sel.split("\\|")[0].trim();
        NetworkClient.getInstance().sendMessage(Protocol.CMD_JOIN + Protocol.SEPARATOR + roomId);
    }

    @FXML
    public void requestScores() {
        try {
            Main.changeScene("/client/scoreboard.fxml");
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Scoreboard-View fehlgeschlagen: " + e.getMessage());
        }
    }


    @FXML
    public void openHistory() {
        try {
            Main.changeScene("/client/history.fxml");
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("History-View fehlgeschlagen: " + e.getMessage());
        }
    }

    public static String consumePendingStartMsg() {
        String tmp = pendingStartMsg;
        pendingStartMsg = null;
        return tmp;
    }

    private void onServerMessage(String msg) {
        Platform.runLater(() -> {
            if (msg.startsWith(Protocol.SRV_START + Protocol.SEPARATOR)) {
                pendingStartMsg = msg;
                try {
                    NetworkClient.getInstance().clearListeners();
                    Main.changeScene("/client/game.fxml");
                } catch (Exception e) {
                    e.printStackTrace();
                    statusLabel.setText("Scene-Wechsel fehlgeschlagen: " + e.getMessage());
                }
                return;
            }

            if (msg.startsWith(Protocol.SRV_MESSAGE + Protocol.SEPARATOR)) {
                statusLabel.setText(msg.substring((Protocol.SRV_MESSAGE + Protocol.SEPARATOR).length()));
            } else if (msg.startsWith(Protocol.SRV_ERROR + Protocol.SEPARATOR)) {
                statusLabel.setText("Fehler: " + msg.substring((Protocol.SRV_ERROR + Protocol.SEPARATOR).length()));
            } else if (msg.startsWith(Protocol.SRV_ROOMS + Protocol.SEPARATOR)) {
                roomsList.getItems().clear();
                String payload = msg.substring((Protocol.SRV_ROOMS + Protocol.SEPARATOR).length());
                if (!payload.isBlank()) {
                    for (String r : payload.split(",")) roomsList.getItems().add(r);
                }
            }
        });
    }
}
