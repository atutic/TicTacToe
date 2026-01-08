package server;

/**
 * Definiert das textbasierte Netzwerkprotokoll für das Tic-Tac-Toe-Spiel.
 * Dient als zentrale Schnittstelle für Client und Server.
 */
public final class Protocol {

    public static final String SEPARATOR = ";";

    // Client an Server Befehle
    public static final String CMD_LOGIN = "LOGIN"; // Client meldet sich an -> LOGIN;Spielername
    public static final String CMD_MOVE = "MOVE";   // Client macht einen Zug -> MOVE;x;y
    public static final String CMD_CHAT = "CHAT";   // Client sendet Chat-Nachricht -> CHAT;Nachricht

    // Server an Client Befehle
    public static final String SRV_WELCOME = "WELCOME";       // Server weist Symbol zu -> WELCOME;X oder WELCOME;O
    public static final String SRV_MESSAGE = "MESSAGE";       // Server sendet Info-Text -> MESSAGE;Text
    public static final String SRV_VALID_MOVE = "VALID_MOVE"; // Server bestätigt Zug -> VALID_MOVE;x;y;Symbol
    public static final String SRV_GAME_OVER = "GAME_OVER";   // Spielende -> GAME_OVER;X, GAME_OVER;O oder GAME_OVER;D
    public static final String SRV_TURN = "TURN";             // Spieler am Zug -> TURN;X oder TURN;O
    public static final String SRV_ERROR = "ERROR";           // Server meldet Fehler -> ERROR;Fehlertext
    public static final String SRV_CHAT = "CHAT";             // Server leitet Chat weiter -> CHAT;Spielername;Nachricht
}
