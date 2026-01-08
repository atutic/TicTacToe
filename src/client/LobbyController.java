package client;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.io.IOException;

public class LobbyController {

    @FXML
    private TextField hostnameField;
    @FXML
    private TextField portField;
    @FXML
    private TextField usernameField;
    @FXML
    private Label statusLabel;

    @FXML
    protected void connectToServer() {
        String host = hostnameField.getText();
        String portStr = portField.getText();
        String username = usernameField.getText();

        if (host.isEmpty() || portStr.isEmpty() || username.isEmpty()) {
            statusLabel.setText("Bitte alle Felder ausfüllen.");
            return;
        }

        try {
            int port = Integer.parseInt(portStr);
            NetworkClient client = NetworkClient.getInstance();
            client.connect(host, port, username);

            // Wenn die Verbindung erfolgreich ist, wechsle die Szene
            Main.changeScene("game.fxml");

        } catch (NumberFormatException e) {
            statusLabel.setText("Fehler: Ungültige Portnummer.");
        } catch (IOException e) {
            statusLabel.setText("Verbindungsfehler: " + e.getMessage());
        }
    }
}
