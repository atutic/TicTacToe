package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;

        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/client/lobby.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root);

        stage.setTitle(System.getProperty("tictactoe.window.title", "Tic-Tac-Toe Lobby"));
        stage.setScene(scene);
        stage.setResizable(true);
        stage.show();
    }

    public static void changeScene(String fxmlAbsolutePath) throws IOException {
        var url = Main.class.getResource(fxmlAbsolutePath);
        if (url == null) {
            throw new IOException("FXML nicht gefunden: " + fxmlAbsolutePath);
        }

        System.out.println("[SCENE] switch -> " + url);

        FXMLLoader loader = new FXMLLoader(url);
        Parent pane = loader.load();

        if (primaryStage == null || primaryStage.getScene() == null) {
            throw new IOException("PrimaryStage/Scene ist null (should never happen).");
        }
        primaryStage.getScene().setRoot(pane);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
