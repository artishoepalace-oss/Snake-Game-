package com.anuj.snake2d;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public class MainActivity extends Activity {
    private SnakeView snakeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        snakeView = new SnakeView(this);
        setContentView(snakeView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (snakeView != null) snakeView.pauseForLifecycle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (snakeView != null) snakeView.resumeForLifecycle();
    }

    static class SnakeView extends View {
        private static final int GRID = 20;
        private static final int BG = Color.rgb(3, 18, 15);
        private static final int BG_2 = Color.rgb(7, 34, 27);
        private static final int TEXT = Color.rgb(242, 255, 248);
        private static final int MUTED = Color.rgb(159, 194, 178);
        private static final int GRID_LINE = Color.argb(90, 80, 160, 128);
        private static final int STROKE = Color.argb(145, 180, 255, 220);
        private static final int STROKE_STRONG = Color.argb(210, 115, 255, 181);
        private static final int SNAKE = Color.rgb(109, 246, 132);
        private static final int SNAKE_HEAD = Color.rgb(197, 255, 176);
        private static final int FOOD = Color.rgb(255, 102, 112);
        private static final int FOOD_HIGHLIGHT = Color.rgb(255, 214, 214);
        private static final int GREEN_GLOW = Color.argb(80, 95, 255, 180);
        private static final int PANEL_FILL = Color.argb(54, 31, 75, 57);
        private static final int PANEL_FILL_STRONG = Color.argb(82, 40, 98, 72);
        private static final int PANEL_FILL_DEEP = Color.argb(165, 12, 33, 27);
        private static final int BUTTON_FILL = Color.argb(76, 45, 112, 83);
        private static final int BUTTON_FILL_ACTIVE = Color.argb(118, 84, 255, 154);

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Random random = new Random();
        private final List<Point> snake = new ArrayList<>();
        private final Deque<Point> directionQueue = new ArrayDeque<>();
        private final SharedPreferences prefs;

        private final RectF hudRect = new RectF();
        private final RectF boardOuterRect = new RectF();
        private final RectF boardRect = new RectF();
        private final RectF pauseRect = new RectF();
        private final RectF restartRect = new RectF();
        private final RectF overlayRect = new RectF();
        private final RectF playAgainRect = new RectF();
        private final RectF controlsWrapRect = new RectF();
        private final RectF upRect = new RectF();
        private final RectF downRect = new RectF();
        private final RectF leftRect = new RectF();
        private final RectF rightRect = new RectF();
        private final RectF helpRect = new RectF();
        private final RectF leftTapZone = new RectF();
        private final RectF rightTapZone = new RectF();
        private final RectF topTapZone = new RectF();
        private final RectF bottomTapZone = new RectF();

        private Point food = new Point(5, 5);
        private int dx = 1;
        private int dy = 0;
        private int score = 0;
        private int highScore = 0;
        private boolean paused = false;
        private boolean gameOver = false;
        private boolean lifecyclePaused = false;
        private float boardLeft;
        private float boardTop;
        private float boardSize;
        private float cell;
        private float touchDownX;
        private float touchDownY;
        private float density;
        private long tickMs = 155;
        private RectF pressedRect = null;

        private final Runnable ticker = new Runnable() {
            @Override public void run() {
                if (!paused && !gameOver && !lifecyclePaused) {
                    step();
                }
                handler.postDelayed(this, tickMs);
            }
        };

        SnakeView(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            density = context.getResources().getDisplayMetrics().density;
            prefs = context.getSharedPreferences("snake_scores", Context.MODE_PRIVATE);
            highScore = prefs.getInt("high_score", 0);
            restartGame();
            handler.postDelayed(ticker, tickMs);
        }

        private float dp(float v) {
            return v * density;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            float side = dp(16);
            float top = dp(16);
            float hudH = dp(92);
            hudRect.set(side, top, w - side, top + hudH);

            float gap = dp(12);
            float controlsPad = dp(14);
            float controlsH = dp(124);
            controlsWrapRect.set(side, h - dp(210), w - side, h - dp(88));
            helpRect.set(side, controlsWrapRect.top - dp(32), w - side, controlsWrapRect.top - dp(8));

            float availableW = w - side * 2f;
            float availableH = controlsWrapRect.top - hudRect.bottom - gap * 2f;
            boardSize = Math.min(availableW, availableH);
            boardLeft = (w - boardSize) / 2f;
            boardTop = hudRect.bottom + gap;
            cell = boardSize / GRID;

            boardOuterRect.set(boardLeft - dp(4), boardTop - dp(4), boardLeft + boardSize + dp(4), boardTop + boardSize + dp(4));
            boardRect.set(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize);

            float btnW = dp(104);
            float btnH = dp(36);
            pauseRect.set(hudRect.left + dp(12), hudRect.bottom - dp(36) - dp(10), hudRect.left + dp(12) + btnW, hudRect.bottom - dp(10));
            restartRect.set(hudRect.right - dp(12) - btnW, hudRect.bottom - dp(36) - dp(10), hudRect.right - dp(12), hudRect.bottom - dp(10));

            float centerX = controlsWrapRect.centerX();
            float topBtn = dp(56);
            float smallGap = dp(10);
            float leftRightW = dp(92);
            float centerW = dp(96);
            float rowH = dp(58);
            upRect.set(centerX - dp(36), controlsWrapRect.top + dp(8), centerX + dp(36), controlsWrapRect.top + dp(8) + topBtn);
            float rowTop = upRect.bottom + smallGap;
            leftRect.set(centerX - centerW / 2f - smallGap - leftRightW, rowTop, centerX - centerW / 2f - smallGap, rowTop + rowH);
            downRect.set(centerX - centerW / 2f, rowTop, centerX + centerW / 2f, rowTop + rowH);
            rightRect.set(centerX + centerW / 2f + smallGap, rowTop, centerX + centerW / 2f + smallGap + leftRightW, rowTop + rowH);

            overlayRect.set(boardRect.left + boardSize * .10f, boardRect.top + boardSize * .34f,
                    boardRect.right - boardSize * .10f, boardRect.top + boardSize * .67f);
            playAgainRect.set(overlayRect.left + dp(28), overlayRect.bottom - dp(62), overlayRect.right - dp(28), overlayRect.bottom - dp(18));

            float tapMargin = cell * 1.5f;
            leftTapZone.set(boardRect.left, boardRect.top, boardRect.left + tapMargin, boardRect.bottom);
            rightTapZone.set(boardRect.right - tapMargin, boardRect.top, boardRect.right, boardRect.bottom);
            topTapZone.set(boardRect.left, boardRect.top, boardRect.right, boardRect.top + tapMargin);
            bottomTapZone.set(boardRect.left, boardRect.bottom - tapMargin, boardRect.right, boardRect.bottom);
        }

        private void restartGame() {
            snake.clear();
            snake.add(new Point(7, 10));
            snake.add(new Point(6, 10));
            snake.add(new Point(5, 10));
            snake.add(new Point(4, 10));
            dx = 1;
            dy = 0;
            directionQueue.clear();
            score = 0;
            tickMs = 155;
            paused = false;
            gameOver = false;
            lifecyclePaused = false;
            spawnFood();
            invalidate();
        }

        private void spawnFood() {
            for (int i = 0; i < 1000; i++) {
                Point candidate = new Point(random.nextInt(GRID), random.nextInt(GRID));
                boolean occupied = false;
                for (Point s : snake) {
                    if (s.x == candidate.x && s.y == candidate.y) {
                        occupied = true;
                        break;
                    }
                }
                if (!occupied) {
                    food = candidate;
                    return;
                }
            }
            food = new Point(0, 0);
        }

        private boolean hitsSnake(Point p) {
            for (Point s : snake) {
                if (s.x == p.x && s.y == p.y) return true;
            }
            return false;
        }

        private void queueDirection(int ndx, int ndy) {
            if (gameOver) return;
            int baseDx = dx;
            int baseDy = dy;
            if (!directionQueue.isEmpty()) {
                Point last = ((ArrayDeque<Point>) directionQueue).peekLast();
                if (last != null) {
                    baseDx = last.x;
                    baseDy = last.y;
                }
            }
            if (ndx == baseDx && ndy == baseDy) return;
            if (ndx == -baseDx && ndy == -baseDy) return;
            if (directionQueue.size() >= 2) return;
            directionQueue.offerLast(new Point(ndx, ndy));
            if (paused) paused = false;
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }

        private void consumeQueuedDirection() {
            if (!directionQueue.isEmpty()) {
                Point nextDir = directionQueue.pollFirst();
                if (nextDir != null && !(nextDir.x == -dx && nextDir.y == -dy)) {
                    dx = nextDir.x;
                    dy = nextDir.y;
                }
            }
        }

        private void step() {
            consumeQueuedDirection();
            Point head = snake.get(0);
            Point next = new Point(head.x + dx, head.y + dy);

            Point tail = snake.get(snake.size() - 1);
            boolean growing = next.x == food.x && next.y == food.y;
            boolean hit = false;
            if (next.x < 0 || next.y < 0 || next.x >= GRID || next.y >= GRID) {
                hit = true;
            } else {
                for (int i = 0; i < snake.size(); i++) {
                    Point s = snake.get(i);
                    boolean isTailThatWillMove = !growing && i == snake.size() - 1 && s.x == tail.x && s.y == tail.y;
                    if (!isTailThatWillMove && s.x == next.x && s.y == next.y) {
                        hit = true;
                        break;
                    }
                }
            }

            if (hit) {
                gameOver = true;
                if (score > highScore) {
                    highScore = score;
                    prefs.edit().putInt("high_score", highScore).apply();
                }
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                invalidate();
                return;
            }

            snake.add(0, next);
            if (growing) {
                score += 10;
                if (score > highScore) highScore = score;
                prefs.edit().putInt("high_score", highScore).apply();
                tickMs = Math.max(70, 155 - (score / 30) * 4L);
                spawnFood();
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            } else {
                snake.remove(snake.size() - 1);
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            drawBackground(c);
            drawGlassPanel(c, hudRect, dp(22), PANEL_FILL, true, true);
            drawHud(c);
            drawBoard(c);
            drawHelp(c);
            drawControls(c);
            drawFooter(c);
            if (paused || gameOver) drawOverlay(c);
        }

        private void drawBackground(Canvas c) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(BG);
            c.drawRect(0, 0, getWidth(), getHeight(), paint);

            RadialGradient rg1 = new RadialGradient(getWidth() * 0.78f, getHeight() * 0.28f, getWidth() * 0.72f,
                    new int[]{Color.argb(85, 53, 179, 110), Color.argb(10, 53, 179, 110), Color.TRANSPARENT},
                    new float[]{0f, .45f, 1f}, Shader.TileMode.CLAMP);
            paint.setShader(rg1);
            c.drawRect(0, 0, getWidth(), getHeight(), paint);

            RadialGradient rg2 = new RadialGradient(getWidth() * 0.2f, getHeight() * 0.86f, getWidth() * 0.8f,
                    new int[]{Color.argb(72, 10, 80, 64), Color.argb(8, 10, 80, 64), Color.TRANSPARENT},
                    new float[]{0f, .42f, 1f}, Shader.TileMode.CLAMP);
            paint.setShader(rg2);
            c.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);
        }

        private void drawHud(Canvas c) {
            drawText(c, "SNAKE", hudRect.left + dp(16), hudRect.top + dp(28), dp(19), TEXT, true, Paint.Align.LEFT);
            drawText(c, "LIQUID 2D", hudRect.left + dp(16), hudRect.top + dp(46), dp(10), SNAKE, true, Paint.Align.LEFT);

            float col1 = hudRect.centerX();
            drawText(c, String.valueOf(score), col1, hudRect.top + dp(34), dp(21), TEXT, true, Paint.Align.CENTER);
            drawText(c, "SCORE", col1, hudRect.top + dp(52), dp(9.5f), MUTED, true, Paint.Align.CENTER);

            drawText(c, String.valueOf(highScore), hudRect.right - dp(18), hudRect.top + dp(34), dp(21), TEXT, true, Paint.Align.RIGHT);
            drawText(c, "BEST", hudRect.right - dp(18), hudRect.top + dp(52), dp(9.5f), MUTED, true, Paint.Align.RIGHT);

            drawButton(c, pauseRect, paused ? "▶ PLAY" : "❚❚ PAUSE", pressedRect == pauseRect, false);
            drawButton(c, restartRect, "↻ RESTART", pressedRect == restartRect, false);
        }

        private void drawBoard(Canvas c) {
            drawGlassPanel(c, boardOuterRect, dp(24), PANEL_FILL_STRONG, true, true);
            paint.setColor(Color.argb(120, 2, 16, 12));
            paint.setStyle(Paint.Style.FILL);
            c.drawRoundRect(boardRect, dp(19), dp(19), paint);

            paint.setColor(GRID_LINE);
            paint.setStrokeWidth(dp(.9f));
            for (int i = 1; i < GRID; i++) {
                float x = boardLeft + i * cell;
                float y = boardTop + i * cell;
                c.drawLine(x, boardTop, x, boardTop + boardSize, paint);
                c.drawLine(boardLeft, y, boardLeft + boardSize, y, paint);
            }

            float inset = Math.max(dp(1.8f), cell * .13f);
            for (int i = snake.size() - 1; i >= 0; i--) {
                Point s = snake.get(i);
                float l = boardLeft + s.x * cell + inset;
                float t = boardTop + s.y * cell + inset;
                float r = boardLeft + (s.x + 1) * cell - inset;
                float b = boardTop + (s.y + 1) * cell - inset;
                RectF rect = new RectF(l, t, r, b);
                int fill = i == 0 ? SNAKE_HEAD : SNAKE;
                drawSnakeSegment(c, rect, fill, i == 0);
            }

            float fx = boardLeft + (food.x + .5f) * cell;
            float fy = boardTop + (food.y + .5f) * cell;
            paint.setShader(new RadialGradient(fx, fy, cell * .72f,
                    new int[]{Color.argb(110, 255, 92, 92), Color.argb(20, 255, 92, 92), Color.TRANSPARENT},
                    new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(fx, fy, cell * .68f, paint);
            paint.setShader(null);
            paint.setColor(FOOD);
            c.drawCircle(fx, fy, cell * .28f, paint);
            paint.setColor(FOOD_HIGHLIGHT);
            c.drawCircle(fx - cell * .09f, fy - cell * .09f, cell * .06f, paint);
        }

        private void drawHelp(Canvas c) {
            drawText(c, "Swipe anywhere • Tap board edges • Use D-pad", helpRect.centerX(), helpRect.centerY() + dp(4), dp(11), MUTED, false, Paint.Align.CENTER);
        }

        private void drawControls(Canvas c) {
            drawGlassPanel(c, controlsWrapRect, dp(28), PANEL_FILL, true, false);
            drawButton(c, upRect, "▲", pressedRect == upRect, true);
            drawButton(c, leftRect, "◀", pressedRect == leftRect, true);
            drawButton(c, downRect, "▼", pressedRect == downRect, true);
            drawButton(c, rightRect, "▶", pressedRect == rightRect, true);
        }

        private void drawFooter(Canvas c) {
            drawText(c, "v1.0.0.2 • EASY CONTROL • LIQUID GLASS", getWidth() / 2f, getHeight() - dp(22), dp(10), Color.argb(155, 188, 225, 205), false, Paint.Align.CENTER);
        }

        private void drawOverlay(Canvas c) {
            drawGlassPanel(c, overlayRect, dp(24), PANEL_FILL_DEEP, true, true);
            float cx = overlayRect.centerX();
            if (gameOver) {
                drawText(c, "GAME OVER", cx, overlayRect.top + dp(52), dp(23), TEXT, true, Paint.Align.CENTER);
                drawText(c, "Score " + score + "  •  Best " + highScore, cx, overlayRect.top + dp(80), dp(14), MUTED, false, Paint.Align.CENTER);
                drawPrimaryButton(c, playAgainRect, "PLAY AGAIN", pressedRect == playAgainRect);
            } else {
                drawText(c, "PAUSED", cx, overlayRect.top + dp(56), dp(22), TEXT, true, Paint.Align.CENTER);
                drawText(c, "Tap PLAY or swipe to continue", cx, overlayRect.top + dp(88), dp(14), MUTED, false, Paint.Align.CENTER);
                drawPrimaryButton(c, playAgainRect, "RESUME", pressedRect == playAgainRect);
            }
        }

        private void drawSnakeSegment(Canvas c, RectF rect, int fill, boolean head) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    head ? new int[]{SNAKE_HEAD, Color.rgb(141, 255, 149)} : new int[]{fill, Color.rgb(76, 218, 115)},
                    null, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, cell * .28f, cell * .28f, paint);
            paint.setShader(null);

            paint.setColor(GREEN_GLOW);
            c.drawRoundRect(new RectF(rect.left - dp(1.2f), rect.top - dp(1.2f), rect.right + dp(1.2f), rect.bottom + dp(1.2f)), cell * .34f, cell * .34f, paint);

            paint.setColor(Color.argb(110, 255, 255, 255));
            RectF highlight = new RectF(rect.left + rect.width() * .12f, rect.top + rect.height() * .12f, rect.right - rect.width() * .18f, rect.top + rect.height() * .36f);
            c.drawRoundRect(highlight, cell * .20f, cell * .20f, paint);
        }

        private void drawGlassPanel(Canvas c, RectF rect, float radius, int baseFill, boolean stroke, boolean topHighlight) {
            paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    new int[]{Color.argb(94, 42, 98, 72), baseFill, Color.argb(64, 18, 47, 36)},
                    new float[]{0f, .52f, 1f}, Shader.TileMode.CLAMP));
            paint.setStyle(Paint.Style.FILL);
            c.drawRoundRect(rect, radius, radius, paint);
            paint.setShader(null);

            RectF soft = new RectF(rect.left + dp(2), rect.top + dp(2), rect.right - dp(2), rect.bottom - dp(2));
            paint.setShader(new LinearGradient(soft.left, soft.top, soft.left, soft.bottom,
                    new int[]{Color.argb(54, 255, 255, 255), Color.argb(8, 255, 255, 255), Color.argb(24, 255, 255, 255)},
                    new float[]{0f, .32f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(soft, radius - dp(2), radius - dp(2), paint);
            paint.setShader(null);

            if (topHighlight) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.8f));
                paint.setColor(Color.argb(120, 255, 255, 255));
                c.drawArc(new RectF(rect.left + dp(8), rect.top - dp(14), rect.right - dp(18), rect.top + rect.height() * .45f), 196f, 104f, false, paint);
                paint.setStrokeWidth(dp(1f));
                paint.setColor(Color.argb(64, 255, 255, 255));
                c.drawArc(new RectF(rect.left + dp(28), rect.top + dp(4), rect.right - dp(56), rect.top + rect.height() * .60f), 192f, 74f, false, paint);
            }

            if (stroke) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.25f));
                paint.setColor(STROKE);
                c.drawRoundRect(rect, radius, radius, paint);
            }

            paint.setStyle(Paint.Style.FILL);
        }

        private void drawButton(Canvas c, RectF rect, String label, boolean pressed, boolean large) {
            int fill = pressed ? BUTTON_FILL_ACTIVE : BUTTON_FILL;
            float radius = large ? dp(22) : dp(18);
            drawGlassPanel(c, rect, radius, fill, true, true);
            drawText(c, label, rect.centerX(), rect.centerY() + dp(large ? 5 : 4), large ? dp(22) : dp(12), TEXT, true, Paint.Align.CENTER);
        }

        private void drawPrimaryButton(Canvas c, RectF rect, String label, boolean pressed) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    pressed ? new int[]{Color.rgb(180, 255, 179), Color.rgb(75, 240, 126)} : new int[]{Color.rgb(138, 255, 154), Color.rgb(77, 233, 117)},
                    null, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, dp(22), dp(22), paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.4f));
            paint.setColor(Color.argb(110, 255, 255, 255));
            c.drawRoundRect(rect, dp(22), dp(22), paint);
            drawText(c, label, rect.centerX(), rect.centerY() + dp(4), dp(13), Color.rgb(6, 42, 23), true, Paint.Align.CENTER);
        }

        private void drawText(Canvas c, String text, float x, float y, float size, int color, boolean bold, Paint.Align align) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTextAlign(align);
            paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
            c.drawText(text, x, y, paint);
        }

        private RectF findPressedTarget(float x, float y) {
            if (pauseRect.contains(x, y)) return pauseRect;
            if (restartRect.contains(x, y)) return restartRect;
            if ((gameOver || paused) && playAgainRect.contains(x, y)) return playAgainRect;
            if (upRect.contains(x, y)) return upRect;
            if (downRect.contains(x, y)) return downRect;
            if (leftRect.contains(x, y)) return leftRect;
            if (rightRect.contains(x, y)) return rightRect;
            return null;
        }

        private boolean handleTapDirection(float x, float y) {
            if (leftRect.contains(x, y) || leftTapZone.contains(x, y)) {
                queueDirection(-1, 0);
                return true;
            }
            if (rightRect.contains(x, y) || rightTapZone.contains(x, y)) {
                queueDirection(1, 0);
                return true;
            }
            if (upRect.contains(x, y) || topTapZone.contains(x, y)) {
                queueDirection(0, -1);
                return true;
            }
            if (downRect.contains(x, y) || bottomTapZone.contains(x, y)) {
                queueDirection(0, 1);
                return true;
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchDownX = x;
                    touchDownY = y;
                    pressedRect = findPressedTarget(x, y);
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (pressedRect != null) {
                        RectF next = findPressedTarget(x, y);
                        if (next != pressedRect) {
                            pressedRect = next;
                            invalidate();
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    RectF releasedTarget = findPressedTarget(x, y);
                    float sx = x - touchDownX;
                    float sy = y - touchDownY;
                    float absX = Math.abs(sx);
                    float absY = Math.abs(sy);

                    if (releasedTarget == pauseRect) {
                        if (!gameOver) paused = !paused;
                        pressedRect = null;
                        invalidate();
                        return true;
                    }
                    if (releasedTarget == restartRect) {
                        pressedRect = null;
                        restartGame();
                        return true;
                    }
                    if (releasedTarget == playAgainRect) {
                        if (gameOver) restartGame();
                        else paused = false;
                        pressedRect = null;
                        invalidate();
                        return true;
                    }

                    if (handleTapDirection(x, y)) {
                        pressedRect = null;
                        invalidate();
                        return true;
                    }

                    if (Math.max(absX, absY) > dp(24)) {
                        if (absX > absY) queueDirection(sx > 0 ? 1 : -1, 0);
                        else queueDirection(0, sy > 0 ? 1 : -1);
                    }

                    if (gameOver && overlayRect.contains(x, y)) {
                        restartGame();
                        return true;
                    }

                    if (paused && boardRect.contains(x, y)) {
                        paused = false;
                    }
                    pressedRect = null;
                    invalidate();
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    pressedRect = null;
                    invalidate();
                    return true;
            }
            return true;
        }

        void pauseForLifecycle() {
            lifecyclePaused = true;
            if (!gameOver) paused = true;
            invalidate();
        }

        void resumeForLifecycle() {
            lifecyclePaused = false;
            invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            handler.removeCallbacks(ticker);
            super.onDetachedFromWindow();
        }
    }
}
