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
import android.widget.ImageView;
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
    private float initialTouchX, initialTouchY;
    private float gestureInitialScaleX, gestureInitialScaleY;
    private float gestureInitialX, gestureInitialY;
    private int gestureInitialWidth, gestureInitialHeight;
    private RectF gestureInitialBounds;
    private boolean hasPassedThreshold = false;
    private int activeHandle = -1;
    private boolean canResizeHorizontal = true;
    private boolean canResizeVertical = true;

    private static final float MOVE_THRESHOLD_DP = 8f;
    private static final float SMOOTHING_FACTOR = 1.0f;

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
    private static final int ACTION_OUTSIDE = 10;

    private final float initialRotation, initialScaleX, initialScaleY, initialX, initialY;
    private final OnSaveListener onSaveListener;

    public interface OnSaveListener {
        void onMove(float x, float y);
        void onSave();
        void onCancel();
        void onRemove();
        void onAppInfo();
        void onCollision(View otherView);
        View findItemAt(float x, float y, View exclude);
    }

    public TransformOverlay(Context context, View targetView, SettingsManager settingsManager, OnSaveListener listener) {
        super(context);
        this.targetView = targetView;
        this.settingsManager = settingsManager;
        this.item = (HomeItem) targetView.getTag();
        this.onSaveListener = listener;
        this.handleSize = dpToPx(12);
        this.rotationHandleDist = dpToPx(50);

        this.initialRotation = targetView.getRotation();
        this.initialScaleX = targetView.getScaleX();
        this.initialScaleY = targetView.getScaleY();
        this.initialX = targetView.getX();
        this.initialY = targetView.getY();

        if (targetView instanceof android.appwidget.AppWidgetHostView) {
            android.appwidget.AppWidgetProviderInfo info = ((android.appwidget.AppWidgetHostView) targetView).getAppWidgetInfo();
            if (info != null) {
                canResizeHorizontal = (info.resizeMode & android.appwidget.AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0;
                canResizeVertical = (info.resizeMode & android.appwidget.AppWidgetProviderInfo.RESIZE_VERTICAL) != 0;
            }
        }

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
        int adaptiveColor = ThemeUtils.getAdaptiveColor(getContext(), settingsManager, true);

        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        container.setBackground(ThemeUtils.getGlassDrawable(getContext(), settingsManager, 12));
        container.setOnClickListener(v -> {});

        TextView btnRemove = new TextView(getContext());
        btnRemove.setText("REMOVE");
        btnRemove.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        btnRemove.setTextColor(adaptiveColor);
        btnRemove.setTextSize(11);
        btnRemove.setTypeface(null, android.graphics.Typeface.BOLD);
        btnRemove.setGravity(Gravity.CENTER);
        btnRemove.setOnClickListener(v -> { if (onSaveListener != null) onSaveListener.onRemove(); });
        LinearLayout.LayoutParams lpRemove = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        container.addView(btnRemove, lpRemove);

        TextView btnReset = new TextView(getContext());
        btnReset.setText("RESET");
        btnReset.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        btnReset.setTextColor(adaptiveColor);
        btnReset.setTextSize(11);
        btnReset.setTypeface(null, android.graphics.Typeface.BOLD);
        btnReset.setGravity(Gravity.CENTER);
        btnReset.setOnClickListener(v -> reset());
        LinearLayout.LayoutParams lpReset = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        container.addView(btnReset, lpReset);

        TextView btnSave = new TextView(getContext());
        btnSave.setText("SAVE");
        btnSave.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        btnSave.setTextColor(adaptiveColor);
        btnSave.setTextSize(11);
        btnSave.setTypeface(null, android.graphics.Typeface.BOLD);
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setOnClickListener(v -> save());
        LinearLayout.LayoutParams lpSave = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        container.addView(btnSave, lpSave);

        if (item.type != HomeItem.Type.WIDGET && item.type != HomeItem.Type.FOLDER) {
            TextView btnInfo = new TextView(getContext());
            btnInfo.setText("APP INFO");
            btnInfo.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            btnInfo.setTextColor(adaptiveColor);
            btnInfo.setTextSize(11);
            btnInfo.setTypeface(null, android.graphics.Typeface.BOLD);
            btnInfo.setGravity(Gravity.CENTER);
            btnInfo.setOnClickListener(v -> { if (onSaveListener != null) onSaveListener.onAppInfo(); });
            LinearLayout.LayoutParams lpInfo = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
            container.addView(btnInfo, lpInfo);
        }

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
        if (onSaveListener != null) onSaveListener.onSave();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (targetView == null) return;

        boolean isFreeform = settingsManager.isFreeformHome();

        float sx = targetView.getScaleX();
        float sy = targetView.getScaleY();
        float r = isFreeform ? targetView.getRotation() : 0;

        float cx = targetView.getX() + targetView.getPivotX();
        float cy = targetView.getY() + targetView.getPivotY();

        RectF bounds = (activeHandle != -1 && gestureInitialBounds != null) ? gestureInitialBounds : getContentBounds();
        float left = (bounds.left - targetView.getPivotX()) * sx;
        float top = (bounds.top - targetView.getPivotY()) * sy;
        float right = (bounds.right - targetView.getPivotX()) * sx;
        float bottom = (bounds.bottom - targetView.getPivotY()) * sy;

        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(r);

        int foregroundColor = ThemeUtils.getAdaptiveColor(getContext(), settingsManager, false);

        // Bounding Box - Thin and subtle
        paint.setColor(foregroundColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpToPx(1));
        paint.setAlpha(60);
        canvas.drawRect(left, top, right, bottom, paint);

        float hs = handleSize / 2f;

        // Corners - Zoom in/out handles - Only in Freeform Mode
        if (isFreeform) {
            drawHandle(canvas, left, top, hs, true, foregroundColor);
            drawHandle(canvas, right, top, hs, true, foregroundColor);
            drawHandle(canvas, right, bottom, hs, true, foregroundColor);
            drawHandle(canvas, left, bottom, hs, true, foregroundColor);
        }

        // Rotation Handle - Only in freeform mode
        if (isFreeform) {
            paint.setColor(foregroundColor);
            paint.setAlpha(100);
            canvas.drawLine((left + right) / 2f, top, (left + right) / 2f, top - rotationHandleDist, paint);
            drawHandle(canvas, (left + right) / 2f, top - rotationHandleDist, hs * 1.1f, true, foregroundColor);
        }

        canvas.restore();
    }

    private void drawHandle(Canvas canvas, float cx, float cy, float radius, boolean isPrimary, int foregroundColor) {
        // Hollow center effect
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setAlpha(isPrimary ? 200 : 120);
        canvas.drawCircle(cx, cy, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(foregroundColor);
        paint.setStrokeWidth(dpToPx(1));
        paint.setAlpha(isPrimary ? 200 : 150);
        canvas.drawCircle(cx, cy, radius, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                activeHandle = findHandle(x, y);
                if (activeHandle == ACTION_OUTSIDE) {
                    if (onSaveListener != null) {
                        View other = onSaveListener.findItemAt(x, y, targetView);
                        if (other != null) {
                            onSaveListener.onCollision(other);
                            return true;
                        }
                        onSaveListener.onSave();
                    }
                    return false;
                }
                initialTouchX = x;
                initialTouchY = y;
                lastTouchX = x;
                lastTouchY = y;
                gestureInitialScaleX = targetView.getScaleX();
                gestureInitialScaleY = targetView.getScaleY();
                gestureInitialX = targetView.getX();
                gestureInitialY = targetView.getY();
                gestureInitialWidth = targetView.getWidth();
                gestureInitialHeight = targetView.getHeight();
                gestureInitialBounds = getContentBounds();
                hasPassedThreshold = false;
                return activeHandle != -1;

            case MotionEvent.ACTION_MOVE:
                if (activeHandle != -1) {
                    if (!hasPassedThreshold) {
                        float threshold = dpToPx((int) MOVE_THRESHOLD_DP);
                        if (dist(x, y, initialTouchX, initialTouchY) > threshold) {
                            hasPassedThreshold = true;
                        }
                    }

                    if (hasPassedThreshold) {
                        handleInteraction(x, y);
                    }
                    lastTouchX = x;
                    lastTouchY = y;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (activeHandle != -1 && activeHandle != ACTION_MOVE && activeHandle != HANDLE_ROTATE && activeHandle != ACTION_OUTSIDE) {
                    if (targetView instanceof android.appwidget.AppWidgetHostView) {
                        ViewGroup.LayoutParams lp = targetView.getLayoutParams();
                        int dw = (int) (lp.width / getResources().getDisplayMetrics().density);
                        int dh = (int) (lp.height / getResources().getDisplayMetrics().density);
                        ((android.appwidget.AppWidgetHostView) targetView).updateAppWidgetSize(null, dw, dh, dw, dh);
                    }
                }
                activeHandle = -1;
                hasPassedThreshold = false;
                return true;
        }
        return true;
    }

    private int findHandle(float tx, float ty) {
        boolean isFreeform = settingsManager.isFreeformHome();
        float sx = targetView.getScaleX();
        float sy = targetView.getScaleY();

        float cx = targetView.getX() + targetView.getPivotX();
        float cy = targetView.getY() + targetView.getPivotY();

        double angle = isFreeform ? Math.toRadians(-targetView.getRotation()) : 0;
        float rx = (float) (Math.cos(angle) * (tx - cx) - Math.sin(angle) * (ty - cy));
        float ry = (float) (Math.sin(angle) * (tx - cx) + Math.cos(angle) * (ty - cy));

        RectF bounds = getContentBounds();

        float left = (bounds.left - targetView.getPivotX()) * sx;
        float top = (bounds.top - targetView.getPivotY()) * sy;
        float right = (bounds.right - targetView.getPivotX()) * sx;
        float bottom = (bounds.bottom - targetView.getPivotY()) * sy;

        float hs = dpToPx(24); // High precision touch area

        // Rotation handle first
        if ((isFreeform || item.type == HomeItem.Type.WIDGET || item.type == HomeItem.Type.FOLDER) && dist(rx, ry, (left + right) / 2f, top - rotationHandleDist) < hs) return HANDLE_ROTATE;

        // Corners - Proportional scale - Only in Freeform Mode
        if (isFreeform && canResizeHorizontal && canResizeVertical) {
            if (dist(rx, ry, left, top) < hs) return HANDLE_TOP_LEFT;
            if (dist(rx, ry, right, top) < hs) return HANDLE_TOP_RIGHT;
            if (dist(rx, ry, right, bottom) < hs) return HANDLE_BOTTOM_RIGHT;
            if (dist(rx, ry, left, bottom) < hs) return HANDLE_BOTTOM_LEFT;
        }

        // Move action if inside the box
        if (rx >= left && rx <= right && ry >= top && ry <= bottom) return ACTION_MOVE;

        return ACTION_OUTSIDE;
    }

    private void handleInteraction(float tx, float ty) {
        boolean isFreeform = settingsManager.isFreeformHome();
        float sx = gestureInitialScaleX;
        float sy = gestureInitialScaleY;
        float cx = gestureInitialX + targetView.getPivotX();
        float cy = gestureInitialY + targetView.getPivotY();

        if (activeHandle == ACTION_MOVE) {
            float newX = gestureInitialX + (tx - initialTouchX);
            float newY = gestureInitialY + (ty - initialTouchY);

            // Screen clamping
            newX = Math.max(0, Math.min(newX, getWidth() - targetView.getWidth()));
            newY = Math.max(0, Math.min(newY, getHeight() - targetView.getHeight()));

            if (!isFreeform) {
                int cellWidth = getWidth() / HomeView.GRID_COLUMNS;
                int cellHeight = getHeight() / HomeView.GRID_ROWS;
                if (cellWidth > 0 && cellHeight > 0) {
                    newX = Math.round(newX / (float) cellWidth) * cellWidth;
                    newY = Math.round(newY / (float) cellHeight) * cellHeight;
                }
            }

            targetView.setX(newX);
            targetView.setY(newY);
            if (onSaveListener != null) onSaveListener.onMove(tx, ty);
        } else if (activeHandle == HANDLE_ROTATE && (isFreeform || item.type == HomeItem.Type.FOLDER || item.type == HomeItem.Type.WIDGET)) {
            double angle = Math.toDegrees(Math.atan2(ty - cy, tx - cx)) + 90;

            float targetR = (float) angle;
            float currentR = targetView.getRotation();

            while (targetR - currentR > 180) targetR -= 360;
            while (targetR - currentR < -180) targetR += 360;

            targetView.setRotation(currentR + (targetR - currentR) * SMOOTHING_FACTOR);
        } else {
            double rotAngle = Math.toRadians(-targetView.getRotation());
            float rx = (float) (Math.cos(rotAngle) * (tx - cx) - Math.sin(rotAngle) * (ty - cy));
            float ry = (float) (Math.sin(rotAngle) * (tx - cx) + Math.cos(rotAngle) * (ty - cy));

            RectF bounds = gestureInitialBounds != null ? gestureInitialBounds : getContentBounds();
            float halfContentW = bounds.width() / 2f;
            float halfContentH = bounds.height() / 2f;

            float newScaleX = sx;
            float newScaleY = sy;

            switch (activeHandle) {
                case HANDLE_TOP:
                case HANDLE_BOTTOM:
                    if (halfContentH > 0 && canResizeVertical)
                        newScaleY = Math.max(0.2f, Math.min(5.0f, Math.abs(ry) / halfContentH));
                    break;
                case HANDLE_LEFT:
                case HANDLE_RIGHT:
                    if (halfContentW > 0 && canResizeHorizontal)
                        newScaleX = Math.max(0.2f, Math.min(5.0f, Math.abs(rx) / halfContentW));
                    break;
                case HANDLE_TOP_LEFT:
                case HANDLE_TOP_RIGHT:
                case HANDLE_BOTTOM_LEFT:
                case HANDLE_BOTTOM_RIGHT:
                    if (canResizeHorizontal && canResizeVertical) {
                        float initialDist = dist(initialTouchX, initialTouchY, cx, cy);
                        float currDist = dist(tx, ty, cx, cy);
                        if (initialDist > 0) {
                            float factor = currDist / initialDist;
                            newScaleX = Math.max(0.2f, Math.min(5.0f, sx * factor));
                            newScaleY = Math.max(0.2f, Math.min(5.0f, sy * factor));
                        }
                    }
                    break;
            }

            // Bounds clamping for symmetrical scaling
            if (gestureInitialWidth > 0 && gestureInitialHeight > 0) {
                float maxSX = Math.min(5.0f, Math.min(2 * cx, 2 * (getWidth() - cx)) * gestureInitialScaleX / (float) gestureInitialWidth);
                float maxSY = Math.min(5.0f, Math.min(2 * cy, 2 * (getHeight() - cy)) * gestureInitialScaleY / (float) gestureInitialHeight);
                newScaleX = Math.min(newScaleX, maxSX);
                newScaleY = Math.min(newScaleY, maxSY);
            }

            if (!isFreeform) {
                int cellWidth = getWidth() / HomeView.GRID_COLUMNS;
                int cellHeight = getHeight() / HomeView.GRID_ROWS;
                if (cellWidth > 0 && cellHeight > 0) {
                    float targetW = newScaleX * (gestureInitialWidth / gestureInitialScaleX);
                    float targetH = newScaleY * (gestureInitialHeight / gestureInitialScaleY);
                    targetW = Math.max(cellWidth, Math.round(targetW / (float) cellWidth) * cellWidth);
                    targetH = Math.max(cellHeight, Math.round(targetH / (float) cellHeight) * cellHeight);
                    newScaleX = targetW * gestureInitialScaleX / (float) gestureInitialWidth;
                    newScaleY = targetH * gestureInitialScaleY / (float) gestureInitialHeight;
                }
            } else {
                newScaleX = Math.round(newScaleX * 100f) / 100.0f;
                newScaleY = Math.round(newScaleY * 100f) / 100.0f;
            }

            if (targetView instanceof android.appwidget.AppWidgetHostView || item.type == HomeItem.Type.FOLDER) {
                ViewGroup.LayoutParams lp = targetView.getLayoutParams();
                int oldW = lp.width;
                int oldH = lp.height;
                lp.width = (int) (newScaleX * (gestureInitialWidth / gestureInitialScaleX));
                lp.height = (int) (newScaleY * (gestureInitialHeight / gestureInitialScaleY));
                targetView.setLayoutParams(lp);

                targetView.setX(targetView.getX() + (oldW - lp.width) / 2f);
                targetView.setY(targetView.getY() + (oldH - lp.height) / 2f);

                targetView.setScaleX(1.0f);
                targetView.setScaleY(1.0f);

                if (targetView instanceof android.appwidget.AppWidgetHostView) {
                    int dw = (int) (lp.width / getResources().getDisplayMetrics().density);
                    int dh = (int) (lp.height / getResources().getDisplayMetrics().density);
                    ((android.appwidget.AppWidgetHostView) targetView).updateAppWidgetSize(null, dw, dh, dw, dh);
                }
            } else {
                targetView.setScaleX(newScaleX);
                targetView.setScaleY(newScaleY);
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
