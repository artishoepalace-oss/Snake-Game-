package com.anuj.snake2d;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Choreographer;
import android.view.Display;
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
        requestHighestRefreshRate();
        snakeView = new SnakeView(this);
        setContentView(snakeView);
    }

    @SuppressWarnings("deprecation")
    private void requestHighestRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display display = getWindowManager().getDefaultDisplay();
            Display.Mode[] modes = display.getSupportedModes();
            Display.Mode best = display.getMode();
            for (Display.Mode mode : modes) {
                if (mode.getRefreshRate() > best.getRefreshRate()) {
                    best = mode;
                }
            }
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.preferredDisplayModeId = best.getModeId();
            lp.preferredRefreshRate = best.getRefreshRate();
            getWindow().setAttributes(lp);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (snakeView != null) snakeView.pauseForLifecycle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestHighestRefreshRate();
        if (snakeView != null) snakeView.resumeForLifecycle();
    }

    static class SnakeView extends View implements Choreographer.FrameCallback {
        private static final int GRID = 20;
        private static final int BG = Color.rgb(5, 10, 9);
        private static final int SURFACE = Color.rgb(12, 22, 18);
        private static final int SURFACE_2 = Color.rgb(16, 31, 25);
        private static final int SURFACE_ACTIVE = Color.rgb(28, 74, 48);
        private static final int BOARD = Color.rgb(7, 15, 12);
        private static final int GRID_LINE = Color.rgb(23, 45, 35);
        private static final int BORDER = Color.rgb(38, 72, 56);
        private static final int BORDER_ACTIVE = Color.rgb(86, 235, 135);
        private static final int TEXT = Color.rgb(244, 249, 246);
        private static final int MUTED = Color.rgb(137, 160, 148);
        private static final int GREEN = Color.rgb(96, 238, 122);
        private static final int GREEN_HEAD = Color.rgb(183, 255, 145);
        private static final int GREEN_DARK = Color.rgb(44, 153, 76);
        private static final int RED = Color.rgb(255, 91, 103);
        private static final int RED_LIGHT = Color.rgb(255, 203, 208);
        private static final Typeface FONT_REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
        private static final Typeface FONT_BOLD = Typeface.create("sans-serif", Typeface.BOLD);

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Random random = new Random();
        private final List<Point> snake = new ArrayList<>();
        private final List<Point> previousSnake = new ArrayList<>();
        private final Deque<Point> directionQueue = new ArrayDeque<>();
        private final SharedPreferences prefs;

        private final RectF hudRect = new RectF();
        private final RectF boardRect = new RectF();
        private final RectF pauseRect = new RectF();
        private final RectF restartRect = new RectF();
        private final RectF controlsRect = new RectF();
        private final RectF upRect = new RectF();
        private final RectF downRect = new RectF();
        private final RectF leftRect = new RectF();
        private final RectF rightRect = new RectF();
        private final RectF overlayRect = new RectF();
        private final RectF playAgainRect = new RectF();
        private final RectF segmentRect = new RectF();

        private Point food = new Point(5, 5);
        private int dx = 1;
        private int dy = 0;
        private int score = 0;
        private int highScore = 0;
        private boolean paused = false;
        private boolean gameOver = false;
        private boolean lifecyclePaused = false;
        private boolean frameLoopRunning = false;
        private float density;
        private float boardLeft;
        private float boardTop;
        private float boardSize;
        private float cell;
        private float touchDownX;
        private float touchDownY;
        private float touchX;
        private float touchY;
        private float renderAlpha = 1f;
        private long tickMs = 150;
        private long lastFrameNanos = 0L;
        private double accumulatorMs = 0.0;
        private RectF pressedRect = null;

        SnakeView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            prefs = context.getSharedPreferences("snake_scores", Context.MODE_PRIVATE);
            highScore = prefs.getInt("high_score", 0);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            restartGame();
            startFrameLoop();
        }

        private float dp(float value) {
            return value * density;
        }

        private void startFrameLoop() {
            if (!frameLoopRunning) {
                frameLoopRunning = true;
                lastFrameNanos = 0L;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!frameLoopRunning) return;

            if (lastFrameNanos == 0L) {
                lastFrameNanos = frameTimeNanos;
            }
            double deltaMs = (frameTimeNanos - lastFrameNanos) / 1_000_000.0;
            lastFrameNanos = frameTimeNanos;
            if (deltaMs > 50.0) deltaMs = 50.0;

            if (!paused && !gameOver && !lifecyclePaused) {
                accumulatorMs += deltaMs;
                while (accumulatorMs >= tickMs) {
                    step();
                    accumulatorMs -= tickMs;
                }
                renderAlpha = (float) Math.max(0.0, Math.min(1.0, accumulatorMs / tickMs));
            } else {
                renderAlpha = 1f;
            }

            postInvalidateOnAnimation();
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            float side = dp(14);
            float top = dp(14);
            float hudH = dp(82);
            hudRect.set(side, top, w - side, top + hudH);

            float gap = dp(10);
            float boardAvailableW = w - side * 2f;
            boardSize = boardAvailableW;
            boardLeft = side;
            boardTop = hudRect.bottom + gap;
            cell = boardSize / GRID;
            boardRect.set(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize);

            float btnW = dp(104);
            float btnH = dp(34);
            pauseRect.set(hudRect.left + dp(12), hudRect.bottom - btnH - dp(10), hudRect.left + dp(12) + btnW, hudRect.bottom - dp(10));
            restartRect.set(hudRect.right - dp(12) - btnW, hudRect.bottom - btnH - dp(10), hudRect.right - dp(12), hudRect.bottom - dp(10));

            float controlsTop = boardRect.bottom + dp(12);
            float controlsBottom = h - dp(40);
            if (controlsBottom - controlsTop < dp(188)) controlsBottom = controlsTop + dp(188);
            controlsRect.set(side, controlsTop, w - side, controlsBottom);

            float cx = controlsRect.centerX();
            float rowH = dp(64);
            float btnGap = dp(10);
            float horizontalW = Math.min(dp(96), (controlsRect.width() - dp(56)) / 3f);
            float centerW = Math.min(dp(104), horizontalW + dp(8));
            float upW = dp(86);

            upRect.set(cx - upW / 2f, controlsRect.top + dp(42), cx + upW / 2f, controlsRect.top + dp(42) + rowH);
            float rowTop = upRect.bottom + btnGap;
            downRect.set(cx - centerW / 2f, rowTop, cx + centerW / 2f, rowTop + rowH);
            leftRect.set(downRect.left - btnGap - horizontalW, rowTop, downRect.left - btnGap, rowTop + rowH);
            rightRect.set(downRect.right + btnGap, rowTop, downRect.right + btnGap + horizontalW, rowTop + rowH);

            overlayRect.set(boardRect.left + boardSize * .08f, boardRect.top + boardSize * .34f,
                    boardRect.right - boardSize * .08f, boardRect.top + boardSize * .67f);
            playAgainRect.set(overlayRect.left + dp(24), overlayRect.bottom - dp(58), overlayRect.right - dp(24), overlayRect.bottom - dp(16));
        }

        private void restartGame() {
            snake.clear();
            snake.add(new Point(7, 10));
            snake.add(new Point(6, 10));
            snake.add(new Point(5, 10));
            snake.add(new Point(4, 10));
            copySnake(snake, previousSnake);
            dx = 1;
            dy = 0;
            directionQueue.clear();
            score = 0;
            tickMs = 150;
            paused = false;
            gameOver = false;
            lifecyclePaused = false;
            accumulatorMs = 0.0;
            renderAlpha = 1f;
            spawnFood();
            postInvalidateOnAnimation();
        }

        private void copySnake(List<Point> from, List<Point> to) {
            to.clear();
            for (Point p : from) {
                to.add(new Point(p.x, p.y));
            }
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
        }

        private void queueDirection(int ndx, int ndy) {
            if (gameOver) return;
            int baseDx = dx;
            int baseDy = dy;
            Point last = directionQueue.peekLast();
            if (last != null) {
                baseDx = last.x;
                baseDy = last.y;
            }
            if (ndx == baseDx && ndy == baseDy) return;
            if (ndx == -baseDx && ndy == -baseDy) return;
            if (directionQueue.size() >= 3) return;
            directionQueue.offerLast(new Point(ndx, ndy));
            if (paused) paused = false;
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }

        private void consumeQueuedDirection() {
            Point nextDir = directionQueue.pollFirst();
            if (nextDir != null && !(nextDir.x == -dx && nextDir.y == -dy)) {
                dx = nextDir.x;
                dy = nextDir.y;
            }
        }

        private void step() {
            copySnake(snake, previousSnake);
            consumeQueuedDirection();

            Point head = snake.get(0);
            Point next = new Point(head.x + dx, head.y + dy);
            boolean growing = next.x == food.x && next.y == food.y;

            boolean hit = next.x < 0 || next.y < 0 || next.x >= GRID || next.y >= GRID;
            if (!hit) {
                int limit = snake.size() - (growing ? 0 : 1);
                for (int i = 0; i < limit; i++) {
                    Point s = snake.get(i);
                    if (s.x == next.x && s.y == next.y) {
                        hit = true;
                        break;
                    }
                }
            }

            if (hit) {
                gameOver = true;
                accumulatorMs = 0.0;
                if (score > highScore) {
                    highScore = score;
                    prefs.edit().putInt("high_score", highScore).apply();
                }
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                return;
            }

            snake.add(0, next);
            if (growing) {
                score += 10;
                if (score > highScore) highScore = score;
                prefs.edit().putInt("high_score", highScore).apply();
                tickMs = Math.max(72, 150 - (score / 30) * 4L);
                spawnFood();
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            } else {
                snake.remove(snake.size() - 1);
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.drawColor(BG);
            drawHud(c);
            drawBoard(c);
            drawControls(c);
            if (paused || gameOver) drawOverlay(c);
        }

        private void drawHud(Canvas c) {
            drawPanel(c, hudRect, dp(18), SURFACE, BORDER);
            drawText(c, "SNAKE 2D", hudRect.left + dp(14), hudRect.top + dp(23), dp(18), TEXT, true, Paint.Align.LEFT);
            drawText(c, "FAST MODE", hudRect.left + dp(14), hudRect.top + dp(41), dp(10), GREEN, true, Paint.Align.LEFT);

            drawText(c, String.valueOf(score), hudRect.centerX(), hudRect.top + dp(27), dp(21), TEXT, true, Paint.Align.CENTER);
            drawText(c, "SCORE", hudRect.centerX(), hudRect.top + dp(44), dp(9), MUTED, true, Paint.Align.CENTER);

            drawText(c, String.valueOf(highScore), hudRect.right - dp(14), hudRect.top + dp(27), dp(20), TEXT, true, Paint.Align.RIGHT);
            drawText(c, "BEST", hudRect.right - dp(14), hudRect.top + dp(44), dp(9), MUTED, true, Paint.Align.RIGHT);

            drawButton(c, pauseRect, paused ? "PLAY" : "PAUSE", pressedRect == pauseRect, dp(11));
            drawButton(c, restartRect, "RESTART", pressedRect == restartRect, dp(11));
        }

        private void drawBoard(Canvas c) {
            drawPanel(c, boardRect, dp(18), BOARD, BORDER);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(.65f));
            paint.setColor(GRID_LINE);
            for (int i = 1; i < GRID; i++) {
                float x = boardLeft + i * cell;
                float y = boardTop + i * cell;
                c.drawLine(x, boardTop, x, boardTop + boardSize, paint);
                c.drawLine(boardLeft, y, boardLeft + boardSize, y, paint);
            }
            paint.setStyle(Paint.Style.FILL);

            float inset = Math.max(dp(1.8f), cell * .12f);
            for (int i = snake.size() - 1; i >= 0; i--) {
                Point cur = snake.get(i);
                Point prev;
                if (previousSnake.isEmpty()) {
                    prev = cur;
                } else if (i < previousSnake.size()) {
                    prev = previousSnake.get(i);
                } else {
                    prev = previousSnake.get(previousSnake.size() - 1);
                }

                float gx = prev.x + (cur.x - prev.x) * renderAlpha;
                float gy = prev.y + (cur.y - prev.y) * renderAlpha;
                float l = boardLeft + gx * cell + inset;
                float t = boardTop + gy * cell + inset;
                float r = l + cell - inset * 2f;
                float b = t + cell - inset * 2f;
                segmentRect.set(l, t, r, b);

                paint.setColor(i == 0 ? GREEN_HEAD : GREEN);
                c.drawRoundRect(segmentRect, cell * .26f, cell * .26f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(.7f));
                paint.setColor(i == 0 ? Color.rgb(226, 255, 204) : GREEN_DARK);
                c.drawRoundRect(segmentRect, cell * .26f, cell * .26f, paint);
                paint.setStyle(Paint.Style.FILL);
            }

            float fx = boardLeft + (food.x + .5f) * cell;
            float fy = boardTop + (food.y + .5f) * cell;
            paint.setColor(RED);
            c.drawCircle(fx, fy, cell * .30f, paint);
            paint.setColor(RED_LIGHT);
            c.drawCircle(fx - cell * .09f, fy - cell * .10f, cell * .06f, paint);
        }

        private void drawControls(Canvas c) {
            drawPanel(c, controlsRect, dp(20), SURFACE, BORDER);
            drawText(c, "SWIPE CONTROL", controlsRect.centerX(), controlsRect.top + dp(22), dp(11), MUTED, true, Paint.Align.CENTER);
            drawText(c, "Swipe anywhere on screen or use the buttons", controlsRect.centerX(), controlsRect.top + dp(37), dp(10), MUTED, false, Paint.Align.CENTER);

            drawButton(c, upRect, "▲", pressedRect == upRect, dp(22));
            drawButton(c, leftRect, "◀", pressedRect == leftRect, dp(22));
            drawButton(c, downRect, "▼", pressedRect == downRect, dp(22));
            drawButton(c, rightRect, "▶", pressedRect == rightRect, dp(22));

            drawText(c, "v1.0.0.3  •  HIGH REFRESH READY", controlsRect.centerX(), controlsRect.bottom - dp(14), dp(9), MUTED, false, Paint.Align.CENTER);
        }

        private void drawOverlay(Canvas c) {
            drawPanel(c, overlayRect, dp(20), SURFACE_2, BORDER_ACTIVE);
            float cx = overlayRect.centerX();
            if (gameOver) {
                drawText(c, "GAME OVER", cx, overlayRect.top + dp(48), dp(23), TEXT, true, Paint.Align.CENTER);
                drawText(c, "Score " + score + "  •  Best " + highScore, cx, overlayRect.top + dp(75), dp(13), MUTED, false, Paint.Align.CENTER);
                drawPrimaryButton(c, playAgainRect, "PLAY AGAIN", pressedRect == playAgainRect);
            } else {
                drawText(c, "PAUSED", cx, overlayRect.top + dp(48), dp(23), TEXT, true, Paint.Align.CENTER);
                drawText(c, "Swipe or tap PLAY to continue", cx, overlayRect.top + dp(75), dp(13), MUTED, false, Paint.Align.CENTER);
                drawPrimaryButton(c, playAgainRect, "RESUME", pressedRect == playAgainRect);
            }
        }

        private void drawPanel(Canvas c, RectF rect, float radius, int fill, int border) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fill);
            c.drawRoundRect(rect, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(border);
            c.drawRoundRect(rect, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawButton(Canvas c, RectF rect, String label, boolean pressed, float textSize) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pressed ? SURFACE_ACTIVE : SURFACE_2);
            c.drawRoundRect(rect, dp(15), dp(15), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.2f));
            paint.setColor(pressed ? BORDER_ACTIVE : BORDER);
            c.drawRoundRect(rect, dp(15), dp(15), paint);
            paint.setStyle(Paint.Style.FILL);
            drawText(c, label, rect.centerX(), rect.centerY() + textSize * .34f, textSize, TEXT, true, Paint.Align.CENTER);
        }

        private void drawPrimaryButton(Canvas c, RectF rect, String label, boolean pressed) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pressed ? Color.rgb(127, 255, 155) : GREEN);
            c.drawRoundRect(rect, dp(16), dp(16), paint);
            drawText(c, label, rect.centerX(), rect.centerY() + dp(4), dp(12), Color.rgb(4, 27, 13), true, Paint.Align.CENTER);
        }

        private void drawText(Canvas c, String text, float x, float y, float size, int color, boolean bold, Paint.Align align) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTextAlign(align);
            paint.setTypeface(bold ? FONT_BOLD : FONT_REGULAR);
            c.drawText(text, x, y, paint);
        }

        private RectF hitTarget(float x, float y) {
            if (pauseRect.contains(x, y)) return pauseRect;
            if (restartRect.contains(x, y)) return restartRect;
            if ((paused || gameOver) && playAgainRect.contains(x, y)) return playAgainRect;
            if (upRect.contains(x, y)) return upRect;
            if (downRect.contains(x, y)) return downRect;
            if (leftRect.contains(x, y)) return leftRect;
            if (rightRect.contains(x, y)) return rightRect;
            return null;
        }

        private boolean handleDirectionButton(float x, float y) {
            if (upRect.contains(x, y)) {
                queueDirection(0, -1);
                return true;
            }
            if (downRect.contains(x, y)) {
                queueDirection(0, 1);
                return true;
            }
            if (leftRect.contains(x, y)) {
                queueDirection(-1, 0);
                return true;
            }
            if (rightRect.contains(x, y)) {
                queueDirection(1, 0);
                return true;
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            touchX = x;
            touchY = y;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchDownX = x;
                    touchDownY = y;
                    pressedRect = hitTarget(x, y);
                    postInvalidateOnAnimation();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    RectF nextPressed = hitTarget(x, y);
                    if (nextPressed != pressedRect) {
                        pressedRect = nextPressed;
                        postInvalidateOnAnimation();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    RectF released = hitTarget(x, y);
                    float sx = x - touchDownX;
                    float sy = y - touchDownY;
                    float absX = Math.abs(sx);
                    float absY = Math.abs(sy);

                    if (released == pauseRect) {
                        if (!gameOver) paused = !paused;
                        pressedRect = null;
                        return true;
                    }
                    if (released == restartRect) {
                        pressedRect = null;
                        restartGame();
                        return true;
                    }
                    if (released == playAgainRect) {
                        if (gameOver) restartGame();
                        else paused = false;
                        pressedRect = null;
                        return true;
                    }
                    if (handleDirectionButton(x, y)) {
                        pressedRect = null;
                        return true;
                    }

                    if (Math.max(absX, absY) >= dp(18)) {
                        if (absX > absY) {
                            queueDirection(sx > 0 ? 1 : -1, 0);
                        } else {
                            queueDirection(0, sy > 0 ? 1 : -1);
                        }
                    } else if (paused && boardRect.contains(x, y)) {
                        paused = false;
                    }

                    pressedRect = null;
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    pressedRect = null;
                    return true;
            }
            return true;
        }

        void pauseForLifecycle() {
            lifecyclePaused = true;
            if (!gameOver) paused = true;
        }

        void resumeForLifecycle() {
            lifecyclePaused = false;
            startFrameLoop();
        }

        @Override
        protected void onDetachedFromWindow() {
            frameLoopRunning = false;
            Choreographer.getInstance().removeFrameCallback(this);
            super.onDetachedFromWindow();
        }
    }
}
