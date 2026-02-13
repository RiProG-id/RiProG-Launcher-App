package com.riprog.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class TransformOverlay extends FrameLayout {
    private final View targetView;
    private final HomeItem item;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float handleSize;
    private final float rotationHandleDist;

    private float lastTouchX, lastTouchY;
    private int activeHandle = -1;

    private static final int HANDLE_TOP_LEFT = 0;
    private static final int HANDLE_TOP = 1;
    private static final int HANDLE_TOP_RIGHT = 2;
    private static final int HANDLE_RIGHT = 3;
    private static final int HANDLE_BOTTOM_RIGHT = 4;
    private static final int HANDLE_BOTTOM = 5;
    private static final int HANDLE_BOTTOM_LEFT = 6;
    private static final int HANDLE_LEFT = 7;
    private static final int HANDLE_ROTATE = 8;
    private static final int ACTION_MOVE = 9;

    private final float initialRotation, initialScale, initialX, initialY;
    private final OnSaveListener onSaveListener;

    public interface OnSaveListener {
        void onSave();
        void onCancel();
    }

    public TransformOverlay(Context context, View targetView, OnSaveListener listener) {
        super(context);
        this.targetView = targetView;
        this.item = (HomeItem) targetView.getTag();
        this.onSaveListener = listener;
        this.handleSize = dpToPx(12);
        this.rotationHandleDist = dpToPx(30);

        this.initialRotation = targetView.getRotation();
        this.initialScale = targetView.getScaleX();
        this.initialX = targetView.getX();
        this.initialY = targetView.getY();

        setWillNotDraw(false);
        setupButtons();
    }

    private void setupButtons() {
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        container.setBackground(ThemeUtils.getGlassDrawable(getContext(), new SettingsManager(getContext())));

        TextView btnReset = new TextView(getContext());
        btnReset.setText("RESET");
        btnReset.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        btnReset.setTextColor(Color.RED);
        btnReset.setOnClickListener(v -> reset());
        container.addView(btnReset);

        TextView btnSave = new TextView(getContext());
        btnSave.setText("SAVE");
        btnSave.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        btnSave.setTextColor(Color.GREEN);
        btnSave.setOnClickListener(v -> save());
        container.addView(btnSave);

        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dpToPx(100);
        addView(container, lp);
    }

    private void reset() {
        targetView.setRotation(initialRotation);
        targetView.setScaleX(initialScale);
        targetView.setScaleY(initialScale);
        targetView.setX(initialX);
        targetView.setY(initialY);
        invalidate();
    }

    private void save() {
        item.rotation = targetView.getRotation();
        item.scale = targetView.getScaleX();

        View parent = (View) targetView.getParent();
        if (parent != null) {
            int cellWidth = parent.getWidth() / HomeView.GRID_COLUMNS;
            int cellHeight = parent.getHeight() / HomeView.GRID_ROWS;
            if (cellWidth > 0) item.col = targetView.getX() / (float) cellWidth;
            if (cellHeight > 0) item.row = targetView.getY() / (float) cellHeight;
        }

        if (onSaveListener != null) onSaveListener.onSave();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (targetView == null) return;

        int[] pos = new int[2];
        targetView.getLocationOnScreen(pos);
        int[] myPos = new int[2];
        getLocationOnScreen(myPos);

        float x = pos[0] - myPos[0];
        float y = pos[1] - myPos[1];
        float w = targetView.getWidth();
        float h = targetView.getHeight();
        float s = targetView.getScaleX();
        float r = targetView.getRotation();

        canvas.save();
        canvas.translate(x + (w/2f), y + (h/2f));
        canvas.rotate(r);
        canvas.scale(s, s);

        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpToPx(2) / s);
        canvas.drawRect(-w/2f, -h/2f, w/2f, h/2f, paint);

        paint.setStyle(Paint.Style.FILL);
        float hs = handleSize / s;

        canvas.drawCircle(-w/2f, -h/2f, hs, paint);
        canvas.drawCircle(w/2f, -h/2f, hs, paint);
        canvas.drawCircle(w/2f, h/2f, hs, paint);
        canvas.drawCircle(-w/2f, h/2f, hs, paint);

        canvas.drawCircle(0, -h/2f, hs, paint);
        canvas.drawCircle(w/2f, 0, hs, paint);
        canvas.drawCircle(0, h/2f, hs, paint);
        canvas.drawCircle(-w/2f, 0, hs, paint);

        paint.setColor(Color.YELLOW);
        canvas.drawLine(0, -h/2f, 0, -h/2f - rotationHandleDist/s, paint);
        canvas.drawCircle(0, -h/2f - rotationHandleDist/s, hs, paint);

        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                activeHandle = findHandle(x, y);
                lastTouchX = x;
                lastTouchY = y;
                return activeHandle != -1;

            case MotionEvent.ACTION_MOVE:
                if (activeHandle != -1) {
                    handleInteraction(x, y);
                    lastTouchX = x;
                    lastTouchY = y;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activeHandle = -1;
                return true;
        }
        return true;
    }

    private int findHandle(float tx, float ty) {
        int[] pos = new int[2];
        targetView.getLocationOnScreen(pos);
        int[] myPos = new int[2];
        getLocationOnScreen(myPos);

        float cx = pos[0] - myPos[0] + (targetView.getWidth() / 2f);
        float cy = pos[1] - myPos[1] + (targetView.getHeight() / 2f);

        double angle = Math.toRadians(-targetView.getRotation());
        float rx = (float) (Math.cos(angle) * (tx - cx) - Math.sin(angle) * (ty - cy));
        float ry = (float) (Math.sin(angle) * (tx - cx) + Math.cos(angle) * (ty - cy));

        float w = targetView.getWidth() * targetView.getScaleX();
        float h = targetView.getHeight() * targetView.getScaleY();
        float hs = handleSize * 2.0f;

        if (dist(rx, ry, 0, -h/2f - rotationHandleDist) < hs) return HANDLE_ROTATE;

        if (dist(rx, ry, -w/2f, -h/2f) < hs) return HANDLE_TOP_LEFT;
        if (dist(rx, ry, w/2f, -h/2f) < hs) return HANDLE_TOP_RIGHT;
        if (dist(rx, ry, w/2f, h/2f) < hs) return HANDLE_BOTTOM_RIGHT;
        if (dist(rx, ry, -w/2f, h/2f) < hs) return HANDLE_BOTTOM_LEFT;

        if (dist(rx, ry, 0, -h/2f) < hs) return HANDLE_TOP;
        if (dist(rx, ry, w/2f, 0) < hs) return HANDLE_RIGHT;
        if (dist(rx, ry, 0, h/2f) < hs) return HANDLE_BOTTOM;
        if (dist(rx, ry, -w/2f, 0) < hs) return HANDLE_LEFT;

        if (rx >= -w/2f && rx <= w/2f && ry >= -h/2f && ry <= h/2f) return ACTION_MOVE;

        return -1;
    }

    private void handleInteraction(float tx, float ty) {
        if (activeHandle == ACTION_MOVE) {
            targetView.setX(targetView.getX() + (tx - lastTouchX));
            targetView.setY(targetView.getY() + (ty - lastTouchY));
        } else if (activeHandle == HANDLE_ROTATE) {
            int[] pos = new int[2];
            targetView.getLocationOnScreen(pos);
            int[] myPos = new int[2];
            getLocationOnScreen(myPos);
            float cx = pos[0] - myPos[0] + (targetView.getWidth() / 2f);
            float cy = pos[1] - myPos[1] + (targetView.getHeight() / 2f);
            double angle = Math.toDegrees(Math.atan2(ty - cy, tx - cx)) + 90;
            targetView.setRotation((float) angle);
        } else {
            float dx = tx - lastTouchX;
            float dy = ty - lastTouchY;

            // Proportional scaling based on distance from center
            int[] pos = new int[2];
            targetView.getLocationOnScreen(pos);
            int[] myPos = new int[2];
            getLocationOnScreen(myPos);
            float cx = pos[0] - myPos[0] + (targetView.getWidth() / 2f);
            float cy = pos[1] - myPos[1] + (targetView.getHeight() / 2f);

            float lastDist = dist(lastTouchX, lastTouchY, cx, cy);
            float currDist = dist(tx, ty, cx, cy);

            if (lastDist > 0) {
                float factor = currDist / lastDist;
                float newScale = Math.max(0.5f, Math.min(3.0f, targetView.getScaleX() * factor));
                targetView.setScaleX(newScale);
                targetView.setScaleY(newScale);
            }
        }
    }

    private float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
