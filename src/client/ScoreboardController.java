package client;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import server.Protocol;

import java.util.ArrayList;
import java.util.List;

public class ScoreboardController {

    @FXML private TableView<String[]> table;
    @FXML private TableColumn<String[], String> colName;
    @FXML private TableColumn<String[], String> colW;
    @FXML private TableColumn<String[], String> colL;
    @FXML private TableColumn<String[], String> colD;

    @FXML private Label statusLabel;

    private final ObservableList<String[]> rows = FXCollections.observableArrayList();
    private final List<String[]> buffer = new ArrayList<>();

    @FXML
    public void initialize() {
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        colW.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        colL.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));
        colD.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[3]));

        table.setItems(rows);

        NetworkClient.getInstance().clearListeners();
        NetworkClient.getInstance().addListener(this::onMsg);

        refresh();
    }

    @FXML
    public void refresh() {
        rows.clear();
        buffer.clear();
        statusLabel.setText("Lade...");

        NetworkClient.getInstance().sendMessage(Protocol.CMD_SCORE_REQ);
    }

    @FXML
    public void back() {
        try {
            Main.changeScene("/client/lobby.fxml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onMsg(String msg) {
        Platform.runLater(() -> {
            if (msg.startsWith(Protocol.SRV_SCOREBOARD + Protocol.SEPARATOR)) {
                String payload = msg.substring((Protocol.SRV_SCOREBOARD + Protocol.SEPARATOR).length());

                buffer.clear();
                if (!payload.isBlank()) {
                    // name|w|l|d,name|w|l|d
                    for (String entry : payload.split(",")) {
                        String[] p = entry.split("\\|");
                        if (p.length >= 4) buffer.add(new String[]{p[0], p[1], p[2], p[3]});
                    }
                }

                rows.setAll(buffer);
                statusLabel.setText("Einträge: " + buffer.size());
            } else if (msg.startsWith(Protocol.SRV_ERROR + Protocol.SEPARATOR)) {
                statusLabel.setText("Fehler: " + msg.substring((Protocol.SRV_ERROR + Protocol.SEPARATOR).length()));
            }
        });
    }
}
