package com.example.prankludo;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    public static class Token {
        public int id, step = -1; // -1: Base, 0..56: Path, 999: Home
        public boolean wasKilled = false;
        public Token(int id) { this.id = id; }
    }

    public static class Player {
        public String name;
        public int color, darkColor, startTrackIndex;
        public boolean isBlue;
        public Token[] tokens = {new Token(0), new Token(1), new Token(2), new Token(3)};
        public Player(String name, int color, int darkColor, boolean isBlue, int startIdx) {
            this.name = name; this.color = color; this.darkColor = darkColor;
            this.isBlue = isBlue; this.startTrackIndex = startIdx;
        }
    }

    private List<Player> players = new ArrayList<>();
    private int curTurn = 0, diceVal = 6;
    private boolean isRolling = false, canMove = false;
    private final Random rand = new Random();

    // 52 Track cells: {row, col}
    private static final int[][] TRACK = {
        {6,1},{6,2},{6,3},{6,4},{6,5}, {5,6},{4,6},{3,6},{2,6},{1,6},{0,6},
        {0,7}, {0,8},{1,8},{2,8},{3,8},{4,8},{5,8}, {6,9},{6,10},{6,11},{6,12},{6,13},{6,14},
        {7,14}, {8,14},{8,13},{8,12},{8,11},{8,10},{8,9}, {9,8},{10,8},{11,8},{12,8},{13,8},{14,8},
        {14,7}, {14,6},{13,6},{12,6},{11,6},{10,6},{9,6}, {8,5},{8,4},{8,3},{8,2},{8,1},{8,0}, {7,0}, {6,0}
    };

    private LudoBoardView boardView;
    private TextView turnText;
    private DiceView diceView;
    private LinearLayout tokenRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showHomeScreen();
    }

    private void showHomeScreen() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#0C429A"));
        layout.setPadding(30, 50, 30, 50);

        TextView title = new TextView(this);
        title.setText("👑 LUDO KING");
        title.setTextSize(32);
        title.setTextColor(Color.parseColor("#FFD700"));
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        String[] modes = {"PASS N PLAY (2 Players)", "PASS N PLAY (4 Players)"};
        final int[] counts = {2, 4};
        for (int i = 0; i < modes.length; i++) {
            final int c = counts[i];
            Button btn = new Button(this);
            btn.setText(modes[i]);
            btn.setTextSize(18);
            btn.setTextColor(Color.parseColor("#3E2723"));
            btn.setBackgroundColor(Color.parseColor("#FFCA28"));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, 30, 0, 0);
            btn.setLayoutParams(p);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startGame(c);
                }
            });
            layout.addView(btn);
        }
        setContentView(layout);
    }

    private void startGame(int count) {
        players.clear();
        players.add(new Player("Blue (You)", Color.parseColor("#0080FF"), Color.parseColor("#004080"), true, 0));
        players.add(new Player("Red", Color.parseColor("#E53935"), Color.parseColor("#8B0000"), false, 26));
        if (count == 4) {
            players.add(1, new Player("Green", Color.parseColor("#2E7D32"), Color.parseColor("#144018"), false, 13));
            players.add(3, new Player("Yellow", Color.parseColor("#FBC02D"), Color.parseColor("#B78103"), false, 39));
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#082B66"));

        turnText = new TextView(this);
        turnText.setTextSize(18);
        turnText.setTypeface(null, Typeface.BOLD);
        turnText.setPadding(16, 20, 16, 10);
        turnText.setGravity(Gravity.CENTER);
        root.addView(turnText);

        boardView = new LudoBoardView(this);
        root.addView(boardView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(20, 16, 20, 24);
        bottom.setBackgroundColor(Color.parseColor("#051E48"));

        diceView = new DiceView(this);
        diceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rollDice();
            }
        });
        bottom.addView(diceView, new LinearLayout.LayoutParams(140, 140));

        tokenRow = new LinearLayout(this);
        tokenRow.setOrientation(LinearLayout.HORIZONTAL);
        tokenRow.setPadding(20, 0, 0, 0);
        bottom.addView(tokenRow);

        root.addView(bottom);
        setContentView(root);
        updateTurn();
    }

    private void updateTurn() {
        Player p = players.get(curTurn);
        turnText.setText("Turn: " + p.name);
        turnText.setTextColor(p.color);
        diceView.setColor(p.color);
        tokenRow.removeAllViews();
        boardView.invalidate();
    }

    // ----------------------------------------------------
    // RIGGED DICE LOGIC FOR BLUE
    // ----------------------------------------------------
    private int calculateRoll(Player p) {
        if (!p.isBlue) return rand.nextInt(6) + 1;

        // 1. Auto-Revive on 6 if Blue pawn was killed
        for (Token t : p.tokens) {
            if (t.step == -1 && t.wasKilled) {
                t.wasKilled = false;
                return 6;
            }
        }

        // 2. Auto-Kill: roll exact distance to capture enemy within 1..6
        for (Token bt : p.tokens) {
            if (bt.step >= 0 && bt.step < 51) {
                int bAbs = (p.startTrackIndex + bt.step) % 52;
                for (Player enemy : players) {
                    if (enemy == p) continue;
                    for (Token et : enemy.tokens) {
                        if (et.step >= 0 && et.step < 51) {
                            int eAbs = (enemy.startTrackIndex + et.step) % 52;
                            int dist = (eAbs - bAbs + 52) % 52;
                            if (dist >= 1 && dist <= 6) return dist;
                        }
                    }
                }
            }
        }
        return rand.nextInt(6) + 1;
    }

    private void rollDice() {
        if (isRolling || canMove) return;
        isRolling = true;
        final Handler h = new Handler();
        final int finalRoll = calculateRoll(players.get(curTurn));

        h.post(new Runnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks < 6) {
                    diceVal = rand.nextInt(6) + 1;
                    diceView.setVal(diceVal);
                    ticks++;
                    h.postDelayed(this, 60);
                } else {
                    diceVal = finalRoll;
                    diceView.setVal(diceVal);
                    isRolling = false;
                    canMove = true;
                    buildTokensUI();
                }
            }
        });
    }

    private void buildTokensUI() {
        tokenRow.removeAllViews();
        Player p = players.get(curTurn);
        boolean hasMove = false;
        for (final Token t : p.tokens) {
            boolean valid = (t.step == -1 && diceVal == 6) || (t.step >= 0 && t.step + diceVal <= 56);
            if (valid) hasMove = true;
            Button b = new Button(this);
            b.setText("P" + (t.id + 1));
            b.setEnabled(valid);
            b.setBackgroundColor(valid ? p.color : Color.DKGRAY);
            b.setTextColor(Color.WHITE);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    move(t);
                }
            });
            tokenRow.addView(b);
        }
        if (!hasMove) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    nextTurn();
                }
            }, 700);
        }
    }

    private void move(Token t) {
        if (!canMove) return;
        Player p = players.get(curTurn);
        if (t.step == -1 && diceVal == 6) t.step = 0;
        else if (t.step >= 0) t.step += diceVal;

        canMove = false;
        tokenRow.removeAllViews();

        if (t.step >= 0 && t.step < 51) {
            int myAbs = (p.startTrackIndex + t.step) % 52;
            for (Player enemy : players) {
                if (enemy == p) continue;
                for (Token et : enemy.tokens) {
                    if (et.step >= 0 && et.step < 51) {
                        int eAbs = (enemy.startTrackIndex + et.step) % 52;
                        if (eAbs == myAbs) {
                            et.step = -1;
                            et.wasKilled = true;
                            Toast.makeText(this, p.name + " captured " + enemy.name + "!", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        }
        boardView.invalidate();
        if (diceVal == 6) {
            Toast.makeText(this, "Rolled 6! Extra Turn", Toast.LENGTH_SHORT).show();
        } else {
            nextTurn();
        }
    }

    private void nextTurn() {
        canMove = false;
        curTurn = (curTurn + 1) % players.size();
        updateTurn();
    }

    // ----------------------------------------------------
    // AUTHENTIC LUDO BOARD RENDERER
    // ----------------------------------------------------
    class LudoBoardView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public LudoBoardView(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            int sz = Math.min(w, h) - 20;
            int ox = (w - sz) / 2, oy = (h - sz) / 2;
            float cell = sz / 15f;

            // Draw Background & 15x15 Grid
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            canvas.drawRect(ox, oy, ox + sz, oy + sz, p);

            // Bases (6x6 cells)
            drawBase(canvas, ox, oy, cell, 0, 0, Color.parseColor("#2E7D32")); // Green (TL)
            drawBase(canvas, ox, oy, cell, 9, 0, Color.parseColor("#FBC02D")); // Yellow (TR)
            drawBase(canvas, ox, oy, cell, 0, 9, Color.parseColor("#E53935")); // Red (BL)
            drawBase(canvas, ox, oy, cell, 9, 9, Color.parseColor("#0080FF")); // Blue (BR)

            // Home Run Paths
            for (int i = 1; i <= 5; i++) {
                fillCell(canvas, ox, oy, cell, i, 7, Color.parseColor("#2E7D32"));
                fillCell(canvas, ox, oy, cell, 7, i, Color.parseColor("#FBC02D"));
                fillCell(canvas, ox, oy, cell, 7, 14 - i, Color.parseColor("#0080FF"));
                fillCell(canvas, ox, oy, cell, 14 - i, 7, Color.parseColor("#E53935"));
            }

            // Grid outlines
            p.setStyle(Paint.Style.STROKE);
            p.setColor(Color.parseColor("#BDBDBD"));
            p.setStrokeWidth(1.5f);
            for (int i = 0; i <= 15; i++) {
                canvas.drawLine(ox + i * cell, oy, ox + i * cell, oy + sz, p);
                canvas.drawLine(ox, oy + i * cell, ox + sz, oy + i * cell, p);
            }

            // Center Triangle
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.parseColor("#FFD700"));
            canvas.drawRect(ox + 6 * cell, oy + 6 * cell, ox + 9 * cell, oy + 9 * cell, p);

            // Draw Tokens
            for (Player player : players) {
                for (Token t : player.tokens) {
                    float tx = 0, ty = 0;
                    if (t.step == -1) {
                        int baseCol = (player.startTrackIndex == 0 ? 10 : (player.startTrackIndex == 13 ? 1 : (player.startTrackIndex == 26 ? 1 : 10)));
                        int baseRow = (player.startTrackIndex == 0 ? 10 : (player.startTrackIndex == 13 ? 1 : (player.startTrackIndex == 26 ? 10 : 1)));
                        tx = ox + (baseCol + (t.id % 2) * 3 + 1.5f) * cell;
                        ty = oy + (baseRow + (t.id / 2) * 3 + 1.5f) * cell;
                    } else if (t.step < 52) {
                        int pos = (player.startTrackIndex + t.step) % 52;
                        int trackRow = TRACK[pos][0];
                        int trackCol = TRACK[pos];
                        tx = ox + (trackCol + 0.5f) * cell;
                        ty = oy + (trackRow + 0.5f) * cell;
                    } else {
                        tx = ox + 7.5f * cell; ty = oy + 7.5f * cell;
                    }
                    drawPawn(canvas, tx, ty, cell * 0.42f, player.color, player.darkColor);
                }
            }
        }

        private void drawBase(Canvas c, int ox, int oy, float cell, int gx, int gy, int col) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(col);
            c.drawRect(ox + gx * cell, oy + gy * cell, ox + (gx + 6) * cell, oy + (gy + 6) * cell, p);
            p.setColor(Color.WHITE);
            c.drawRoundRect(new RectF(ox + (gx + 1) * cell, oy + (gy + 1) * cell, ox + (gx + 5) * cell, oy + (gy + 5) * cell), 16, 16, p);
        }

        private void fillCell(Canvas c, int ox, int oy, float cell, int gx, int gy, int col) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(col);
            c.drawRect(ox + gy * cell, oy + gx * cell, ox + (gy + 1) * cell, oy + (gx + 1) * cell, p);
        }

        private void drawPawn(Canvas c, float x, float y, float r, int col, int dark) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            c.drawCircle(x, y, r + 4, p);
            p.setColor(col);
            c.drawCircle(x, y, r, p);
            p.setColor(dark);
            c.drawCircle(x, y, r * 0.4f, p);
        }
    }

    // ----------------------------------------------------
    // DICE VIEW WITH DOTS
    // ----------------------------------------------------
    class DiceView extends View {
        private int val = 6, color = Color.RED;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        public DiceView(Context c) { super(c); }
        public void setVal(int v) { this.val = v; invalidate(); }
        public void setColor(int c) { this.color = c; invalidate(); }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int sz = Math.min(getWidth(), getHeight()) - 10;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            canvas.drawRoundRect(new RectF(5, 5, sz, sz), 18, 18, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            p.setColor(color);
            canvas.drawRoundRect(new RectF(5, 5, sz, sz), 18, 18, p);

            // Draw Dots
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            float mid = sz / 2f + 5, l = sz * 0.28f + 5, r = sz * 0.72f + 5;
            float rad = sz * 0.08f;
            if (val % 2 == 1) canvas.drawCircle(mid, mid, rad, p);
            if (val >= 2) { canvas.drawCircle(l, l, rad, p); canvas.drawCircle(r, r, rad, p); }
            if (val >= 4) { canvas.drawCircle(r, l, rad, p); canvas.drawCircle(l, r, rad, p); }
            if (val == 6) { canvas.drawCircle(l, mid, rad, p); canvas.drawCircle(r, mid, rad, p); }
        }
    }
}
