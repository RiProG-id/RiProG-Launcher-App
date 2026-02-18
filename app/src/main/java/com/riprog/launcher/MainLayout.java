package com.riprog.launcher;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class MainLayout extends FrameLayout {
    private final MainActivity activity;
    private boolean isDrawerOpen = false;
    private float startX, startY;
    private long downTime;
    private boolean isGestureCanceled = false;
    private final int touchSlop;
    private final Handler longPressHandler = new Handler();
    private View touchedView = null;
    private boolean longPressTriggered = false;
    private boolean isDragging = false;
    private LinearLayout dragOverlay;
    private ImageView ivRemove, ivAppInfo;
    private float origCol, origRow;
    private int origPage;
    private boolean isExternalDrag = false;

    private float lastDist, lastAngle;
    private float baseScale, baseRotation, baseTiltX, baseTiltY;
    private float startX3, startY3;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            longPressTriggered = true;
            if (touchedView != null) {
                if (activity.settingsManager.isFreeformHome()) {
                    activity.getFreeformInteraction().showTransformOverlay(touchedView);
                } else {
                    isDragging = true;
                    isExternalDrag = false;
                    HomeItem item = (HomeItem) touchedView.getTag();
                    if (item != null) {
                        origCol = item.col;
                        origRow = item.row;
                        origPage = item.page;
                    }
                    if (dragOverlay != null) {
                        boolean isApp = item != null && item.type == HomeItem.Type.APP;
                        ivAppInfo.setVisibility(isApp ? View.VISIBLE : View.GONE);
                        dragOverlay.setVisibility(View.VISIBLE);
                    }
                    activity.homeView.startDragging(touchedView, startX, startY);
                }
            } else {
                int cellWidth = getWidth() / HomeView.GRID_COLUMNS;
                int cellHeight = getHeight() / HomeView.GRID_ROWS;
                float col = startX / (cellWidth > 0 ? (float) cellWidth : 1.0f);
                float row = startY / (cellHeight > 0 ? (float) cellHeight : 1.0f);
                activity.showHomeContextMenu(col, row, activity.homeView.getCurrentPage());
            }
        }
    };

    public MainLayout(MainActivity activity) {
        super(activity);
        this.activity = activity;
        touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        setupDragOverlay();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void setupDragOverlay() {
        dragOverlay = new LinearLayout(getContext());
        dragOverlay.setOrientation(LinearLayout.HORIZONTAL);
        dragOverlay.setBackgroundResource(R.drawable.glass_bg);
        dragOverlay.setGravity(Gravity.CENTER);
        dragOverlay.setVisibility(View.GONE);
        dragOverlay.setElevation(dpToPx(8));

        ivRemove = new ImageView(getContext());
        ivRemove.setImageResource(R.drawable.ic_remove);
        ivRemove.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16));
        ivRemove.setContentDescription(getContext().getString(R.string.drag_remove));
        dragOverlay.addView(ivRemove);

        ivAppInfo = new ImageView(getContext());
        ivAppInfo.setImageResource(R.drawable.ic_info);
        ivAppInfo.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16));
        ivAppInfo.setContentDescription(getContext().getString(R.string.drag_app_info));
        dragOverlay.addView(ivAppInfo);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dpToPx(48);
        addView(dragOverlay, lp);
    }

    private float spacing(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0;
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private float angle(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0;
        double delta_x = (event.getX(0) - event.getX(1));
        double delta_y = (event.getY(0) - event.getY(1));
        double radians = Math.atan2(delta_y, delta_x);
        return (float) Math.toDegrees(radians);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isDrawerOpen) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = ev.getX();
                    startY = ev.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dy = ev.getY() - startY;
                    float dx = ev.getX() - startX;

                    if (dy > touchSlop && dy > Math.abs(dx)) {
                        if (activity.getDrawerView().isAtTop() || dy > touchSlop * 4) {
                            return true;
                        }
                    }
                    break;
            }
            return false;
        }

        switch (ev.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                downTime = System.currentTimeMillis();
                isGestureCanceled = false;
                longPressTriggered = false;
                isDragging = false;
                touchedView = findTouchedHomeItem(startX, startY);
                longPressHandler.removeCallbacks(longPressRunnable);
                longPressHandler.postDelayed(longPressRunnable, 400);
                return false;

            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - startX;
                float dy = ev.getY() - startY;

                if (dy < -touchSlop && Math.abs(dy) > Math.abs(dx)) {
                    longPressHandler.removeCallbacks(longPressRunnable);
                    return true;
                }
                if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                    longPressHandler.removeCallbacks(longPressRunnable);
                    if (!longPressTriggered) {
                        return true;
                    }
                }
                return isDragging;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) return true;
                long duration = System.currentTimeMillis() - downTime;
                if (duration < 80) {
                    longPressHandler.removeCallbacks(longPressRunnable);
                    return false;
                }
                break;
        }
        return isDragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isDrawerOpen) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startX = event.getX();
                startY = event.getY();
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float dy = event.getY() - startY;
                if (dy > touchSlop) {
                    closeDrawer();
                    return true;
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                float dy = event.getY() - startY;
                if (dy > touchSlop) closeDrawer();
            }
            return true;
        }

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (isDragging && activity.settingsManager.isFreeformHome()) {
                    if (event.getPointerCount() == 2) {
                        lastDist = spacing(event);
                        lastAngle = angle(event);
                        baseScale = touchedView.getScaleX();
                        baseRotation = touchedView.getRotation();
                    } else if (event.getPointerCount() == 3) {
                        startX3 = event.getX(2);
                        startY3 = event.getY(2);
                        baseTiltX = touchedView.getRotationX();
                        baseTiltY = touchedView.getRotationY();
                    }
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - startX;
                float dy = event.getY() - startY;

                if (isDragging) {
                    if (activity.settingsManager.isFreeformHome() && event.getPointerCount() > 1) {
                        if (event.getPointerCount() == 2) {
                            float newDist = spacing(event);
                            if (newDist > 10f) {
                                float scaleFactor = newDist / lastDist;
                                touchedView.setScaleX(baseScale * scaleFactor);
                                touchedView.setScaleY(baseScale * scaleFactor);
                            }
                            float newAngle = angle(event);
                            touchedView.setRotation(baseRotation + (newAngle - lastAngle));
                        } else if (event.getPointerCount() == 3) {
                            float mdx = event.getX(2) - startX3;
                            float mdy = event.getY(2) - startY3;
                            touchedView.setRotationX(baseTiltX + mdy / 5f);
                            touchedView.setRotationY(baseTiltY - mdx / 5f);
                        }
                    } else {
                        activity.homeView.handleDrag(event.getX(), event.getY());
                        updateDragHighlight(event.getX(), event.getY());
                    }
                    return true;
                }

                if (!isGestureCanceled && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    longPressHandler.removeCallbacks(longPressRunnable);
                    if (Math.abs(dy) > Math.abs(dx)) {
                        if (dy < -touchSlop * 2) {
                            openDrawer();
                            isGestureCanceled = true;
                        }
                    } else {
                        if (dx > touchSlop * 2 && activity.homeView.getCurrentPage() > 0) {
                            activity.homeView.scrollToPage(activity.homeView.getCurrentPage() - 1);
                            isGestureCanceled = true;
                        } else if (dx < -touchSlop * 2 && activity.homeView.getCurrentPage() < activity.homeView.getPageCount() - 1) {
                            activity.homeView.scrollToPage(activity.homeView.getCurrentPage() + 1);
                            isGestureCanceled = true;
                        }
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                longPressHandler.removeCallbacks(longPressRunnable);
                if (isDragging) {
                    if (dragOverlay != null) {
                        int overlayHeight = dragOverlay.getHeight();
                        int overlayWidth = dragOverlay.getWidth();
                        float left = (getWidth() - overlayWidth) / 2f;
                        dragOverlay.setVisibility(View.GONE);
                        ivRemove.setBackgroundColor(Color.TRANSPARENT);
                        ivAppInfo.setBackgroundColor(Color.TRANSPARENT);

                        if (event.getY() < dragOverlay.getBottom() + touchSlop * 2 &&
                            event.getX() >= left && event.getX() <= left + overlayWidth) {
                            HomeItem item = (HomeItem) touchedView.getTag();
                            if (item != null) {
                                boolean isApp = ivAppInfo.getVisibility() == View.VISIBLE;
                                if (!isApp) {
                                    activity.removeHomeItem(item, touchedView);
                                } else {
                                    float x = event.getX();
                                    if (x < left + overlayWidth / 2f) {
                                        activity.removeHomeItem(item, touchedView);
                                    } else {
                                        activity.showAppInfo(item);
                                        revertPosition(item, touchedView);
                                    }
                                }
                            }
                            activity.homeView.cancelDragging();
                        } else {
                            activity.homeView.endDragging();
                        }
                    } else {
                        activity.homeView.endDragging();
                    }
                    isDragging = false;
                    return true;
                }
                if (!isGestureCanceled && !longPressTriggered) {
                    long duration = System.currentTimeMillis() - downTime;
                    float finalDx = event.getX() - startX;
                    float finalDy = event.getY() - startY;
                    float dist = (float) Math.sqrt(finalDx * finalDx + finalDy * finalDy);
                    if (duration >= 80 && duration < 150 && dist < touchSlop) {
                        if (touchedView != null) activity.handleItemClick(touchedView);
                        else performClick();
                    }
                }
                return true;
        }
        return true;
    }

    private View findTouchedHomeItem(float x, float y) {
        int page = activity.homeView.getCurrentPage();
        ViewGroup pagesContainer = (ViewGroup) activity.homeView.getChildAt(0);
        if (pagesContainer != null && page < pagesContainer.getChildCount()) {
            ViewGroup pageLayout = (ViewGroup) pagesContainer.getChildAt(page);
            float adjustedX = x - pagesContainer.getPaddingLeft();
            float adjustedY = y - pagesContainer.getPaddingTop();
            for (int i = pageLayout.getChildCount() - 1; i >= 0; i--) {
                View child = pageLayout.getChildAt(i);
                if (adjustedX >= child.getX() && adjustedX <= child.getX() + child.getWidth() &&
                    adjustedY >= child.getY() && adjustedY <= child.getY() + child.getHeight()) {
                    return child;
                }
            }
        }
        return null;
    }

    public void openDrawer() {
        if (isDrawerOpen) return;
        isDrawerOpen = true;
        activity.settingsManager.incrementDrawerOpenCount();
        activity.getDrawerView().setVisibility(View.VISIBLE);
        activity.getDrawerView().setAlpha(0f);
        activity.getDrawerView().setTranslationY(getHeight() / 4f);
        activity.getDrawerView().animate()
            .translationY(0)
            .alpha(1f)
            .setDuration(250)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
        activity.homeView.animate().alpha(0).setDuration(250).start();
        activity.getDrawerView().onOpen();
    }

    public void startExternalDrag(View v) {
        isDragging = true;
        isExternalDrag = true;
        if (dragOverlay != null) {
            HomeItem item = (HomeItem) v.getTag();
            boolean isApp = item != null && item.type == HomeItem.Type.APP;
            ivAppInfo.setVisibility(isApp ? View.VISIBLE : View.GONE);
            dragOverlay.setVisibility(View.VISIBLE);
        }
        touchedView = v;

        int iconSize = getResources().getDimensionPixelSize(R.dimen.grid_icon_size);
        v.setX(startX - iconSize / 2f);
        v.setY(startY - iconSize / 2f - dpToPx(48));

        activity.homeView.startDragging(v, startX, startY);
    }

    private void updateDragHighlight(float x, float y) {
        if (dragOverlay == null || dragOverlay.getVisibility() != View.VISIBLE) return;

        int overlayHeight = dragOverlay.getHeight();
        int overlayWidth = dragOverlay.getWidth();
        float left = (getWidth() - overlayWidth) / 2f;
        boolean isApp = ivAppInfo.getVisibility() == View.VISIBLE;

        ivRemove.setBackgroundColor(Color.TRANSPARENT);
        ivAppInfo.setBackgroundColor(Color.TRANSPARENT);

        if (y < dragOverlay.getBottom() + touchSlop * 2 && x >= left && x <= left + overlayWidth) {
            if (!isApp) {
                ivRemove.setBackgroundColor(0x40FFFFFF);
            } else {
                if (x < left + overlayWidth / 2f) {
                    ivRemove.setBackgroundColor(0x40FFFFFF);
                } else {
                    ivAppInfo.setBackgroundColor(0x40FFFFFF);
                }
            }
        }
    }

    private void revertPosition(HomeItem item, View v) {
        if (isExternalDrag) {
            activity.removeHomeItem(item, v);
        } else {
            item.col = origCol;
            item.row = origRow;
            item.page = origPage;
            activity.homeView.addItemView(item, v);
            v.setRotation(item.rotation);
            v.setScaleX(item.scale);
            v.setScaleY(item.scale);
            v.setRotationX(item.tiltX);
            v.setRotationY(item.tiltY);
            activity.homeView.updateViewPosition(item, v);
            activity.saveHomeState();
        }
    }

    public void closeDrawer() {
        if (!isDrawerOpen) return;
        isDrawerOpen = false;
        activity.getDrawerView().animate()
            .translationY(getHeight() / 4f)
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(new android.view.animation.AccelerateInterpolator())
            .withEndAction(() -> {
                activity.getDrawerView().setVisibility(View.GONE);
                activity.homeView.setVisibility(View.VISIBLE);
                activity.getDrawerView().onClose();
                System.gc();
            })
            .start();
        activity.homeView.setVisibility(View.VISIBLE);
        activity.homeView.animate().alpha(1).setDuration(200).start();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    public void handleItemClick(View v) {
        HomeItem item = (HomeItem) v.getTag();
        if (item == null) return;
        if (item.type == HomeItem.Type.APP) {
            android.content.Intent intent = activity.getPackageManager().getLaunchIntentForPackage(item.packageName);
            if (intent != null) activity.startActivity(intent);
        } else if (item.type == HomeItem.Type.WIDGET) {
            activity.showWidgetOptions(item, v);
        } else if (item.type == HomeItem.Type.FOLDER) {
            activity.folderManager.openFolder(item, v, activity.homeItems, activity.allApps);
        }
    }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
