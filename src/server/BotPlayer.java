package server;

import java.util.*;

public class BotPlayer {

    private final Random rnd = new Random();

    public int[] pickMove(char[][] board, char me, char opp) {
        // win
        int[] w = findWinning(board, me);
        if (w != null) return w;
        // block
        int[] b = findWinning(board, opp);
        if (b != null) return b;
        // center
        if (board[1][1] == '\0') return new int[]{1, 1};
        // corners
        int[][] corners = {{0,0},{0,2},{2,0},{2,2}};
        List<int[]> freeCorners = new ArrayList<>();
        for (int[] c : corners) if (board[c[0]][c[1]] == '\0') freeCorners.add(c);
        if (!freeCorners.isEmpty()) return freeCorners.get(rnd.nextInt(freeCorners.size()));
        // any
        List<int[]> free = new ArrayList<>();
        for (int r=0;r<3;r++) for (int c=0;c<3;c++) if (board[r][c] == '\0') free.add(new int[]{r,c});
        return free.isEmpty() ? null : free.get(rnd.nextInt(free.size()));
    }

    private int[] findWinning(char[][] b, char s) {
        for (int r=0;r<3;r++) {
            int[] m = lineWin(b, s, r,0, r,1, r,2);
            if (m != null) return m;
        }
        for (int c=0;c<3;c++) {
            int[] m = lineWin(b, s, 0,c, 1,c, 2,c);
            if (m != null) return m;
        }
        int[] d1 = lineWin(b, s, 0,0, 1,1, 2,2);
        if (d1 != null) return d1;
        int[] d2 = lineWin(b, s, 0,2, 1,1, 2,0);
        return d2;
    }

    private int[] lineWin(char[][] b, char s, int r1,int c1,int r2,int c2,int r3,int c3) {
        char a=b[r1][c1], d=b[r2][c2], e=b[r3][c3];
        if (a==s && d==s && e=='\0') return new int[]{r3,c3};
        if (a==s && e==s && d=='\0') return new int[]{r2,c2};
        if (d==s && e==s && a=='\0') return new int[]{r1,c1};
        return null;
    }
}
