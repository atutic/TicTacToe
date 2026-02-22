package server;

public final class Protocol {
    private Protocol() {}
    public static final String SEPARATOR = ";";

    // Client -> Server
    public static final String CMD_LOGIN = "LOGIN";               // LOGIN;name
    public static final String CMD_SETTINGS = "SETTINGS";         // SETTINGS;mode;timerSec
    public static final String CMD_LIST = "LIST";                 // LIST
    public static final String CMD_HOST = "HOST";                 // HOST;roomName
    public static final String CMD_JOIN = "JOIN";                 // JOIN;roomId
    public static final String CMD_MOVE = "MOVE";                 // MOVE;row;col
    public static final String CMD_CHAT = "CHAT";                 // CHAT;msg
    public static final String CMD_SCORE_REQ = "SCORES";          // SCORES
    public static final String CMD_HISTORY_REQ = "HISTORY";       // HISTORY;playerFilter;from(yyyy-MM-dd);to(yyyy-MM-dd)

    public static final String CMD_LEAVE = "LEAVE";               // LEAVE (zur Lobby zurück, Verbindung bleibt)

    public static final String CMD_REMATCH = "REMATCH";           // REMATCH offer
    public static final String CMD_REMATCH_ACCEPT = "RACCEPT";    // accept
    public static final String CMD_REMATCH_DECLINE = "RDECL";     // decline

    public static final String CMD_QUIT = "QUIT";                 // QUIT connection close

    public static final String CMD_SPECTATE = "SPECTATE";          // SPECTATE;sessionId

    // Tournament Client -> Server
    public static final String CMD_HOST_TOURNAMENT = "THOST";      // THOST;name;maxPlayers
    public static final String CMD_JOIN_TOURNAMENT = "TJOIN";      // TJOIN;tournamentId
    public static final String CMD_START_TOURNAMENT = "TSTART";    // TSTART;tournamentId
    public static final String CMD_LIST_TOURNAMENTS = "TLIST";     // TLIST

    // Server -> Client
    public static final String SRV_WELCOME = "WELCOME";           // WELCOME;X|O
    public static final String SRV_MESSAGE = "MESSAGE";           // MESSAGE;text
    public static final String SRV_ERROR = "ERROR";               // ERROR;text

    public static final String SRV_ROOMS = "ROOMS";               // ROOMS;id|name|host|mode|timer,id|...
    public static final String SRV_HOSTED = "HOSTED";             // HOSTED;roomId
    public static final String SRV_START = "START";               // START;sessionId;mySymbol;opponent;mode;timerSec

    public static final String SRV_TURN = "TURN";                 // TURN;X|O
    public static final String SRV_VALID_MOVE = "VALID_MOVE";     // VALID_MOVE;row;col;symbol
    public static final String SRV_GAME_OVER = "GAME_OVER";       // GAME_OVER;X|O|D

    public static final String SRV_CHAT = "CHAT";                 // CHAT;from;msg

    public static final String SRV_REMATCH_OFFER = "ROFFER";      // ROFFER;from
    public static final String SRV_REMATCH_DECLINED = "RDECL";    // RDECL;reason
    public static final String SRV_OPPONENT_LEFT = "OLEFT";       // OLEFT

    public static final String SRV_SCOREBOARD = "SCOREBOARD";     // SCOREBOARD;name|w|l|d|elo,name|...

    public static final String SRV_HISTORY_LINE = "HLINE";        // HLINE;timestamp;X;O;winner;moves
    public static final String SRV_HISTORY_END = "HEND";          // HEND

    public static final String SRV_SPECTATOR_JOINED = "SJOIN";    // SJOIN;name
    public static final String SRV_SPECTATE_START = "SSTART";     // SSTART;sessionId;playerX;playerO

    // Tournament Server -> Client
    public static final String SRV_TOURNAMENTS = "TROOMS";        // TROOMS;id|name|host|max|current,...
    public static final String SRV_TOURNAMENT_HOSTED = "THOSTED"; // THOSTED;tournamentId
    public static final String SRV_TOURNAMENT_STARTED = "TSTARTED"; // TSTARTED;tournamentId
    public static final String SRV_TOURNAMENT_MATCH = "TMATCH";   // TMATCH;round;matchIndex;playerX;playerO
    public static final String SRV_TOURNAMENT_RESULT = "TRESULT"; // TRESULT;round;matchIndex;winner
    public static final String SRV_TOURNAMENT_OVER = "TOVER";     // TOVER;winnerName
    public static final String SRV_TOURNAMENT_MSG = "TMSG";       // TMSG;text
}
