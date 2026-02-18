package com.riprog.launcher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

@SuppressLint("ViewConstructor")
public class TransformOverlay extends FrameLayout {

    private final View targetView;
    private final SettingsManager settingsManager;
    private final OnSaveListener onSaveListener;
    private final HomeItem item;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float handleSize;
    private final float rotationHandleDist;

    private float lastTouchX = 0f;
    private float lastTouchY = 0f;
    private float initialTouchX = 0f;
    private float initialTouchY = 0f;
    private float gestureInitialScaleX = 0f;
    private float gestureInitialScaleY = 0f;
    private float gestureInitialX = 0f;
    private float gestureInitialY = 0f;
    private int gestureInitialWidth = 0;
    private int gestureInitialHeight = 0;
    private RectF gestureInitialBounds = null;
    private boolean hasPassedThreshold = false;
    private int activeHandle = -1;
    private boolean canResizeHorizontal = true;
    private boolean canResizeVertical = true;

    private final float initialRotation;
    private final float initialScaleX;
    private final float initialScaleY;
    private final float initialX;
    private final float initialY;

    public interface OnSaveListener {
        void onMove(float x, float y);
        void onMoveStart(float x, float y);
        void onSave(View collisionView);
        void onRemove();
        void onAppInfo();
        void onCollision(View otherView);
        View findItemAt(float x, float y, View exclude);
    }

    public TransformOverlay(Context context, View targetView, SettingsManager settingsManager, OnSaveListener onSaveListener) {
        super(context);
        this.targetView = targetView;
        this.settingsManager = settingsManager;
        this.onSaveListener = onSaveListener;
        this.item = (HomeItem) targetView.getTag();
        this.handleSize = ThemeUtils.dpToPx(context, 12);
        this.rotationHandleDist = ThemeUtils.dpToPx(context, 50);

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

    public void startDirectMove(float x, float y) {
        if (onSaveListener != null) onSaveListener.onMoveStart(x, y);
        activeHandle = ACTION_MOVE;
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
        hasPassedThreshold = true;
        invalidate();
    }

    private RectF getContentBounds() {
        if (!(targetView instanceof ViewGroup)) {
            return new RectF(0f, 0f, targetView.getWidth(), targetView.getHeight());
        }
        ViewGroup vg = (ViewGroup) targetView;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float maxY = Float.MIN_VALUE;
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
            return new RectF(0f, 0f, targetView.getWidth(), targetView.getHeight());
        }
        return new RectF(minX, minY, maxX, maxY);
    }

    private void setupButtons() {
        int adaptiveColor = ThemeUtils.getAdaptiveColor(getContext(), settingsManager, true);

        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);
        int p12 = ThemeUtils.dpToPx(getContext(), 12);
        int p6 = ThemeUtils.dpToPx(getContext(), 6);
        container.setPadding(p12, p6, p12, p6);
        container.setBackground(ThemeUtils.getGlassDrawable(getContext(), settingsManager, 12f));

        TextView btnRemove = createButton(R.string.action_remove, adaptiveColor);
        btnRemove.setOnClickListener(v -> { if (onSaveListener != null) onSaveListener.onRemove(); });
        container.addView(btnRemove, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f));

        TextView btnReset = createButton(R.string.action_reset, adaptiveColor);
        btnReset.setOnClickListener(v -> reset());
        container.addView(btnReset, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f));

        TextView btnSave = createButton(R.string.action_save, adaptiveColor);
        btnSave.setOnClickListener(v -> save());
        container.addView(btnSave, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f));

        if (item.type != HomeItem.Type.WIDGET && item.type != HomeItem.Type.FOLDER) {
            TextView btnInfo = createButton(R.string.action_app_info, adaptiveColor);
            btnInfo.setOnClickListener(v -> { if (onSaveListener != null) onSaveListener.onAppInfo(); });
            container.addView(btnInfo, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f));
        }

        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = ThemeUtils.dpToPx(getContext(), 48);
        lp.leftMargin = ThemeUtils.dpToPx(getContext(), 24);
        lp.rightMargin = ThemeUtils.dpToPx(getContext(), 24);
        addView(container, lp);
    }

    private TextView createButton(int textRes, int color) {
        TextView tv = new TextView(getContext());
        tv.setText(textRes);
        int p8 = ThemeUtils.dpToPx(getContext(), 8);
        tv.setPadding(p8, p8, p8, p8);
        tv.setTextColor(color);
        tv.setTextSize(11f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        return tv;
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
        if (onSaveListener != null) onSaveListener.onSave(null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        boolean isFreeform = settingsManager.isFreeformHome();

        float sx = targetView.getScaleX();
        float sy = targetView.getScaleY();
        float r = isFreeform ? targetView.getRotation() : 0f;

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

        paint.setColor(foregroundColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(ThemeUtils.dpToPx(getContext(), 1));
        paint.setAlpha(60);
        canvas.drawRect(left, top, right, bottom, paint);

        float hs = handleSize / 2f;

        if (isFreeform) {
            drawHandle(canvas, left, top, hs, true, foregroundColor);
            drawHandle(canvas, right, top, hs, true, foregroundColor);
            drawHandle(canvas, right, bottom, hs, true, foregroundColor);
            drawHandle(canvas, left, bottom, hs, true, foregroundColor);

            paint.setColor(foregroundColor);
            paint.setAlpha(100);
            canvas.drawLine((left + right) / 2f, top, (left + right) / 2f, top - rotationHandleDist, paint);
            drawHandle(canvas, (left + right) / 2f, top - rotationHandleDist, hs * 1.1f, true, foregroundColor);
        }
        canvas.restore();
    }

    private void drawHandle(Canvas canvas, float cx, float cy, float radius, boolean isPrimary, int foregroundColor) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setAlpha(isPrimary ? 200 : 120);
        canvas.drawCircle(cx, cy, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(foregroundColor);
        paint.setStrokeWidth(ThemeUtils.dpToPx(getContext(), 1));
        paint.setAlpha(isPrimary ? 200 : 150);
        canvas.drawCircle(cx, cy, radius, paint);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                performClick();
                activeHandle = findHandle(x, y);
                if (activeHandle == ACTION_MOVE) {
                    if (onSaveListener != null) onSaveListener.onMoveStart(x, y);
                }
                if (activeHandle == ACTION_OUTSIDE) {
                    if (onSaveListener != null) {
                        View other = onSaveListener.findItemAt(x, y, targetView);
                        if (other != null) {
                            onSaveListener.onCollision(other);
                            return true;
                        }
                        onSaveListener.onSave(null);
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
                        float threshold = ThemeUtils.dpToPx(getContext(), MOVE_THRESHOLD_DP);
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
                if (activeHandle == ACTION_MOVE) {
                    float midX = targetView.getX() + targetView.getWidth() / 2f;
                    float midY = targetView.getY() + targetView.getHeight() / 2f;
                    View other = onSaveListener != null ? onSaveListener.findItemAt(midX, midY, targetView) : null;
                    if (other != null && item.type == HomeItem.Type.APP) {
                        HomeItem otherItem = (HomeItem) other.getTag();
                        if (otherItem != null && (otherItem.type == HomeItem.Type.APP || otherItem.type == HomeItem.Type.FOLDER)) {
                            if (onSaveListener != null) onSaveListener.onSave(other);
                            activeHandle = -1;
                            hasPassedThreshold = false;
                            return true;
                        }
                    }
                }

                if (activeHandle != -1 && activeHandle != ACTION_MOVE && activeHandle != HANDLE_ROTATE && activeHandle != ACTION_OUTSIDE) {
                    if (targetView instanceof android.appwidget.AppWidgetHostView) {
                        ViewGroup.LayoutParams lp = targetView.getLayoutParams();
                        int dw = (int) (lp.width / getResources().getDisplayMetrics().density);
                        int dh = (int) (lp.height / getResources().getDisplayMetrics().density);
                        Bundle options = new Bundle();
                        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, dw);
                        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, dh);
                        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, dw);
                        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, dh);
                        ((android.appwidget.AppWidgetHostView) targetView).updateAppWidgetOptions(options);
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

        double angle = isFreeform ? Math.toRadians(-targetView.getRotation()) : 0.0;
        float rx = (float) (Math.cos(angle) * (tx - cx) - Math.sin(angle) * (ty - cy));
        float ry = (float) (Math.sin(angle) * (tx - cx) + Math.cos(angle) * (ty - cy));

        RectF bounds = getContentBounds();

        float left = (bounds.left - targetView.getPivotX()) * sx;
        float top = (bounds.top - targetView.getPivotY()) * sy;
        float right = (bounds.right - targetView.getPivotX()) * sx;
        float bottom = (bounds.bottom - targetView.getPivotY()) * sy;

        float hs = ThemeUtils.dpToPx(getContext(), 24);

        if ((isFreeform || item.type == HomeItem.Type.WIDGET || item.type == HomeItem.Type.FOLDER) &&
            dist(rx, ry, (left + right) / 2f, top - rotationHandleDist) < hs
        ) return HANDLE_ROTATE;

        if (isFreeform && canResizeHorizontal && canResizeVertical) {
            if (dist(rx, ry, left, top) < hs) return HANDLE_TOP_LEFT;
            if (dist(rx, ry, right, top) < hs) return HANDLE_TOP_RIGHT;
            if (dist(rx, ry, right, bottom) < hs) return HANDLE_BOTTOM_RIGHT;
            if (dist(rx, ry, left, bottom) < hs) return HANDLE_BOTTOM_LEFT;
        }

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

            newX = Math.max(0f, Math.min(newX, (getWidth() - targetView.getWidth())));
            newY = Math.max(0f, Math.min(newY, (getHeight() - targetView.getHeight())));

            if (!isFreeform) {
                int cellWidth = getWidth() / HomeView.GRID_COLUMNS;
                int cellHeight = getHeight() / HomeView.GRID_ROWS;
                if (cellWidth > 0 && cellHeight > 0) {
                    newX = Math.round(newX / cellWidth) * cellWidth;
                    newY = Math.round(newY / cellHeight) * cellHeight;
                }
            }

            targetView.setX(newX);
            targetView.setY(newY);
            if (onSaveListener != null) onSaveListener.onMove(tx, ty);
        } else if (activeHandle == HANDLE_ROTATE && (isFreeform || item.type == HomeItem.Type.FOLDER || item.type == HomeItem.Type.WIDGET)) {
            float angle = (float) Math.toDegrees(Math.atan2((ty - cy), (tx - cx))) + 90;
            float targetR = angle;
            float currentR = targetView.getRotation();

            while (targetR - currentR > 180) targetR -= 360f;
            while (targetR - currentR < -180) targetR += 360f;

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

            if (isFreeform) {
                float initialDist = dist(initialTouchX, initialTouchY, cx, cy);
                float currDist = dist(tx, ty, cx, cy);
                if (initialDist > 0) {
                    float factor = currDist / initialDist;

                    float minFactor = 0.2f / Math.min(sx, sy);
                    float maxFactor = 5.0f / Math.max(sx, sy);
                    factor = Math.max(minFactor, Math.min(maxFactor, factor));

                    if (gestureInitialWidth > 0 && gestureInitialHeight > 0) {
                        float maxSX = Math.min(2 * cx, 2 * (getWidth() - cx)) * sx / (float) gestureInitialWidth;
                        float maxSY = Math.min(2 * cy, 2 * (getHeight() - cy)) * sy / (float) gestureInitialHeight;
                        float boundFactor = Math.min(maxSX / sx, maxSY / sy);
                        factor = Math.min(factor, boundFactor);
                    }

                    newScaleX = sx * factor;
                    newScaleY = sy * factor;
                }
            } else {
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
                                newScaleX = sx * factor;
                                newScaleY = sy * factor;
                            }
                        }
                        break;
                }

                if (gestureInitialWidth > 0 && gestureInitialHeight > 0) {
                    float maxSX = Math.min(5.0f, Math.min(2 * cx, 2 * (getWidth() - cx)) * sx / (float) gestureInitialWidth);
                    float maxSY = Math.min(5.0f, Math.min(2 * cy, 2 * (getHeight() - cy)) * sy / (float) gestureInitialHeight);
                    newScaleX = Math.min(newScaleX, maxSX);
                    newScaleY = Math.min(newScaleY, maxSY);
                }
            }

            if (!isFreeform) {
                int cellWidth = getWidth() / HomeView.GRID_COLUMNS;
                int cellHeight = getHeight() / HomeView.GRID_ROWS;
                if (cellWidth > 0 && cellHeight > 0) {
                    float targetW = newScaleX * (gestureInitialWidth / gestureInitialScaleX);
                    float targetH = newScaleY * (gestureInitialHeight / gestureInitialScaleY);
                    targetW = Math.max((float) cellWidth, Math.round(targetW / cellWidth) * (float) cellWidth);
                    targetH = Math.max((float) cellHeight, Math.round(targetH / cellHeight) * (float) cellHeight);
                    newScaleX = targetW * gestureInitialScaleX / (float) gestureInitialWidth;
                    newScaleY = targetH * gestureInitialScaleY / (float) gestureInitialHeight;
                }
            } else {
                newScaleX = Math.round(newScaleX * 100f) / 100.0f;
                newScaleY = Math.round(newScaleY * 100f) / 100.0f;
            }

            targetView.setScaleX(newScaleX);
            targetView.setScaleY(newScaleY);
        }
    }

    private float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt(Math.pow((x1 - x2), 2.0) + Math.pow((y1 - y2), 2.0));
    }

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
}
