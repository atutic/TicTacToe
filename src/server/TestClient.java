package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class TestClient {
    public static void main(String[] args) {
        try {
            // Verbinde zum eigenen PC (localhost) auf Port 8088
            System.out.println("Versuche zu verbinden...");
            Socket socket = new Socket("localhost", 8088);
            System.out.println("Verbunden! Tippe etwas ein und drücke Enter.");

            // Output zum Server
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            // Input vom Server
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Ein extra Thread, der permanent auf Nachrichten vom Server hört
            new Thread(() -> {
                try {
                    String msg;
                    while ((msg = in.readLine()) != null) {
                        System.out.println(">> EMPFANGEN: " + msg);
                    }
                } catch (IOException e) {
                    System.out.println("Verbindung geschlossen.");
                }
            }).start();

            // Haupt-Thread: Liest deine Tastatur-Eingaben und schickt sie zum Server
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String input = scanner.nextLine();
                out.println(input); // Schickt Text an Server -> Server schickt an Gegner
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
