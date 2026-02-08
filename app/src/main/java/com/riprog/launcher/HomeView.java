package com.riprog.launcher;

import android.app.Activity;
import android.os.Handler;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateFormat;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class HomeView extends FrameLayout {
    public static final int GRID_COLUMNS = 4;
    public static final int GRID_ROWS = 6;

    private final LinearLayout pagesContainer;
    private final PageIndicator pageIndicator;
    private final List<FrameLayout> pages = new ArrayList<>();
    private int currentPage = 0;
    private int accentColor = Color.WHITE;

    private View draggingView = null;
    private float lastX, lastY;
    private final Handler edgeScrollHandler = new Handler();
    private boolean isEdgeScrolling = false;
    private final Runnable edgeScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (draggingView != null) {
                if (lastX < getWidth() * 0.05f && currentPage > 0) {
                    scrollToPage(currentPage - 1);
                } else if (lastX > getWidth() * 0.95f && currentPage < pages.size() - 1) {
                    scrollToPage(currentPage + 1);
                }
                edgeScrollHandler.postDelayed(this, 400);
            } else {
                isEdgeScrolling = false;
            }
        }
    };

    public HomeView(Context context) {
        super(context);

        pagesContainer = new LinearLayout(context);
        pagesContainer.setOrientation(LinearLayout.HORIZONTAL);
        pagesContainer.setPadding(0, dpToPx(48), 0, 0);
        addView(pagesContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        pageIndicator = new PageIndicator(context);
        LayoutParams indicatorParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        indicatorParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        indicatorParams.bottomMargin = dpToPx(80);
        addView(pageIndicator, indicatorParams);

        // Add initial pages
        addPage();
        addPage();
    }

    public void addPage() {
        FrameLayout page = new FrameLayout(getContext());
        pages.add(page);
        pagesContainer.addView(page, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        pageIndicator.setPageCount(pages.size());
        pageIndicator.setCurrentPage(currentPage);
    }

    public void addItemView(HomeItem item, View view) {
        while (item.page >= pages.size()) {
            addPage();
        }
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        FrameLayout page = pages.get(item.page);

        updateViewPosition(item, view);
        view.setTag(item);
        page.addView(view);
    }

    public void updateViewPosition(HomeItem item, View view) {
        int cellWidth = getWidth() / GRID_COLUMNS;
        int cellHeight = getHeight() / GRID_ROWS;

        if (cellWidth == 0 || cellHeight == 0) {
            post(() -> updateViewPosition(item, view));
            return;
        }

        LayoutParams lp = new LayoutParams(cellWidth * item.spanX, cellHeight * item.spanY);
        view.setLayoutParams(lp);
        view.setX(item.col * cellWidth);
        view.setY(item.row * cellHeight);
    }

    public void startDragging(View v, float x, float y) {
        draggingView = v;
        lastX = x;
        lastY = y;
        v.animate().scaleX(1.1f).scaleY(1.1f).alpha(0.8f).setDuration(150).start();
        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
    }

    public void handleDrag(float x, float y) {
        if (draggingView != null) {
            float dx = x - lastX;
            float dy = y - lastY;
            draggingView.setX(draggingView.getX() + dx);
            draggingView.setY(draggingView.getY() + dy);
            lastX = x;
            lastY = y;
            checkEdgeScroll(x);
        }
    }

    private void checkEdgeScroll(float x) {
        if (x < getWidth() * 0.05f || x > getWidth() * 0.95f) {
            if (!isEdgeScrolling) {
                isEdgeScrolling = true;
                edgeScrollHandler.postDelayed(edgeScrollRunnable, 300);
            }
        } else {
            isEdgeScrolling = false;
            edgeScrollHandler.removeCallbacks(edgeScrollRunnable);
        }
    }

    public void endDragging() {
        if (draggingView != null) {
            draggingView.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start();
            HomeItem item = (HomeItem) draggingView.getTag();
            if (item != null) {
                snapToGrid(item, draggingView);
            }
            draggingView = null;
            isEdgeScrolling = false;
            edgeScrollHandler.removeCallbacks(edgeScrollRunnable);
        }
    }

    public void cancelDragging() {
        draggingView = null;
        isEdgeScrolling = false;
        edgeScrollHandler.removeCallbacks(edgeScrollRunnable);
    }

    private void snapToGrid(HomeItem item, View v) {
        int cellWidth = getWidth() / GRID_COLUMNS;
        int cellHeight = getHeight() / GRID_ROWS;

        item.col = Math.max(0, Math.min(GRID_COLUMNS - item.spanX, Math.round(v.getX() / cellWidth)));
        item.row = Math.max(0, Math.min(GRID_ROWS - item.spanY, Math.round(v.getY() / cellHeight)));

        v.animate()
                .x(item.col * cellWidth)
                .y(item.row * cellHeight)
                .setDuration(200)
                .start();

        if (getContext() instanceof MainActivity) {
            ((MainActivity) getContext()).saveHomeState();
        }
    }

    public void swipePages(float dx) {
        // Simple page swipe animation based on delta
        // For now just handle the final page change
    }

    public void scrollToPage(int page) {
        if (page < 0 || page >= pages.size()) return;
        currentPage = page;
        int targetX = page * getWidth();
        pagesContainer.animate()
                .translationX(-targetX)
                .setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        pageIndicator.setCurrentPage(page);
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getPageCount() {
        return pages.size();
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        pageIndicator.setAccentColor(color);
    }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private class PageIndicator extends LinearLayout {
        private int count = 0;
        private int current = 0;
        private int accentColor = Color.WHITE;

        public PageIndicator(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
        }

        public void setPageCount(int count) {
            this.count = count;
            updateDots();
        }

        public void setCurrentPage(int current) {
            this.current = current;
            updateDots();
        }

        public void setAccentColor(int color) {
            this.accentColor = color;
            updateDots();
        }

        private void updateDots() {
            removeAllViews();
            for (int i = 0; i < count; i++) {
                View dot = new View(getContext());
                int size = dpToPx(6);
                LayoutParams lp = new LayoutParams(size, size);
                lp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
                dot.setLayoutParams(lp);

                GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.OVAL);
                if (i == current) {
                    shape.setColor(accentColor);
                } else {
                    shape.setColor(Color.GRAY & 0x80FFFFFF);
                }
                dot.setBackground(shape);
                addView(dot);
            }
        }
    }
}
