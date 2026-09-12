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

import java.util.ArrayList;
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

        private static final int BG_TOP = Color.rgb(4, 16, 12);
        private static final int BG_BOTTOM = Color.rgb(7, 25, 18);
        private static final int BOARD_BG = Color.rgb(4, 18, 13);
        private static final int GRID_LINE = Color.argb(72, 76, 155, 112);
        private static final int SNAKE = Color.rgb(102, 245, 125);
        private static final int SNAKE_HEAD = Color.rgb(206, 255, 126);
        private static final int FOOD = Color.rgb(255, 92, 107);
        private static final int TEXT = Color.rgb(244, 255, 248);
        private static final int MUTED = Color.rgb(152, 188, 166);
        private static final int ACCENT = Color.rgb(83, 238, 132);
        private static final int ACCENT_BRIGHT = Color.rgb(169, 255, 173);

        private static final int PRESS_NONE = 0;
        private static final int PRESS_PAUSE = 1;
        private static final int PRESS_RESTART = 2;
        private static final int PRESS_UP = 3;
        private static final int PRESS_DOWN = 4;
        private static final int PRESS_LEFT = 5;
        private static final int PRESS_RIGHT = 6;
        private static final int PRESS_PLAY_AGAIN = 7;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Random random = new Random();
        private final List<Point> snake = new ArrayList<>();
        private final SharedPreferences prefs;

        private final RectF hudRect = new RectF();
        private final RectF pauseRect = new RectF();
        private final RectF restartRect = new RectF();
        private final RectF upRect = new RectF();
        private final RectF downRect = new RectF();
        private final RectF leftRect = new RectF();
        private final RectF rightRect = new RectF();
        private final RectF playAgainRect = new RectF();

        private Point food = new Point(5, 5);
        private int dx = 1, dy = 0;
        private int pendingDx = 1, pendingDy = 0;
        private int score = 0;
        private int highScore = 0;
        private boolean paused = false;
        private boolean gameOver = false;
        private boolean lifecyclePaused = false;
        private float boardLeft, boardTop, boardSize, cell;
        private float touchDownX, touchDownY;
        private long tickMs = 145;
        private int pressed = PRESS_NONE;

        private final Runnable ticker = new Runnable() {
            @Override public void run() {
                if (!paused && !gameOver && !lifecyclePaused) step();
                handler.postDelayed(this, tickMs);
            }
        };

        SnakeView(Context context) {
            super(context);
            setFocusable(true);
            setBackgroundColor(BG_TOP);
            prefs = context.getSharedPreferences("snake_scores", Context.MODE_PRIVATE);
            highScore = prefs.getInt("high_score", 0);
            restartGame();
            handler.postDelayed(ticker, tickMs);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            float margin = 16f;
            hudRect.set(margin, 16f, w - margin, 124f);

            boardTop = 142f;
            float maxByWidth = w - 24f;
            float maxByHeight = Math.max(280f, h - boardTop - 222f);
            boardSize = Math.min(maxByWidth, maxByHeight);
            boardLeft = (w - boardSize) / 2f;
            cell = boardSize / GRID;

            pauseRect.set(hudRect.left + 12f, 82f, hudRect.left + 102f, 114f);
            restartRect.set(hudRect.right - 110f, 82f, hudRect.right - 12f, 114f);

            float controlsTop = boardTop + boardSize + 22f;
            float btn = Math.min(68f, (w - 100f) / 4f);
            float gap = 8f;
            float centerX = w / 2f;

            upRect.set(centerX - btn / 2f, controlsTop, centerX + btn / 2f, controlsTop + btn);
            float rowTop = controlsTop + btn + gap;
            leftRect.set(centerX - 1.5f * btn, rowTop, centerX - .5f * btn, rowTop + btn);
            downRect.set(centerX - .5f * btn, rowTop, centerX + .5f * btn, rowTop + btn);
            rightRect.set(centerX + .5f * btn, rowTop, centerX + 1.5f * btn, rowTop + btn);
        }

        private void restartGame() {
            snake.clear();
            snake.add(new Point(8, 10));
            snake.add(new Point(7, 10));
            snake.add(new Point(6, 10));
            snake.add(new Point(5, 10));
            dx = pendingDx = 1;
            dy = pendingDy = 0;
            score = 0;
            tickMs = 145;
            paused = false;
            gameOver = false;
            lifecyclePaused = false;
            pressed = PRESS_NONE;
            spawnFood();
            invalidate();
        }

        private void spawnFood() {
            for (int tries = 0; tries < 1000; tries++) {
                Point p = new Point(random.nextInt(GRID), random.nextInt(GRID));
                boolean occupied = false;
                for (Point s : snake) {
                    if (s.x == p.x && s.y == p.y) {
                        occupied = true;
                        break;
                    }
                }
                if (!occupied) {
                    food = p;
                    return;
                }
            }
        }

        private void step() {
            if (!(pendingDx == -dx && pendingDy == -dy)) {
                dx = pendingDx;
                dy = pendingDy;
            }

            Point head = snake.get(0);
            Point next = new Point(head.x + dx, head.y + dy);
            boolean eating = next.x == food.x && next.y == food.y;

            if (next.x < 0 || next.y < 0 || next.x >= GRID || next.y >= GRID || hitsSnake(next, eating)) {
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
            if (eating) {
                score += 10;
                if (score > highScore) highScore = score;
                tickMs = Math.max(68, 145 - (score / 40) * 6L);
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                spawnFood();
            } else {
                snake.remove(snake.size() - 1);
            }
            invalidate();
        }

        private boolean hitsSnake(Point p, boolean eating) {
            int limit = snake.size() - (eating ? 0 : 1);
            for (int i = 0; i < limit; i++) {
                Point s = snake.get(i);
                if (s.x == p.x && s.y == p.y) return true;
            }
            return false;
        }

        private void setDirection(int ndx, int ndy) {
            if (gameOver) return;
            if (ndx == -dx && ndy == -dy) return;
            pendingDx = ndx;
            pendingDy = ndy;
            if (paused) paused = false;
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            drawBackground(c);
            drawHud(c);
            drawBoard(c);
            drawControls(c);
            drawText(c, "v1.0.0.1  •  LIQUID GLASS", getWidth() / 2f, getHeight() - 28f,
                    10.5f, Color.argb(125, 190, 225, 202), false, Paint.Align.CENTER);

            if (paused || gameOver) drawOverlay(c);
        }

        private void drawBackground(Canvas c) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, 0, 0, getHeight(), BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);

            paint.setShader(new RadialGradient(getWidth() * .18f, getHeight() * .18f,
                    getWidth() * .72f,
                    new int[]{Color.argb(58, 54, 201, 116), Color.argb(16, 40, 126, 79), Color.TRANSPARENT},
                    new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(getWidth() * .18f, getHeight() * .18f, getWidth() * .72f, paint);
            paint.setShader(null);

            paint.setShader(new RadialGradient(getWidth() * .88f, getHeight() * .58f,
                    getWidth() * .56f,
                    new int[]{Color.argb(34, 45, 159, 111), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP));
            c.drawCircle(getWidth() * .88f, getHeight() * .58f, getWidth() * .56f, paint);
            paint.setShader(null);
        }

        private void drawHud(Canvas c) {
            drawGlassPanel(c, hudRect, 28f, 48, true);

            drawText(c, "SNAKE", hudRect.left + 18f, 49f, 25f, TEXT, true, Paint.Align.LEFT);
            drawText(c, "LIQUID 2D", hudRect.left + 18f, 70f, 11.5f, ACCENT, true, Paint.Align.LEFT);

            drawText(c, String.valueOf(score), hudRect.centerX(), 48f, 24f, TEXT, true, Paint.Align.CENTER);
            drawText(c, "SCORE", hudRect.centerX(), 68f, 10f, MUTED, true, Paint.Align.CENTER);

            drawText(c, String.valueOf(highScore), hudRect.right - 18f, 48f, 22f, TEXT, true, Paint.Align.RIGHT);
            drawText(c, "BEST", hudRect.right - 18f, 68f, 10f, MUTED, true, Paint.Align.RIGHT);

            drawGlassButton(c, pauseRect, paused ? "▶  PLAY" : "Ⅱ  PAUSE", pressed == PRESS_PAUSE, 11.5f);
            drawGlassButton(c, restartRect, "↻  RESTART", pressed == PRESS_RESTART, 11.5f);
        }

        private void drawBoard(Canvas c) {
            RectF outer = new RectF(boardLeft - 7f, boardTop - 7f,
                    boardLeft + boardSize + 7f, boardTop + boardSize + 7f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(78, 0, 0, 0));
            c.drawRoundRect(new RectF(outer.left + 2f, outer.top + 7f, outer.right + 2f, outer.bottom + 7f),
                    25f, 25f, paint);
            drawGlassPanel(c, outer, 24f, 34, false);

            RectF board = new RectF(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(242, Color.red(BOARD_BG), Color.green(BOARD_BG), Color.blue(BOARD_BG)));
            c.drawRoundRect(board, 18f, 18f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(GRID_LINE);
            for (int i = 1; i < GRID; i++) {
                float x = boardLeft + i * cell;
                float y = boardTop + i * cell;
                c.drawLine(x, boardTop + 1f, x, boardTop + boardSize - 1f, paint);
                c.drawLine(boardLeft + 1f, y, boardLeft + boardSize - 1f, y, paint);
            }
            paint.setStyle(Paint.Style.FILL);

            float inset = Math.max(2.2f, cell * .13f);
            for (int i = snake.size() - 1; i >= 0; i--) {
                Point s = snake.get(i);
                float l = boardLeft + s.x * cell + inset;
                float t = boardTop + s.y * cell + inset;
                float r = boardLeft + (s.x + 1) * cell - inset;
                float b = boardTop + (s.y + 1) * cell - inset;
                RectF seg = new RectF(l, t, r, b);

                int base = i == 0 ? SNAKE_HEAD : SNAKE;
                paint.setShader(new LinearGradient(l, t, r, b,
                        lighten(base, 34), base, Shader.TileMode.CLAMP));
                c.drawRoundRect(seg, cell * .3f, cell * .3f, paint);
                paint.setShader(null);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1f);
                paint.setColor(Color.argb(i == 0 ? 145 : 92, 232, 255, 222));
                c.drawRoundRect(seg, cell * .3f, cell * .3f, paint);
                paint.setStyle(Paint.Style.FILL);
            }

            float fx = boardLeft + (food.x + .5f) * cell;
            float fy = boardTop + (food.y + .5f) * cell;
            paint.setShader(new RadialGradient(fx, fy, cell * .75f,
                    new int[]{Color.argb(92, 255, 80, 100), Color.argb(20, 255, 80, 100), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP));
            c.drawCircle(fx, fy, cell * .75f, paint);
            paint.setShader(null);
            paint.setColor(FOOD);
            c.drawCircle(fx, fy, cell * .32f, paint);
            paint.setColor(Color.rgb(255, 226, 230));
            c.drawCircle(fx - cell * .10f, fy - cell * .11f, cell * .065f, paint);
        }

        private void drawControls(Canvas c) {
            drawGlassButton(c, upRect, "▲", pressed == PRESS_UP, 22f);

            RectF rowShell = new RectF(leftRect.left - 5f, leftRect.top - 5f,
                    rightRect.right + 5f, rightRect.bottom + 5f);
            drawGlassPanel(c, rowShell, 24f, 39, true);

            drawSegment(c, leftRect, "◀", pressed == PRESS_LEFT, true, false);
            drawSegment(c, downRect, "▼", pressed == PRESS_DOWN, false, false);
            drawSegment(c, rightRect, "▶", pressed == PRESS_RIGHT, false, true);
        }

        private void drawSegment(Canvas c, RectF rect, String label, boolean isPressed,
                                 boolean leftEdge, boolean rightEdge) {
            if (isPressed) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(55, 144, 255, 174));
                c.drawRoundRect(rect, leftEdge || rightEdge ? 20f : 9f, leftEdge || rightEdge ? 20f : 9f, paint);
            }

            if (!rightEdge) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1f);
                paint.setColor(Color.argb(36, 228, 255, 237));
                c.drawLine(rect.right, rect.top + 12f, rect.right, rect.bottom - 12f, paint);
            }
            drawText(c, label, rect.centerX(), verticallyCenteredY(rect), 21f,
                    isPressed ? ACCENT_BRIGHT : TEXT, true, Paint.Align.CENTER);
        }

        private void drawOverlay(Canvas c) {
            RectF board = new RectF(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(108, 0, 8, 5));
            c.drawRoundRect(board, 18f, 18f, paint);

            float boxWidth = boardSize * .78f;
            float boxHeight = Math.min(220f, boardSize * .36f);
            RectF box = new RectF(boardLeft + (boardSize - boxWidth) / 2f,
                    boardTop + (boardSize - boxHeight) / 2f,
                    boardLeft + (boardSize + boxWidth) / 2f,
                    boardTop + (boardSize + boxHeight) / 2f);
            drawGlassPanel(c, box, 30f, 84, true);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.4f);
            paint.setColor(Color.argb(155, 101, 244, 143));
            c.drawRoundRect(box, 30f, 30f, paint);
            paint.setStyle(Paint.Style.FILL);

            float cx = box.centerX();
            if (gameOver) {
                drawText(c, "GAME OVER", cx, box.top + 54f, 27f, TEXT, true, Paint.Align.CENTER);
                drawText(c, "Score  " + score + "    •    Best  " + highScore,
                        cx, box.top + 83f, 14.5f, MUTED, false, Paint.Align.CENTER);
                playAgainRect.set(cx - 86f, box.bottom - 58f, cx + 86f, box.bottom - 18f);
                drawAccentButton(c, playAgainRect, "PLAY AGAIN", pressed == PRESS_PLAY_AGAIN);
            } else {
                drawText(c, "PAUSED", cx, box.top + 67f, 27f, TEXT, true, Paint.Align.CENTER);
                drawText(c, "Swipe or tap PLAY to continue", cx, box.top + 99f,
                        14f, MUTED, false, Paint.Align.CENTER);
                playAgainRect.setEmpty();
            }
        }

        private void drawAccentButton(Canvas c, RectF rect, String label, boolean isPressed) {
            int top = isPressed ? Color.rgb(98, 222, 132) : Color.rgb(135, 255, 159);
            int bottom = isPressed ? Color.rgb(49, 180, 91) : Color.rgb(62, 214, 108);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, top, bottom, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, 22f, 22f, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f);
            paint.setColor(Color.argb(190, 239, 255, 241));
            c.drawRoundRect(rect, 22f, 22f, paint);
            paint.setStyle(Paint.Style.FILL);
            drawText(c, label, rect.centerX(), verticallyCenteredY(rect), 11.5f,
                    Color.rgb(4, 33, 17), true, Paint.Align.CENTER);
        }

        private void drawGlassPanel(Canvas c, RectF rect, float radius, int alpha, boolean strongerHighlight) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(76, 0, 0, 0));
            c.drawRoundRect(new RectF(rect.left + 1f, rect.top + 5f, rect.right + 1f, rect.bottom + 5f),
                    radius, radius, paint);

            paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                    Color.argb(alpha + 18, 105, 168, 135),
                    Color.argb(alpha, 23, 67, 45), Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, radius, radius, paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(Color.argb(strongerHighlight ? 95 : 64, 224, 255, 237));
            c.drawRoundRect(new RectF(rect.left + .8f, rect.top + .8f, rect.right - .8f, rect.bottom - .8f),
                    radius, radius, paint);

            paint.setStrokeWidth(2f);
            paint.setColor(Color.argb(strongerHighlight ? 72 : 42, 255, 255, 255));
            c.drawArc(new RectF(rect.left + 3f, rect.top + 3f, rect.right - 3f, rect.bottom - 3f),
                    198f, 116f, false, paint);

            paint.setStrokeWidth(1.2f);
            paint.setColor(Color.argb(70, 0, 12, 6));
            c.drawArc(new RectF(rect.left + 2f, rect.top + 2f, rect.right - 2f, rect.bottom - 2f),
                    18f, 135f, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawGlassButton(Canvas c, RectF rect, String label, boolean isPressed, float textSize) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(60, 0, 0, 0));
            c.drawRoundRect(new RectF(rect.left + 1f, rect.top + 4f, rect.right + 1f, rect.bottom + 4f),
                    20f, 20f, paint);

            int topAlpha = isPressed ? 70 : 58;
            int bottomAlpha = isPressed ? 86 : 52;
            paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                    Color.argb(topAlpha, 184, 255, 211),
                    Color.argb(bottomAlpha, 25, 98, 60), Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, 20f, 20f, paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(Color.argb(108, 226, 255, 236));
            c.drawRoundRect(new RectF(rect.left + .7f, rect.top + .7f, rect.right - .7f, rect.bottom - .7f),
                    20f, 20f, paint);

            paint.setStrokeWidth(2f);
            paint.setColor(Color.argb(75, 255, 255, 255));
            c.drawArc(new RectF(rect.left + 3f, rect.top + 3f, rect.right - 3f, rect.bottom - 3f),
                    200f, 112f, false, paint);
            paint.setStyle(Paint.Style.FILL);

            drawText(c, label, rect.centerX(), verticallyCenteredY(rect), textSize,
                    isPressed ? ACCENT_BRIGHT : TEXT, true, Paint.Align.CENTER);
        }

        private float verticallyCenteredY(RectF rect) {
            paint.setTextSize(16f);
            Paint.FontMetrics fm = paint.getFontMetrics();
            return rect.centerY() - (fm.ascent + fm.descent) / 2f;
        }

        private int lighten(int color, int amount) {
            return Color.rgb(Math.min(255, Color.red(color) + amount),
                    Math.min(255, Color.green(color) + amount),
                    Math.min(255, Color.blue(color) + amount));
        }

        private void drawText(Canvas c, String text, float x, float y, float size,
                              int color, boolean bold, Paint.Align align) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTextAlign(align);
            paint.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
            c.drawText(text, x, y, paint);
        }

        private int hitButton(float x, float y) {
            if (gameOver && playAgainRect.contains(x, y)) return PRESS_PLAY_AGAIN;
            if (pauseRect.contains(x, y)) return PRESS_PAUSE;
            if (restartRect.contains(x, y)) return PRESS_RESTART;
            if (upRect.contains(x, y)) return PRESS_UP;
            if (downRect.contains(x, y)) return PRESS_DOWN;
            if (leftRect.contains(x, y)) return PRESS_LEFT;
            if (rightRect.contains(x, y)) return PRESS_RIGHT;
            return PRESS_NONE;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchDownX = event.getX();
                touchDownY = event.getY();
                pressed = hitButton(touchDownX, touchDownY);
                if (pressed != PRESS_NONE) invalidate();
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                int now = hitButton(event.getX(), event.getY());
                if (now != pressed && pressed != PRESS_NONE) {
                    pressed = PRESS_NONE;
                    invalidate();
                }
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_UP) {
                float x = event.getX();
                float y = event.getY();
                float sx = x - touchDownX;
                float sy = y - touchDownY;
                int releasedOn = hitButton(x, y);
                int originallyPressed = pressed;
                pressed = PRESS_NONE;

                if (originallyPressed != PRESS_NONE && originallyPressed == releasedOn) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    switch (releasedOn) {
                        case PRESS_PAUSE:
                            if (!gameOver) paused = !paused;
                            break;
                        case PRESS_RESTART:
                        case PRESS_PLAY_AGAIN:
                            restartGame();
                            return true;
                        case PRESS_UP:
                            setDirection(0, -1);
                            break;
                        case PRESS_DOWN:
                            setDirection(0, 1);
                            break;
                        case PRESS_LEFT:
                            setDirection(-1, 0);
                            break;
                        case PRESS_RIGHT:
                            setDirection(1, 0);
                            break;
                        default:
                            break;
                    }
                    invalidate();
                    return true;
                }

                if (gameOver && x >= boardLeft && x <= boardLeft + boardSize
                        && y >= boardTop && y <= boardTop + boardSize) {
                    restartGame();
                    return true;
                }

                boolean startedOnBoard = touchDownX >= boardLeft && touchDownX <= boardLeft + boardSize
                        && touchDownY >= boardTop && touchDownY <= boardTop + boardSize;
                if (startedOnBoard && Math.max(Math.abs(sx), Math.abs(sy)) > 30f) {
                    if (Math.abs(sx) > Math.abs(sy)) setDirection(sx > 0 ? 1 : -1, 0);
                    else setDirection(0, sy > 0 ? 1 : -1);
                }
                invalidate();
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                pressed = PRESS_NONE;
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
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility == VISIBLE) lifecyclePaused = false;
        }

        @Override
        protected void onDetachedFromWindow() {
            handler.removeCallbacks(ticker);
            super.onDetachedFromWindow();
        }
    }
}
