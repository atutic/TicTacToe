package server;

public class TicTacToeGame {

    private final char[][] board = new char[3][3];
    private char currentPlayer = 'X';

    public boolean makeMove(int row, int col, char player) {
        if (row < 0 || row > 2 || col < 0 || col > 2) return false;
        if (board[row][col] != '\0') return false;
        if (player != currentPlayer) return false;

        board[row][col] = player;
        currentPlayer = (currentPlayer == 'X') ? 'O' : 'X';
        return true;
    }

    public char getCurrentPlayer() {
        return currentPlayer;
    }

    // 'X'/'O' winner, 'D' draw, ' ' ongoing
    public char checkWinner() {
        for (int r=0;r<3;r++) {
            if (board[r][0] != '\0' && board[r][0]==board[r][1] && board[r][1]==board[r][2]) return board[r][0];
        }
        for (int c=0;c<3;c++) {
            if (board[0][c] != '\0' && board[0][c]==board[1][c] && board[1][c]==board[2][c]) return board[0][c];
        }
        if (board[0][0] != '\0' && board[0][0]==board[1][1] && board[1][1]==board[2][2]) return board[0][0];
        if (board[0][2] != '\0' && board[0][2]==board[1][1] && board[1][1]==board[2][0]) return board[0][2];

        boolean full = true;
        for (int r=0;r<3;r++) for (int c=0;c<3;c++) if (board[r][c] == '\0') full = false;
        return full ? 'D' : ' ';
    }

    public char[][] snapshot() {
        char[][] s = new char[3][3];
        for (int r=0;r<3;r++) {
            System.arraycopy(board[r], 0, s[r], 0, 3);
        }
        return s;
    }
}
