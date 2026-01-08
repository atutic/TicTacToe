package server;

public class TicTacToeGame {
    private char[][] board = new char[3][3];
    private char currentPlayer = 'X';

    public TicTacToeGame() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                board[i][j] = ' ';
            }
        }
    }

    public synchronized char getCurrentPlayer() {
        return currentPlayer;
    }

    public synchronized boolean makeMove(int x, int y, char player) {
        if (player != currentPlayer || x < 0 || x >= 3 || y < 0 || y >= 3 || board[x][y] != ' ') {
            return false; // Ungültiger Zug
        }
        board[x][y] = player;
        currentPlayer = (currentPlayer == 'X') ? 'O' : 'X';
        return true;
    }

    public synchronized char checkWinner() {
        // Reihen und Spalten prüfen
        for (int i = 0; i < 3; i++) {
            if (board[i][0] != ' ' && board[i][0] == board[i][1] && board[i][1] == board[i][2]) return board[i][0];
            if (board[0][i] != ' ' && board[0][i] == board[1][i] && board[1][i] == board[2][i]) return board[0][i];
        }
        // Diagonalen prüfen
        if (board[0][0] != ' ' && board[0][0] == board[1][1] && board[1][1] == board[2][2]) return board[0][0];
        if (board[0][2] != ' ' && board[0][2] == board[1][1] && board[1][1] == board[2][0]) return board[0][2];

        // Unentschieden prüfen
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[i][j] == ' ') return ' '; // Spiel läuft noch
            }
        }
        return 'D'; // Draw (Unentschieden)
    }
}
