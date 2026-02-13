package com.riprog.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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
    private final SettingsManager settingsManager;
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

    private final float initialRotation, initialScaleX, initialScaleY, initialX, initialY;
    private final OnSaveListener onSaveListener;

    public interface OnSaveListener {
        void onSave();
        void onCancel();
        void onRemove();
        void onAppInfo();
    }

    public TransformOverlay(Context context, View targetView, SettingsManager settingsManager, OnSaveListener listener) {
        super(context);
        this.targetView = targetView;
        this.settingsManager = settingsManager;
        this.item = (HomeItem) targetView.getTag();
        this.onSaveListener = listener;
        this.handleSize = dpToPx(12);
        this.rotationHandleDist = dpToPx(30);

        this.initialRotation = targetView.getRotation();
        this.initialScaleX = targetView.getScaleX();
        this.initialScaleY = targetView.getScaleY();
        this.initialX = targetView.getX();
        this.initialY = targetView.getY();

        setWillNotDraw(false);
        setupButtons();
    }

    private RectF getContentBounds() {
        if (!(targetView instanceof ViewGroup)) {
            return new RectF(0, 0, targetView.getWidth(), targetView.getHeight());
        }
        ViewGroup vg = (ViewGroup) targetView;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
        boolean hasVisibleChildren = false;

        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                minX = Math.min(minX, child.getX());
                minY = Math.min(minY, child.getY());
                maxX = Math.max(maxX, child.getX() + child.getWidth());
                maxY = Math.max(maxY, child.getY() + child.getHeight());
                hasVisibleChildren = true;
            }
        }

        if (!hasVisibleChildren) {
            return new RectF(0, 0, targetView.getWidth(), targetView.getHeight());
        }
        return new RectF(minX, minY, maxX, maxY);
    }

    private void setupButtons() {
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        container.setBackground(ThemeUtils.getGlassDrawable(getContext(), settingsManager, 12));

        TextView btnRemove = new TextView(getContext());
        btnRemove.setText("REMOVE");
        btnRemove.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        btnRemove.setTextColor(Color.parseColor("#FF5252"));
        btnRemove.setTextSize(11);
        btnRemove.setTypeface(null, android.graphics.Typeface.BOLD);
        btnRemove.setGravity(Gravity.CENTER);
        btnRemove.setOnClickListener(v -> { if (onSaveListener != null) onSaveListener.onRemove(); });
        LinearLayout.LayoutParams lpRemove = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        container.addView(btnRemove, lpRemove);

        TextView btnReset = new TextView(getContext());
        btnReset.setText("RESET");
        btnReset.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        btnReset.setTextColor(getContext().getColor(R.color.foreground));
        btnReset.setTextSize(11);
        btnReset.setTypeface(null, android.graphics.Typeface.BOLD);
        btnReset.setGravity(Gravity.CENTER);
        btnReset.setOnClickListener(v -> reset());
        LinearLayout.LayoutParams lpReset = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        container.addView(btnReset, lpReset);

        TextView btnSave = new TextView(getContext());
        btnSave.setText("SAVE");
        btnSave.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        btnSave.setTextColor(Color.parseColor("#4CAF50"));
        btnSave.setTextSize(11);
        btnSave.setTypeface(null, android.graphics.Typeface.BOLD);
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setOnClickListener(v -> save());
        LinearLayout.LayoutParams lpSave = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        container.addView(btnSave, lpSave);

        TextView btnInfo = new TextView(getContext());
        btnInfo.setText("APP INFO");
        btnInfo.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        btnInfo.setTextColor(getContext().getColor(R.color.foreground));
        btnInfo.setTextSize(11);
        btnInfo.setTypeface(null, android.graphics.Typeface.BOLD);
        btnInfo.setGravity(Gravity.CENTER);
        btnInfo.setOnClickListener(v -> { if (onSaveListener != null) onSaveListener.onAppInfo(); });
        LinearLayout.LayoutParams lpInfo = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        container.addView(btnInfo, lpInfo);

        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dpToPx(48);
        lp.leftMargin = dpToPx(24);
        lp.rightMargin = dpToPx(24);
        addView(container, lp);
    }

    private void reset() {
        targetView.setRotation(initialRotation);
        targetView.setScaleX(initialScaleX);
        targetView.setScaleY(initialScaleY);
        targetView.setX(initialX);
        targetView.setY(initialY);
        invalidate();
    }

    private void save() {
        item.rotation = targetView.getRotation();
        item.scaleX = targetView.getScaleX();
        item.scaleY = targetView.getScaleY();

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
        float sx = targetView.getScaleX();
        float sy = targetView.getScaleY();
        float r = targetView.getRotation();

        RectF bounds = getContentBounds();
        float left = (bounds.left - w / 2f) * sx;
        float top = (bounds.top - h / 2f) * sy;
        float right = (bounds.right - w / 2f) * sx;
        float bottom = (bounds.bottom - h / 2f) * sy;

        canvas.save();
        canvas.translate(x + (w / 2f), y + (h / 2f));
        canvas.rotate(r);

        paint.setColor(getContext().getColor(R.color.foreground));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpToPx(1));
        paint.setAlpha(80);
        canvas.drawRect(left, top, right, bottom, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(200);
        float hs = handleSize / 2f;

        // Corners
        canvas.drawCircle(left, top, hs, paint);
        canvas.drawCircle(right, top, hs, paint);
        canvas.drawCircle(right, bottom, hs, paint);
        canvas.drawCircle(left, bottom, hs, paint);

        // Sides
        canvas.drawCircle((left + right) / 2f, top, hs, paint);
        canvas.drawCircle(right, (top + bottom) / 2f, hs, paint);
        canvas.drawCircle((left + right) / 2f, bottom, hs, paint);
        canvas.drawCircle(left, (top + bottom) / 2f, hs, paint);

        paint.setColor(getContext().getColor(R.color.foreground));
        paint.setAlpha(255);
        canvas.drawLine((left + right) / 2f, top, (left + right) / 2f, top - rotationHandleDist, paint);
        canvas.drawCircle((left + right) / 2f, top - rotationHandleDist, hs * 1.2f, paint);

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

        RectF bounds = getContentBounds();
        float w = targetView.getWidth();
        float h = targetView.getHeight();
        float sx = targetView.getScaleX();
        float sy = targetView.getScaleY();

        float left = (bounds.left - w / 2f) * sx;
        float top = (bounds.top - h / 2f) * sy;
        float right = (bounds.right - w / 2f) * sx;
        float bottom = (bounds.bottom - h / 2f) * sy;

        float hs = dpToPx(24); // High precision touch area

        // Rotation handle first
        if (dist(rx, ry, (left + right) / 2f, top - rotationHandleDist) < hs) return HANDLE_ROTATE;

        // Corners
        if (dist(rx, ry, left, top) < hs) return HANDLE_TOP_LEFT;
        if (dist(rx, ry, right, top) < hs) return HANDLE_TOP_RIGHT;
        if (dist(rx, ry, right, bottom) < hs) return HANDLE_BOTTOM_RIGHT;
        if (dist(rx, ry, left, bottom) < hs) return HANDLE_BOTTOM_LEFT;

        // Sides
        if (dist(rx, ry, (left + right) / 2f, top) < hs) return HANDLE_TOP;
        if (dist(rx, ry, right, (top + bottom) / 2f) < hs) return HANDLE_RIGHT;
        if (dist(rx, ry, (left + right) / 2f, bottom) < hs) return HANDLE_BOTTOM;
        if (dist(rx, ry, left, (top + bottom) / 2f) < hs) return HANDLE_LEFT;

        // Move action if inside the box
        if (rx >= left && rx <= right && ry >= top && ry <= bottom) return ACTION_MOVE;

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
            int[] pos = new int[2];
            targetView.getLocationOnScreen(pos);
            int[] myPos = new int[2];
            getLocationOnScreen(myPos);
            float cx = pos[0] - myPos[0] + (targetView.getWidth() / 2f);
            float cy = pos[1] - myPos[1] + (targetView.getHeight() / 2f);

            double angle = Math.toRadians(-targetView.getRotation());
            float rx = (float) (Math.cos(angle) * (tx - cx) - Math.sin(angle) * (ty - cy));
            float ry = (float) (Math.sin(angle) * (tx - cx) + Math.cos(angle) * (ty - cy));

            RectF bounds = getContentBounds();
            float halfContentW = bounds.width() / 2f;
            float halfContentH = bounds.height() / 2f;

            switch (activeHandle) {
                case HANDLE_TOP:
                case HANDLE_BOTTOM:
                    if (halfContentH > 0)
                        targetView.setScaleY(Math.max(0.2f, Math.min(5.0f, Math.abs(ry) / halfContentH)));
                    break;
                case HANDLE_LEFT:
                case HANDLE_RIGHT:
                    if (halfContentW > 0)
                        targetView.setScaleX(Math.max(0.2f, Math.min(5.0f, Math.abs(rx) / halfContentW)));
                    break;
                case HANDLE_TOP_LEFT:
                case HANDLE_TOP_RIGHT:
                case HANDLE_BOTTOM_LEFT:
                case HANDLE_BOTTOM_RIGHT:
                    float lastDist = dist(lastTouchX, lastTouchY, cx, cy);
                    float currDist = dist(tx, ty, cx, cy);
                    if (lastDist > 0) {
                        float factor = currDist / lastDist;
                        targetView.setScaleX(Math.max(0.2f, Math.min(5.0f, targetView.getScaleX() * factor)));
                        targetView.setScaleY(Math.max(0.2f, Math.min(5.0f, targetView.getScaleY() * factor)));
                    }
                    break;
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
