package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import server.Protocol;

public class GameController {

    @FXML private GridPane gameGrid;
    @FXML private Label statusLabel;
    @FXML private Label infoLabel;

    @FXML private Canvas winCanvas;

    @FXML private TextArea chatArea;
    @FXML private TextField chatInput;
    @FXML private Button sendChatBtn;

    @FXML private Button rematchBtn;
    @FXML private Button backBtn;

    private final Button[][] buttons = new Button[3][3];
    private char mySymbol = '\0';
    private boolean myTurn = false;
    private boolean gameOver = false;
    private final char[][] board = new char[3][3];

    private boolean rematchRequestedByMe = false;
    private boolean rematchOfferedToMe = false;

    @FXML
    public void initialize() {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                Button button = new Button();
                button.setPrefSize(120, 120);
                button.setFont(new Font("Arial Bold", 44));
                final int r = row;
                final int c = col;
                button.setOnAction(event -> handleMove(r, c));
                buttons[row][col] = button;
                gameGrid.add(button, c, row);
            }
        }

        Platform.runLater(() -> {
            winCanvas.setWidth(gameGrid.getWidth());
            winCanvas.setHeight(gameGrid.getHeight());
        });
        gameGrid.widthProperty().addListener((o, a, b) -> winCanvas.setWidth(b.doubleValue()));
        gameGrid.heightProperty().addListener((o, a, b) -> winCanvas.setHeight(b.doubleValue()));

        NetworkClient.getInstance().clearListeners();
        NetworkClient.getInstance().addListener(this::processServerMessage);

        String start = LobbyController.consumePendingStartMsg();
        if (start != null) {
            String[] p = start.split(Protocol.SEPARATOR);
            if (p.length >= 3 && !p[2].isEmpty()) mySymbol = p[2].charAt(0);

            myTurn = (mySymbol == 'X');
            updateInfoLabel('X');
            statusLabel.setText("Spiel gestartet. Du bist " + mySymbol);
            setBoardEnabled(myTurn);
        } else {
            updateInfoLabel('\0');
            statusLabel.setText("Warte auf Start...");
            setBoardEnabled(false);
        }

        rematchBtn.setDisable(true);
        backBtn.setDisable(true);

        rematchRequestedByMe = false;
        rematchOfferedToMe = false;
        clearWinLine();
    }

    private void handleMove(int row, int col) {
        if (gameOver || !myTurn) return;
        if (board[row][col] != '\0') return;

        setBoardEnabled(false);
        NetworkClient.getInstance().sendMessage(
                Protocol.CMD_MOVE + Protocol.SEPARATOR + row + Protocol.SEPARATOR + col
        );
    }

    @FXML
    public void sendChat() {
        String msg = chatInput.getText();
        if (msg == null) return;
        msg = msg.trim();
        if (msg.isEmpty()) return;

        msg = msg.replace(";", ",");
        NetworkClient.getInstance().sendMessage(Protocol.CMD_CHAT + Protocol.SEPARATOR + msg);
        chatInput.clear();
    }

    @FXML
    public void requestRematch() {
        if (!gameOver) return;

        rematchRequestedByMe = true;
        rematchBtn.setDisable(true);
        statusLabel.setText("Rematch angeboten...");

        NetworkClient.getInstance().sendMessage(Protocol.CMD_REMATCH);
    }

    @FXML
    public void backToLobby() {
        // leaving = decline
        if (gameOver) {
            NetworkClient.getInstance().sendMessage(Protocol.CMD_REMATCH_DECLINE);
        }
        NetworkClient.getInstance().sendMessage(Protocol.CMD_LEAVE);

        try {
            NetworkClient.getInstance().clearListeners();
            Main.changeScene("/client/lobby.fxml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processServerMessage(String message) {
        Platform.runLater(() -> {
            int sepIndex = message.indexOf(Protocol.SEPARATOR);
            String command = (sepIndex >= 0) ? message.substring(0, sepIndex) : message;
            String payload = (sepIndex >= 0) ? message.substring(sepIndex + 1) : "";

            switch (command) {
                case Protocol.SRV_WELCOME:
                    if (!payload.isEmpty()) mySymbol = payload.charAt(0);
                    break;

                case Protocol.SRV_MESSAGE:
                    statusLabel.setText(payload);
                    break;

                case Protocol.SRV_TURN: {
                    char current = payload.isEmpty() ? '\0' : payload.charAt(0);
                    myTurn = (current != '\0' && current == mySymbol);
                    updateInfoLabel(current);
                    if (!gameOver) setBoardEnabled(myTurn);
                    break;
                }

                case Protocol.SRV_VALID_MOVE: {
                    String[] moveParts = payload.split(Protocol.SEPARATOR);
                    if (moveParts.length >= 3) {
                        int r = Integer.parseInt(moveParts[0]);
                        int c = Integer.parseInt(moveParts[1]);
                        String sym = moveParts[2];
                        applyMove(r, c, sym);
                    }
                    break;
                }

                case Protocol.SRV_CHAT: {
                    String[] p = payload.split(Protocol.SEPARATOR, 2);
                    String from = (p.length >= 1) ? p[0] : "?";
                    String msg = (p.length >= 2) ? p[1] : "";
                    chatArea.appendText(from + ": " + msg + "\n");
                    break;
                }

                case Protocol.SRV_REMATCH_OFFER: {
                    // ROFFER;from
                    rematchOfferedToMe = true;
                    String from = payload.isBlank() ? "?" : payload.trim();

                    if (!gameOver) {
                        // dumm gelaufen lol
                        break;
                    }

                    Alert a = new Alert(Alert.AlertType.CONFIRMATION);
                    a.setTitle("Rematch");
                    a.setHeaderText(from + " will ein Rematch.");
                    a.setContentText("Annehmen? (Wenn du in die Lobby gehst, gilt das als Ablehnung.)");

                    ButtonType accept = new ButtonType("Annehmen");
                    ButtonType stay = new ButtonType("Später");
                    a.getButtonTypes().setAll(accept, stay);

                    var res = a.showAndWait();
                    if (res.isPresent() && res.get() == accept) {
                        statusLabel.setText("Rematch angenommen. Starte neu...");
                        NetworkClient.getInstance().sendMessage(Protocol.CMD_REMATCH_ACCEPT);
                        rematchBtn.setDisable(true);
                        backBtn.setDisable(true);
                    } else {
                        // bleibt im Screen, kann noch Back drücken
                        statusLabel.setText("Rematch offen. Du kannst annehmen (Popup) oder in Lobby gehen.");
                        rematchBtn.setDisable(true);
                        backBtn.setDisable(false);
                    }
                    break;
                }

                case Protocol.SRV_REMATCH_DECLINED: {
                    // RDECL;reason
                    String reason = payload.isBlank() ? "abgelehnt" : payload;
                    statusLabel.setText("Rematch abgelehnt: " + reason);
                    rematchBtn.setDisable(true);
                    backBtn.setDisable(false);
                    break;
                }

                case Protocol.SRV_OPPONENT_LEFT: {
                    statusLabel.setText("Gegner ist weg. Du kannst nur noch zurück in die Lobby.");
                    rematchBtn.setDisable(true);
                    backBtn.setDisable(false);
                    setBoardEnabled(false);
                    break;
                }

                case Protocol.SRV_GAME_OVER:
                    gameOver = true;
                    myTurn = false;
                    setBoardEnabled(false);

                    if ("D".equals(payload)) {
                        statusLabel.setText("Unentschieden!");
                    } else {
                        statusLabel.setText("Spiel vorbei! Gewinner ist " + payload);
                        drawWinLine(payload.charAt(0));
                    }

                    // jetzt sind die Buttons erlaubt
                    rematchBtn.setDisable(false);
                    backBtn.setDisable(false);

                    rematchRequestedByMe = false;
                    rematchOfferedToMe = false;
                    break;

                case Protocol.SRV_ERROR:
                    statusLabel.setText("Fehler: " + payload);
                    if (!gameOver) setBoardEnabled(myTurn);
                    break;

                case Protocol.SRV_START:
                    // Rematch startet auch über START -> reset state
                    resetForNewMatch(payload);
                    break;
            }
        });
    }

    private void resetForNewMatch(String payload) {
        // // START-Payload auswerten (egal ob komplett oder schon ohne "START;")

        gameOver = false;
        clearWinLine();

        // clear board
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                board[r][c] = '\0';
                buttons[r][c].setText("");
                buttons[r][c].setStyle("");
                buttons[r][c].setDisable(true);
            }
        }

        // parse symbol
        String[] p = payload.split(Protocol.SEPARATOR);
        // payload starts with sessionId in normal format
        if (p.length >= 2 && !p[1].isEmpty()) {
            mySymbol = p[1].charAt(0);
        }

        myTurn = (mySymbol == 'X');
        updateInfoLabel('X');
        statusLabel.setText("Neues Spiel. Du bist " + mySymbol);
        setBoardEnabled(myTurn);

        rematchBtn.setDisable(true);
        backBtn.setDisable(true);
    }

    private void applyMove(int row, int col, String symbol) {
        if (row < 0 || row >= 3 || col < 0 || col >= 3) return;

        char s = (symbol == null || symbol.isEmpty()) ? '\0' : symbol.charAt(0);
        board[row][col] = s;

        Button b = buttons[row][col];
        b.setText(symbol);

        if (s == 'X') b.setStyle("-fx-text-fill: red;");
        else if (s == 'O') b.setStyle("-fx-text-fill: blue;");

        b.setDisable(true);
    }

    private void updateInfoLabel(char currentTurn) {
        String me = (mySymbol == '\0') ? "?" : String.valueOf(mySymbol);
        String turn = (currentTurn == '\0') ? "?" : String.valueOf(currentTurn);
        infoLabel.setText("Du bist: " + me + " | Am Zug: " + turn);
    }

    private void setBoardEnabled(boolean enabled) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                boolean isEmpty = buttons[row][col].getText() == null || buttons[row][col].getText().isEmpty();
                buttons[row][col].setDisable(!enabled || !isEmpty);
            }
        }
    }

    private void clearWinLine() {
        GraphicsContext gc = winCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, winCanvas.getWidth(), winCanvas.getHeight());
    }

    private void drawWinLine(char winner) {
        int[][] win = findWinCells(winner);
        if (win == null) return;

        double[] p1 = centerOfCell(win[0][0], win[0][1]);
        double[] p2 = centerOfCell(win[2][0], win[2][1]);
        if (p1 == null || p2 == null) return;

        GraphicsContext gc = winCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, winCanvas.getWidth(), winCanvas.getHeight());
        gc.setLineWidth(6);
        gc.setStroke(javafx.scene.paint.Color.BLACK);
        gc.strokeLine(p1[0], p1[1], p2[0], p2[1]);
    }

    private double[] centerOfCell(int row, int col) {
        Button b = buttons[row][col];
        Bounds bounds = b.localToScene(b.getBoundsInLocal());
        Bounds gridBounds = gameGrid.localToScene(gameGrid.getBoundsInLocal());

        double x = (bounds.getMinX() - gridBounds.getMinX()) + bounds.getWidth() / 2.0;
        double y = (bounds.getMinY() - gridBounds.getMinY()) + bounds.getHeight() / 2.0;

        return new double[]{x, y};
    }

    private int[][] findWinCells(char w) {
        for (int r = 0; r < 3; r++) {
            if (board[r][0] == w && board[r][1] == w && board[r][2] == w)
                return new int[][]{{r,0},{r,1},{r,2}};
        }
        for (int c = 0; c < 3; c++) {
            if (board[0][c] == w && board[1][c] == w && board[2][c] == w)
                return new int[][]{{0,c},{1,c},{2,c}};
        }
        if (board[0][0] == w && board[1][1] == w && board[2][2] == w)
            return new int[][]{{0,0},{1,1},{2,2}};
        if (board[0][2] == w && board[1][1] == w && board[2][0] == w)
            return new int[][]{{0,2},{1,1},{2,0}};
        return null;
    }
}
