package client;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import server.Protocol;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class HistoryController {

    @FXML private TextField playerFilterField;
    @FXML private DatePicker fromDate;
    @FXML private DatePicker toDate;
    @FXML private Label statusLabel;

    @FXML private TableView<String[]> table;
    @FXML private TableColumn<String[], String> colDate;
    @FXML private TableColumn<String[], String> colX;
    @FXML private TableColumn<String[], String> colO;
    @FXML private TableColumn<String[], String> colWinner;

    private final ObservableList<String[]> rows = FXCollections.observableArrayList();
    private final List<String[]> buffer = new ArrayList<>();

    @FXML
    public void initialize() {
        colDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        colX.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        colO.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));
        colWinner.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[3]));

        table.setItems(rows);

        NetworkClient.getInstance().clearListeners();
        NetworkClient.getInstance().addListener(this::onMsg);

        search();
    }

    @FXML
    public void search() {
        rows.clear();
        buffer.clear();

        String pf = playerFilterField.getText() == null ? "" : playerFilterField.getText().trim();
        LocalDate f = fromDate.getValue();
        LocalDate t = toDate.getValue();

        String from = (f == null) ? "" : f.toString();
        String to = (t == null) ? "" : t.toString();

        statusLabel.setText("Lade History...");

        NetworkClient.getInstance().sendMessage(
                Protocol.CMD_HISTORY_REQ + Protocol.SEPARATOR + pf + Protocol.SEPARATOR + from + Protocol.SEPARATOR + to
        );
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
            if (msg.startsWith(Protocol.SRV_HISTORY_LINE + Protocol.SEPARATOR)) {
                String line = msg.substring((Protocol.SRV_HISTORY_LINE + Protocol.SEPARATOR).length());
                // timestamp;X;O;winner;moves
                String[] p = line.split(";", -1);
                if (p.length >= 4) {
                    String ts = p[0];
                    String x = p[1];
                    String o = p[2];
                    String w = p[3];
                    buffer.add(new String[]{ts, x, o, w});
                }
            } else if (msg.equals(Protocol.SRV_HISTORY_END)) {
                rows.setAll(buffer);
                statusLabel.setText("Einträge: " + buffer.size());
            } else if (msg.startsWith(Protocol.SRV_ERROR + Protocol.SEPARATOR)) {
                statusLabel.setText("Fehler: " + msg.substring((Protocol.SRV_ERROR + Protocol.SEPARATOR).length()));
            }
        });
    }
}
