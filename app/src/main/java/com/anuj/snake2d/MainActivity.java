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
        private static final int BG = Color.rgb(7, 19, 13);
        private static final int PANEL = Color.rgb(13, 34, 23);
        private static final int PANEL_2 = Color.rgb(18, 46, 31);
        private static final int GRID_LINE = Color.rgb(24, 58, 40);
        private static final int SNAKE = Color.rgb(124, 255, 107);
        private static final int SNAKE_HEAD = Color.rgb(196, 255, 132);
        private static final int FOOD = Color.rgb(255, 91, 91);
        private static final int TEXT = Color.rgb(239, 255, 243);
        private static final int MUTED = Color.rgb(142, 176, 153);
        private static final int ACCENT = Color.rgb(84, 214, 114);

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Random random = new Random();
        private final List<Point> snake = new ArrayList<>();
        private final SharedPreferences prefs;

        private final RectF pauseRect = new RectF();
        private final RectF restartRect = new RectF();
        private final RectF upRect = new RectF();
        private final RectF downRect = new RectF();
        private final RectF leftRect = new RectF();
        private final RectF rightRect = new RectF();

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

        private final Runnable ticker = new Runnable() {
            @Override public void run() {
                if (!paused && !gameOver && !lifecyclePaused) step();
                handler.postDelayed(this, tickMs);
            }
        };

        SnakeView(Context context) {
            super(context);
            setFocusable(true);
            setBackgroundColor(BG);
            prefs = context.getSharedPreferences("snake_scores", Context.MODE_PRIVATE);
            highScore = prefs.getInt("high_score", 0);
            restartGame();
            handler.postDelayed(ticker, tickMs);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            float sidePadding = 20f;
            float maxByWidth = w - sidePadding * 2f;
            float maxByHeight = Math.max(300f, h - 365f);
            boardSize = Math.min(maxByWidth, maxByHeight);
            boardLeft = (w - boardSize) / 2f;
            boardTop = 132f;
            cell = boardSize / GRID;

            float controlsTop = boardTop + boardSize + 26f;
            float btn = Math.min(76f, (w - 80f) / 4f);
            float gap = 12f;
            float centerX = w / 2f;

            upRect.set(centerX - btn / 2f, controlsTop, centerX + btn / 2f, controlsTop + btn);
            leftRect.set(centerX - btn - gap, controlsTop + btn + gap, centerX - gap, controlsTop + btn * 2f + gap);
            downRect.set(centerX - btn / 2f, controlsTop + btn + gap, centerX + btn / 2f, controlsTop + btn * 2f + gap);
            rightRect.set(centerX + gap, controlsTop + btn + gap, centerX + btn + gap, controlsTop + btn * 2f + gap);

            pauseRect.set(20f, 72f, 116f, 116f);
            restartRect.set(w - 116f, 72f, w - 20f, 116f);
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
            spawnFood();
            invalidate();
        }

        private void spawnFood() {
            for (int tries = 0; tries < 1000; tries++) {
                Point p = new Point(random.nextInt(GRID), random.nextInt(GRID));
                boolean occupied = false;
                for (Point s : snake) {
                    if (s.x == p.x && s.y == p.y) { occupied = true; break; }
                }
                if (!occupied) { food = p; return; }
            }
        }

        private void step() {
            if (!(pendingDx == -dx && pendingDy == -dy)) {
                dx = pendingDx;
                dy = pendingDy;
            }

            Point head = snake.get(0);
            Point next = new Point(head.x + dx, head.y + dy);

            if (next.x < 0 || next.y < 0 || next.x >= GRID || next.y >= GRID || hitsSnake(next)) {
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
            if (next.x == food.x && next.y == food.y) {
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

        private boolean hitsSnake(Point p) {
            for (Point s : snake) if (s.x == p.x && s.y == p.y) return true;
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
            c.drawColor(BG);

            drawText(c, "SNAKE 2D", 20f, 43f, 27f, TEXT, true, Paint.Align.LEFT);
            drawText(c, "Score  " + score, 20f, 66f, 15f, MUTED, false, Paint.Align.LEFT);
            drawText(c, "Best  " + highScore, getWidth() - 20f, 43f, 16f, TEXT, true, Paint.Align.RIGHT);
            drawText(c, "Swipe or use controls", getWidth() - 20f, 66f, 13f, MUTED, false, Paint.Align.RIGHT);

            drawButton(c, pauseRect, paused ? "PLAY" : "PAUSE", PANEL_2, TEXT, 12f);
            drawButton(c, restartRect, "RESTART", PANEL_2, TEXT, 12f);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(PANEL);
            c.drawRoundRect(new RectF(boardLeft - 5f, boardTop - 5f, boardLeft + boardSize + 5f, boardTop + boardSize + 5f), 18f, 18f, paint);
            paint.setColor(BG);
            c.drawRoundRect(new RectF(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize), 14f, 14f, paint);

            paint.setStrokeWidth(1f);
            paint.setColor(GRID_LINE);
            for (int i = 1; i < GRID; i++) {
                float x = boardLeft + i * cell;
                float y = boardTop + i * cell;
                c.drawLine(x, boardTop, x, boardTop + boardSize, paint);
                c.drawLine(boardLeft, y, boardLeft + boardSize, y, paint);
            }

            float inset = Math.max(2f, cell * 0.12f);
            for (int i = snake.size() - 1; i >= 0; i--) {
                Point s = snake.get(i);
                float l = boardLeft + s.x * cell + inset;
                float t = boardTop + s.y * cell + inset;
                float r = boardLeft + (s.x + 1) * cell - inset;
                float b = boardTop + (s.y + 1) * cell - inset;
                paint.setColor(i == 0 ? SNAKE_HEAD : SNAKE);
                c.drawRoundRect(new RectF(l, t, r, b), cell * 0.28f, cell * 0.28f, paint);
            }

            float fx = boardLeft + (food.x + .5f) * cell;
            float fy = boardTop + (food.y + .5f) * cell;
            paint.setColor(FOOD);
            c.drawCircle(fx, fy, cell * .33f, paint);
            paint.setColor(Color.rgb(255, 210, 210));
            c.drawCircle(fx - cell * .10f, fy - cell * .11f, cell * .07f, paint);

            drawButton(c, upRect, "▲", PANEL_2, TEXT, 25f);
            drawButton(c, leftRect, "◀", PANEL_2, TEXT, 25f);
            drawButton(c, downRect, "▼", PANEL_2, TEXT, 25f);
            drawButton(c, rightRect, "▶", PANEL_2, TEXT, 25f);

            if (paused || gameOver) drawOverlay(c);
        }

        private void drawOverlay(Canvas c) {
            RectF box = new RectF(boardLeft + boardSize * .13f, boardTop + boardSize * .34f,
                    boardLeft + boardSize * .87f, boardTop + boardSize * .66f);
            paint.setColor(Color.argb(235, 10, 26, 18));
            c.drawRoundRect(box, 24f, 24f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(ACCENT);
            c.drawRoundRect(box, 24f, 24f, paint);
            paint.setStyle(Paint.Style.FILL);

            float cx = box.centerX();
            if (gameOver) {
                drawText(c, "GAME OVER", cx, box.top + 55f, 28f, TEXT, true, Paint.Align.CENTER);
                drawText(c, "Score  " + score + "   •   Best  " + highScore, cx, box.top + 86f, 16f, MUTED, false, Paint.Align.CENTER);
                drawText(c, "Tap RESTART to play again", cx, box.bottom - 30f, 14f, SNAKE, true, Paint.Align.CENTER);
            } else {
                drawText(c, "PAUSED", cx, box.top + 66f, 28f, TEXT, true, Paint.Align.CENTER);
                drawText(c, "Swipe or tap PLAY", cx, box.bottom - 42f, 15f, MUTED, false, Paint.Align.CENTER);
            }
        }

        private void drawButton(Canvas c, RectF rect, String label, int fill, int color, float textSize) {
            paint.setColor(fill);
            paint.setStyle(Paint.Style.FILL);
            c.drawRoundRect(rect, 16f, 16f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(GRID_LINE);
            c.drawRoundRect(rect, 16f, 16f, paint);
            paint.setStyle(Paint.Style.FILL);
            Paint.FontMetrics fm = paint.getFontMetrics();
            drawText(c, label, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2f, textSize, color, true, Paint.Align.CENTER);
        }

        private void drawText(Canvas c, String text, float x, float y, float size, int color, boolean bold, Paint.Align align) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTextAlign(align);
            paint.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
            c.drawText(text, x, y, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchDownX = event.getX();
                touchDownY = event.getY();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float x = event.getX(), y = event.getY();
                float sx = x - touchDownX, sy = y - touchDownY;

                if (pauseRect.contains(x, y)) {
                    if (!gameOver) paused = !paused;
                    invalidate();
                    return true;
                }
                if (restartRect.contains(x, y)) {
                    restartGame();
                    return true;
                }
                if (upRect.contains(x, y)) { setDirection(0, -1); return true; }
                if (downRect.contains(x, y)) { setDirection(0, 1); return true; }
                if (leftRect.contains(x, y)) { setDirection(-1, 0); return true; }
                if (rightRect.contains(x, y)) { setDirection(1, 0); return true; }

                if (gameOver && x >= boardLeft && x <= boardLeft + boardSize && y >= boardTop && y <= boardTop + boardSize) {
                    restartGame();
                    return true;
                }

                if (Math.max(Math.abs(sx), Math.abs(sy)) > 34f) {
                    if (Math.abs(sx) > Math.abs(sy)) setDirection(sx > 0 ? 1 : -1, 0);
                    else setDirection(0, sy > 0 ? 1 : -1);
                }
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
