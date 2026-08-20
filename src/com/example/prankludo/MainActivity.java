package com.example.prankludo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends Activity {

    // Game state models
    public static class Token {
        public int id;
        public int position; // -1: Base, 0..51: Track, 999: Home
        public boolean wasJustKilled = false;

        public Token(int id) {
            this.id = id;
            this.position = -1;
        }
    }

    public static class Player {
        public String name;
        public int color;
        public boolean isRigged;
        public int startIndex;
        public Token[] tokens;

        public Player(String name, int color, boolean isRigged, int startIndex) {
            this.name = name;
            this.color = color;
            this.isRigged = isRigged;
            this.startIndex = startIndex;
            this.tokens = new Token[]{new Token(0), new Token(1), new Token(2), new Token(3)};
        }
    }

    private int playerCount = 4;
    private List<Player> players = new ArrayList<>();
    private int currentPlayerIndex = 0;
    private int diceValue = 1;
    private boolean isRolling = false;
    private boolean canMove = false;
    private final int trackLength = 52;
    private final Random random = new Random();

    private LinearLayout rootLayout;
    private LudoBoardView boardView;
    private TextView turnTextView;
    private Button diceButton;
    private LinearLayout tokenControlLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showPlayerSelectionScreen();
    }

    // ----------------------------------------------------
    // SCREEN 1: PLAYER SETUP (2, 4, 6, 8 Players)
    // ----------------------------------------------------
    private void showPlayerSelectionScreen() {
        LinearLayout menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setGravity(Gravity.CENTER);
        menuLayout.setPadding(40, 40, 40, 40);
        menuLayout.setBackgroundColor(Color.parseColor("#121212"));

        TextView titleView = new TextView(this);
        titleView.setText("Ludo Classic");
        titleView.setTextSize(28);
        titleView.setTextColor(Color.WHITE);
        titleView.setGravity(Gravity.CENTER);
        menuLayout.addView(titleView);

        TextView subTitleView = new TextView(this);
        subTitleView.setText("Select Number of Players\n(Blue player is active)");
        subTitleView.setTextSize(14);
        subTitleView.setTextColor(Color.LTGRAY);
        subTitleView.setGravity(Gravity.CENTER);
        subTitleView.setPadding(0, 20, 0, 40);
        menuLayout.addView(subTitleView);

        int[] options = {2, 4, 6, 8};
        for (final int count : options) {
            Button btn = new Button(this);
            btn.setText(count + " Players");
            btn.setTextSize(18);
            btn.setPadding(20, 20, 20, 20);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playerCount = count;
                    startLudoGame();
                }
            });
            menuLayout.addView(btn);
        }

        setContentView(menuLayout);
    }

    // ----------------------------------------------------
    // SCREEN 2: GAMEPLAY & RIGGED LOGIC
    // ----------------------------------------------------
    private void startLudoGame() {
        players.clear();
        int[] colorPalette = {
            Color.parseColor("#1E88E5"), // 0: Blue (Rigged)
            Color.parseColor("#E53935"), // 1: Red
            Color.parseColor("#43A047"), // 2: Green
            Color.parseColor("#FDD835"), // 3: Yellow
            Color.parseColor("#8E24AA"), // 4: Purple
            Color.parseColor("#FB8C00"), // 5: Orange
            Color.parseColor("#00ACC1"), // 6: Cyan
            Color.parseColor("#D81B60")  // 7: Pink
        };

        String[] colorNames = {"Blue", "Red", "Green", "Yellow", "Purple", "Orange", "Cyan", "Pink"};

        for (int i = 0; i < playerCount; i++) {
            boolean isBlue = (i == 0);
            int startStep = (i * (trackLength / playerCount));
            players.add(new Player(colorNames[i], colorPalette[i], isBlue, startStep));
        }

        currentPlayerIndex = 0;

        // Build UI
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.parseColor("#1A1A1A"));

        turnTextView = new TextView(this);
        turnTextView.setTextSize(20);
        turnTextView.setPadding(20, 30, 20, 20);
        turnTextView.setGravity(Gravity.CENTER);
        updateTurnDisplay();
        rootLayout.addView(turnTextView);

        boardView = new LudoBoardView(this);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        rootLayout.addView(boardView, boardParams);

        LinearLayout bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setPadding(20, 20, 20, 30);
        bottomPanel.setBackgroundColor(Color.parseColor("#262626"));

        diceButton = new Button(this);
        diceButton.setText("Roll Dice");
        diceButton.setTextSize(22);
        diceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rollDice();
            }
        });
        bottomPanel.addView(diceButton);

        tokenControlLayout = new LinearLayout(this);
        tokenControlLayout.setOrientation(LinearLayout.HORIZONTAL);
        tokenControlLayout.setGravity(Gravity.CENTER);
        tokenControlLayout.setPadding(0, 20, 0, 0);
        bottomPanel.addView(tokenControlLayout);

        rootLayout.addView(bottomPanel);
        setContentView(rootLayout);
    }

    private void updateTurnDisplay() {
        Player cur = players.get(currentPlayerIndex);
        turnTextView.setText("Turn: " + cur.name);
        turnTextView.setTextColor(cur.color);
    }

    // ----------------------------------------------------
    // INCONSPICUOUS RIGGED DICE CALCULATOR
    // ----------------------------------------------------
    private int computeRollForPlayer(Player player) {
        if (!player.isRigged) {
            // Normal Fair RNG
            return random.nextInt(6) + 1;
        }

        // --- BLUE CHEAT 1: Auto-Revive ---
        boolean hasTokenInBase = false;
        for (Token t : player.tokens) {
            if (t.position == -1) {
                hasTokenInBase = true;
                if (t.wasJustKilled) {
                    t.wasJustKilled = false;
                    return 6; // Guaranteed 6 after being killed
                }
            }
        }
        if (hasTokenInBase && random.nextInt(100) < 40) {
            return 6;
        }

        // --- BLUE CHEAT 2: Auto-Kill (Exact Roll to eliminate nearby enemy) ---
        for (Token blueToken : player.tokens) {
            if (blueToken.position >= 0 && blueToken.position < 999) {
                int blueAbs = (player.startIndex + blueToken.position) % trackLength;

                for (Player enemy : players) {
                    if (enemy == player) continue;
                    for (Token enemyToken : enemy.tokens) {
                        if (enemyToken.position >= 0 && enemyToken.position < 999) {
                            int enemyAbs = (enemy.startIndex + enemyToken.position) % trackLength;
                            int distance = (enemyAbs - blueAbs + trackLength) % trackLength;

                            if (distance >= 1 && distance <= 6) {
                                return distance; // Exact roll to kill enemy!
                            }
                        }
                    }
                }
            }
        }

        // Fallback: Normal random roll
        return random.nextInt(6) + 1;
    }

    private void rollDice() {
        if (isRolling || canMove) return;
        isRolling = true;

        final Handler handler = new Handler();
        final int finalValue = computeRollForPlayer(players.get(currentPlayerIndex));

        // Dice roll animation
        handler.post(new Runnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks < 6) {
                    diceValue = random.nextInt(6) + 1;
                    diceButton.setText("Rolling... " + diceValue);
                    ticks++;
                    handler.postDelayed(this, 70);
                } else {
                    diceValue = finalValue;
                    diceButton.setText("Rolled: " + diceValue);
                    isRolling = false;
                    canMove = true;
                    buildTokenButtons();
                }
            }
        });
    }

    private void buildTokenButtons() {
        tokenControlLayout.removeAllViews();
        Player cur = players.get(currentPlayerIndex);

        boolean hasAnyValidMove = false;
        for (final Token token : cur.tokens) {
            boolean isValid = (token.position == -1 && diceValue == 6) ||
                              (token.position >= 0 && token.position < 999);

            if (isValid) hasAnyValidMove = true;

            Button btn = new Button(this);
            String status = token.position == -1 ? "Base" : (token.position == 999 ? "Home" : "P" + token.position);
            btn.setText("T" + (token.id + 1) + "\n" + status);
            btn.setEnabled(canMove && isValid);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    moveToken(token);
                }
            });
            tokenControlLayout.addView(btn);
        }

        if (!hasAnyValidMove) {
            Toast.makeText(this, "No valid moves", Toast.LENGTH_SHORT).show();
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    nextTurn();
                }
            }, 800);
        }
    }

    private void moveToken(Token token) {
        if (!canMove) return;
        Player cur = players.get(currentPlayerIndex);

        if (token.position == -1 && diceValue == 6) {
            token.position = 0;
        } else if (token.position >= 0) {
            token.position += diceValue;
            if (token.position >= 56) {
                token.position = 999; // Reached Home
            }
        }

        canMove = false;
        tokenControlLayout.removeAllViews();
        checkCaptures(cur, token);
        boardView.invalidate();

        if (diceValue == 6) {
            diceButton.setText("Rolled 6! Roll Again");
        } else {
            nextTurn();
        }
    }

    private void checkCaptures(Player activePlayer, Token movedToken) {
        if (movedToken.position < 0 || movedToken.position >= 999) return;

        int activeAbs = (activePlayer.startIndex + movedToken.position) % trackLength;

        for (Player enemy : players) {
            if (enemy == activePlayer) continue;
            for (Token enemyToken : enemy.tokens) {
                if (enemyToken.position >= 0 && enemyToken.position < 999) {
                    int enemyAbs = (enemy.startIndex + enemyToken.position) % trackLength;
                    if (enemyAbs == activeAbs) {
                        enemyToken.position = -1;
                        enemyToken.wasJustKilled = true;
                        Toast.makeText(this, activePlayer.name + " killed " + enemy.name + "!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    private void nextTurn() {
        canMove = false;
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        updateTurnDisplay();
        diceButton.setText("Roll Dice");
        tokenControlLayout.removeAllViews();
        boardView.invalidate();
    }

    // ----------------------------------------------------
    // LUDO BOARD CUSTOM VIEW
    // ----------------------------------------------------
    private class LudoBoardView extends View {
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public LudoBoardView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            int boardSize = Math.min(width, height) - 40;
            int offsetX = (width - boardSize) / 2;
            int offsetY = (height - boardSize) / 2;

            // Draw Board Outline
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(4);
            canvas.drawRect(offsetX, offsetY, offsetX + boardSize, offsetY + boardSize, paint);

            // Draw Player Bases
            paint.setStyle(Paint.Style.FILL);
            int baseSize = (int)(boardSize * 0.35f);

            for (int i = 0; i < players.size(); i++) {
                Player p = players.get(i);
                paint.setColor(p.color);
                paint.setAlpha(120);

                int bx = offsetX + (i % 2 == 0 ? 10 : boardSize - baseSize - 10);
                int by = offsetY + (i < 2 ? 10 : boardSize - baseSize - 10);

                canvas.drawRoundRect(new RectF(bx, by, bx + baseSize, by + baseSize), 16, 16, paint);

                // Draw Tokens inside Base
                paint.setAlpha(255);
                for (int t = 0; t < p.tokens.length; t++) {
                    if (p.tokens[t].position == -1) {
                        float tx = bx + 40 + (t * 35);
                        float ty = by + baseSize / 2.0f;
                        canvas.drawCircle(tx, ty, 14, paint);
                    }
                }
            }
        }
    }
}