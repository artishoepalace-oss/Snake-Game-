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
        enableImmersiveMode();
        requestHighestRefreshRate();
        snakeView = new SnakeView(this);
        setContentView(snakeView);
    }

    @SuppressWarnings("deprecation")
    private void enableImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @SuppressWarnings("deprecation")
    private void requestHighestRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display display = getWindowManager().getDefaultDisplay();
            Display.Mode best = display.getMode();
            for (Display.Mode mode : display.getSupportedModes()) {
                if (mode.getRefreshRate() > best.getRefreshRate()) best = mode;
            }
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.preferredDisplayModeId = best.getModeId();
            lp.preferredRefreshRate = best.getRefreshRate();
            getWindow().setAttributes(lp);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enableImmersiveMode();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (snakeView != null) snakeView.pauseForLifecycle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableImmersiveMode();
        requestHighestRefreshRate();
        if (snakeView != null) snakeView.resumeForLifecycle();
    }

    static final class SnakeView extends View implements Choreographer.FrameCallback {
        private static final int GRID = 20;

        private static final int BG = Color.rgb(5, 11, 9);
        private static final int SURFACE = Color.rgb(14, 25, 20);
        private static final int SURFACE_2 = Color.rgb(18, 35, 27);
        private static final int BOARD = Color.rgb(7, 16, 13);
        private static final int BORDER = Color.rgb(34, 63, 49);
        private static final int GRID_LINE = Color.rgb(20, 38, 31);
        private static final int TEXT = Color.rgb(245, 250, 247);
        private static final int MUTED = Color.rgb(137, 158, 148);
        private static final int GREEN = Color.rgb(91, 235, 119);
        private static final int GREEN_BRIGHT = Color.rgb(184, 255, 147);
        private static final int GREEN_DARK = Color.rgb(29, 91, 50);
        private static final int RED = Color.rgb(255, 93, 106);
        private static final int PRESSED = Color.rgb(31, 83, 54);

        private static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
        private static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Random random = new Random();
        private final List<Point> snake = new ArrayList<>();
        private final List<Point> previousSnake = new ArrayList<>();
        private final Deque<Point> directionQueue = new ArrayDeque<>();
        private final SharedPreferences prefs;

        private final RectF headerRect = new RectF();
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
        private final RectF tempRect = new RectF();

        private Point food = new Point(5, 5);
        private int dx = 1;
        private int dy = 0;
        private int score = 0;
        private int highScore = 0;
        private boolean paused = false;
        private boolean gameOver = false;
        private boolean lifecyclePaused = false;
        private boolean frameLoopRunning = false;
        private boolean swipeArmed = false;
        private float density;
        private float boardLeft;
        private float boardTop;
        private float boardSize;
        private float cell;
        private float touchDownX;
        private float touchDownY;
        private float swipeAnchorX;
        private float swipeAnchorY;
        private float renderAlpha = 1f;
        private float swipeThreshold;
        private long tickMs = 150L;
        private long lastFrameNanos = 0L;
        private double accumulatorMs = 0.0;
        private RectF pressedRect = null;

        SnakeView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            swipeThreshold = dp(18);
            prefs = context.getSharedPreferences("snake_scores", Context.MODE_PRIVATE);
            highScore = prefs.getInt("high_score", 0);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            restartGame();
            startFrameLoop();
        }

        private float dp(float value) { return value * density; }

        private void startFrameLoop() {
            if (frameLoopRunning) return;
            frameLoopRunning = true;
            lastFrameNanos = 0L;
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!frameLoopRunning) return;
            if (lastFrameNanos == 0L) lastFrameNanos = frameTimeNanos;

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
            float side = dp(12);
            float top = dp(12);
            float headerH = dp(104);
            headerRect.set(side, top, w - side, top + headerH);

            float boardGap = dp(10);
            float bottomSafe = dp(16);
            float controlsMinH = dp(190);
            float availableWidth = w - side * 2f;
            float availableForBoard = h - headerRect.bottom - controlsMinH - bottomSafe - boardGap * 2f;
            boardSize = Math.min(availableWidth, availableForBoard);
            boardSize = Math.max(dp(248), boardSize);
            boardLeft = (w - boardSize) / 2f;
            boardTop = headerRect.bottom + boardGap;
            cell = boardSize / GRID;
            boardRect.set(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize);

            float actionH = dp(38);
            float actionW = Math.min(dp(112), headerRect.width() * .29f);
            float actionTop = headerRect.top + dp(58);
            pauseRect.set(headerRect.left + dp(12), actionTop,
                    headerRect.left + dp(12) + actionW, actionTop + actionH);
            restartRect.set(headerRect.right - dp(12) - actionW, actionTop,
                    headerRect.right - dp(12), actionTop + actionH);

            float controlsTop = boardRect.bottom + dp(10);
            float controlsBottom = h - bottomSafe;
            controlsRect.set(side, controlsTop, w - side, controlsBottom);

            float cx = controlsRect.centerX();
            float btnH = Math.min(dp(70), Math.max(dp(58), controlsRect.height() * .32f));
            float upW = dp(92);
            float horizontalW = Math.min(dp(104), (controlsRect.width() - dp(64)) / 3f);
            float centerW = Math.min(dp(112), horizontalW + dp(10));
            float gap = dp(8);

            float totalControlsH = btnH * 2f + gap;
            float titleReserve = dp(40);
            float free = Math.max(0f, controlsRect.height() - titleReserve - totalControlsH);
            float firstTop = controlsRect.top + titleReserve + free * .34f;
            upRect.set(cx - upW / 2f, firstTop, cx + upW / 2f, firstTop + btnH);
            float rowTop = upRect.bottom + gap;
            downRect.set(cx - centerW / 2f, rowTop, cx + centerW / 2f, rowTop + btnH);
            leftRect.set(downRect.left - gap - horizontalW, rowTop, downRect.left - gap, rowTop + btnH);
            rightRect.set(downRect.right + gap, rowTop, downRect.right + gap + horizontalW, rowTop + btnH);

            float overlayH = Math.min(dp(196), boardSize * .42f);
            overlayH = Math.max(dp(174), overlayH);
            float overlayWInset = Math.max(dp(28), boardSize * .08f);
            float overlayTop = boardRect.centerY() - overlayH / 2f;
            overlayRect.set(boardRect.left + overlayWInset, overlayTop,
                    boardRect.right - overlayWInset, overlayTop + overlayH);

            float buttonH = dp(48);
            float buttonBottom = overlayRect.bottom - dp(16);
            playAgainRect.set(overlayRect.left + dp(24), buttonBottom - buttonH,
                    overlayRect.right - dp(24), buttonBottom);
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
            tickMs = 150L;
            paused = false;
            gameOver = false;
            lifecyclePaused = false;
            accumulatorMs = 0.0;
            renderAlpha = 1f;
            pressedRect = null;
            spawnFood();
            postInvalidateOnAnimation();
        }

        private void copySnake(List<Point> from, List<Point> to) {
            to.clear();
            for (Point p : from) to.add(new Point(p.x, p.y));
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
            if ((ndx == baseDx && ndy == baseDy) || (ndx == -baseDx && ndy == -baseDy)) return;
            if (directionQueue.size() >= 3) return;
            directionQueue.offerLast(new Point(ndx, ndy));
            if (paused) paused = false;
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }

        private void consumeDirection() {
            Point next = directionQueue.pollFirst();
            if (next != null && !(next.x == -dx && next.y == -dy)) {
                dx = next.x;
                dy = next.y;
            }
        }

        private void step() {
            copySnake(snake, previousSnake);
            consumeDirection();

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
                tickMs = Math.max(70L, 150L - (score / 30) * 4L);
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
            drawHeader(c);
            drawBoard(c);
            drawControls(c);
            if (paused || gameOver) drawOverlay(c);
        }

        private void drawHeader(Canvas c) {
            drawPanel(c, headerRect, dp(18), SURFACE, BORDER);

            float titleY = headerRect.top + dp(27);
            float labelY = headerRect.top + dp(46);
            drawText(c, "SNAKE", headerRect.left + dp(14), titleY, dp(18), TEXT, true, Paint.Align.LEFT);
            drawText(c, "SMOOTH MODE", headerRect.left + dp(14), labelY, dp(9), GREEN, true, Paint.Align.LEFT);

            drawText(c, String.valueOf(score), headerRect.centerX(), titleY + dp(2), dp(21), TEXT, true, Paint.Align.CENTER);
            drawText(c, "SCORE", headerRect.centerX(), labelY, dp(8.5f), MUTED, true, Paint.Align.CENTER);

            drawText(c, String.valueOf(highScore), headerRect.right - dp(14), titleY + dp(2), dp(20), TEXT, true, Paint.Align.RIGHT);
            drawText(c, "BEST", headerRect.right - dp(14), labelY, dp(8.5f), MUTED, true, Paint.Align.RIGHT);

            drawButton(c, pauseRect, paused ? "PLAY" : "PAUSE", pressedRect == pauseRect, false);
            drawButton(c, restartRect, "RESTART", pressedRect == restartRect, false);
        }

        private void drawBoard(Canvas c) {
            drawPanel(c, boardRect, dp(18), BOARD, BORDER);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(.6f));
            paint.setColor(GRID_LINE);
            for (int i = 1; i < GRID; i++) {
                float x = boardLeft + i * cell;
                float y = boardTop + i * cell;
                c.drawLine(x, boardTop, x, boardTop + boardSize, paint);
                c.drawLine(boardLeft, y, boardLeft + boardSize, y, paint);
            }
            paint.setStyle(Paint.Style.FILL);

            float inset = Math.max(dp(1.8f), cell * .11f);
            int count = snake.size();
            for (int i = count - 1; i >= 0; i--) {
                Point cur = snake.get(i);
                Point prev = i < previousSnake.size() ? previousSnake.get(i) : cur;
                float gx = prev.x + (cur.x - prev.x) * renderAlpha;
                float gy = prev.y + (cur.y - prev.y) * renderAlpha;

                float l = boardLeft + gx * cell + inset;
                float t = boardTop + gy * cell + inset;
                float r = l + cell - inset * 2f;
                float b = t + cell - inset * 2f;
                tempRect.set(l, t, r, b);
                paint.setColor(i == 0 ? GREEN_BRIGHT : GREEN);
                c.drawRoundRect(tempRect, cell * .28f, cell * .28f, paint);

                if (i == 0) {
                    paint.setColor(GREEN_DARK);
                    float eye = cell * .055f;
                    if (dy != 0) {
                        float ey = t + (dy > 0 ? tempRect.height() * .70f : tempRect.height() * .30f);
                        c.drawCircle(l + tempRect.width() * .35f, ey, eye, paint);
                        c.drawCircle(l + tempRect.width() * .65f, ey, eye, paint);
                    } else {
                        float ex = l + (dx >= 0 ? tempRect.width() * .70f : tempRect.width() * .30f);
                        c.drawCircle(ex, t + tempRect.height() * .33f, eye, paint);
                        c.drawCircle(ex, t + tempRect.height() * .67f, eye, paint);
                    }
                }
            }

            float pulse = 1f + .08f * (float) Math.sin(System.nanoTime() / 180_000_000.0);
            float fx = boardLeft + (food.x + .5f) * cell;
            float fy = boardTop + (food.y + .5f) * cell;
            paint.setColor(RED);
            c.drawCircle(fx, fy, cell * .28f * pulse, paint);
            paint.setColor(Color.rgb(255, 213, 218));
            c.drawCircle(fx - cell * .08f, fy - cell * .09f, cell * .05f, paint);
        }

        private void drawControls(Canvas c) {
            drawText(c, "SWIPE ANYWHERE OR USE CONTROLS", controlsRect.centerX(), controlsRect.top + dp(18),
                    dp(9.5f), MUTED, true, Paint.Align.CENTER);
            drawButton(c, upRect, "▲", pressedRect == upRect, true);
            drawButton(c, leftRect, "◀", pressedRect == leftRect, true);
            drawButton(c, downRect, "▼", pressedRect == downRect, true);
            drawButton(c, rightRect, "▶", pressedRect == rightRect, true);
        }

        private void drawOverlay(Canvas c) {
            drawPanel(c, overlayRect, dp(22), SURFACE_2, BORDER);
            float cx = overlayRect.centerX();
            float titleY = overlayRect.top + dp(48);
            float metaY = overlayRect.top + dp(82);

            if (gameOver) {
                drawText(c, "GAME OVER", cx, titleY, dp(23), TEXT, true, Paint.Align.CENTER);
                drawText(c, "Score " + score + "  •  Best " + highScore, cx, metaY, dp(13), MUTED, false, Paint.Align.CENTER);
                drawPrimaryButton(c, playAgainRect, "PLAY AGAIN", pressedRect == playAgainRect);
            } else {
                drawText(c, "PAUSED", cx, titleY, dp(23), TEXT, true, Paint.Align.CENTER);
                drawText(c, "Ready when you are", cx, metaY, dp(13), MUTED, false, Paint.Align.CENTER);
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

        private void drawButton(Canvas c, RectF rect, String label, boolean isPressed, boolean big) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(isPressed ? PRESSED : SURFACE_2);
            float radius = dp(big ? 18 : 15);
            c.drawRoundRect(rect, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(isPressed ? GREEN : BORDER);
            c.drawRoundRect(rect, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            drawText(c, label, rect.centerX(), rect.centerY() + dp(big ? 7 : 4),
                    dp(big ? 24 : 10.5f), TEXT, true, Paint.Align.CENTER);
        }

        private void drawPrimaryButton(Canvas c, RectF rect, String label, boolean isPressed) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(isPressed ? Color.rgb(133, 255, 154) : GREEN);
            c.drawRoundRect(rect, dp(18), dp(18), paint);
            drawText(c, label, rect.centerX(), rect.centerY() + dp(4), dp(12), BG, true, Paint.Align.CENTER);
        }

        private void drawText(Canvas c, String text, float x, float y, float size, int color,
                              boolean bold, Paint.Align align) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTextAlign(align);
            paint.setTypeface(bold ? BOLD : REGULAR);
            c.drawText(text, x, y, paint);
        }

        private RectF targetAt(float x, float y) {
            if (pauseRect.contains(x, y)) return pauseRect;
            if (restartRect.contains(x, y)) return restartRect;
            if ((paused || gameOver) && playAgainRect.contains(x, y)) return playAgainRect;
            if (upRect.contains(x, y)) return upRect;
            if (leftRect.contains(x, y)) return leftRect;
            if (downRect.contains(x, y)) return downRect;
            if (rightRect.contains(x, y)) return rightRect;
            return null;
        }

        private boolean handleImmediateControl(RectF target) {
            if (target == upRect) { queueDirection(0, -1); return true; }
            if (target == downRect) { queueDirection(0, 1); return true; }
            if (target == leftRect) { queueDirection(-1, 0); return true; }
            if (target == rightRect) { queueDirection(1, 0); return true; }
            return false;
        }

        private void processSwipe(float x, float y) {
            float sx = x - swipeAnchorX;
            float sy = y - swipeAnchorY;
            float ax = Math.abs(sx);
            float ay = Math.abs(sy);
            if (Math.max(ax, ay) < swipeThreshold) return;

            if (ax > ay) queueDirection(sx > 0 ? 1 : -1, 0);
            else queueDirection(0, sy > 0 ? 1 : -1);

            swipeAnchorX = x;
            swipeAnchorY = y;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    touchDownX = x;
                    touchDownY = y;
                    swipeAnchorX = x;
                    swipeAnchorY = y;
                    swipeArmed = true;
                    pressedRect = targetAt(x, y);

                    if (handleImmediateControl(pressedRect)) {
                        postInvalidateOnAnimation();
                        return true;
                    }
                    postInvalidateOnAnimation();
                    return true;
                }

                case MotionEvent.ACTION_MOVE: {
                    RectF hover = targetAt(x, y);
                    if (hover != pressedRect && (pressedRect == pauseRect || pressedRect == restartRect || pressedRect == playAgainRect)) {
                        pressedRect = hover;
                    }
                    if (swipeArmed && pressedRect == null) processSwipe(x, y);
                    postInvalidateOnAnimation();
                    return true;
                }

                case MotionEvent.ACTION_UP: {
                    RectF released = targetAt(x, y);
                    if (released == pauseRect && pressedRect == pauseRect) {
                        if (!gameOver) paused = !paused;
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    } else if (released == restartRect && pressedRect == restartRect) {
                        restartGame();
                    } else if (released == playAgainRect && pressedRect == playAgainRect) {
                        if (gameOver) restartGame();
                        else paused = false;
                    } else if (gameOver && overlayRect.contains(x, y)) {
                        restartGame();
                    } else if (paused && boardRect.contains(x, y)) {
                        paused = false;
                    } else if (swipeArmed && Math.hypot(x - touchDownX, y - touchDownY) >= swipeThreshold) {
                        processSwipe(x, y);
                    }

                    pressedRect = null;
                    swipeArmed = false;
                    postInvalidateOnAnimation();
                    return true;
                }

                case MotionEvent.ACTION_CANCEL:
                    pressedRect = null;
                    swipeArmed = false;
                    postInvalidateOnAnimation();
                    return true;
            }
            return true;
        }

        void pauseForLifecycle() {
            lifecyclePaused = true;
            if (!gameOver) paused = true;
            postInvalidateOnAnimation();
        }

        void resumeForLifecycle() {
            lifecyclePaused = false;
            lastFrameNanos = 0L;
            postInvalidateOnAnimation();
        }

        @Override
        protected void onDetachedFromWindow() {
            frameLoopRunning = false;
            Choreographer.getInstance().removeFrameCallback(this);
            super.onDetachedFromWindow();
        }
    }
}
