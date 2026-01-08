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
        FXMLLoader loader = new FXMLLoader(getClass().getResource("lobby.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root);
        stage.setTitle("Tic-Tac-Toe Lobby");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void changeScene(String fxml) throws IOException {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource(fxml));
        Parent pane = loader.load();
        primaryStage.getScene().setRoot(pane);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
