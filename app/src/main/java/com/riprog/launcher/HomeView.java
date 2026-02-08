package com.riprog.launcher;

import android.app.Activity;
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
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class HomeView extends FrameLayout {
    private final LinearLayout pagesContainer;
    private final PageIndicator pageIndicator;
    private final List<FrameLayout> pages = new ArrayList<>();
    private int currentPage = 0;
    private final GestureDetector gestureDetector;
    private int accentColor = Color.WHITE;
    private boolean isChildDragging = false;

    public HomeView(Context context) {
        super(context);
        setBackgroundResource(R.color.background);

        pagesContainer = new LinearLayout(context);
        pagesContainer.setOrientation(LinearLayout.HORIZONTAL);
        addView(pagesContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        pageIndicator = new PageIndicator(context);
        LayoutParams indicatorParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        indicatorParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        indicatorParams.bottomMargin = dpToPx(80);
        addView(pageIndicator, indicatorParams);

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (Math.abs(velocityX) > Math.abs(velocityY)) {
                    if (velocityX < -500 && currentPage < pages.size() - 1) {
                        scrollToPage(currentPage + 1);
                        return true;
                    } else if (velocityX > 500 && currentPage > 0) {
                        scrollToPage(currentPage - 1);
                        return true;
                    }
                }
                return false;
            }
        });

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
        if (item.page >= pages.size()) return;
        FrameLayout page = pages.get(item.page);

        LayoutParams lp = new LayoutParams(
                item.width > 0 ? dpToPx(item.width) : LayoutParams.WRAP_CONTENT,
                item.height > 0 ? dpToPx(item.height) : LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(lp);
        view.setX(item.x);
        view.setY(item.y);
        view.setTag(item);

        makeDraggable(view);
        page.addView(view);
    }

    public void makeDraggable(View view) {
        view.setOnTouchListener(new OnTouchListener() {
            private float lastX, lastY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = event.getRawX();
                        lastY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - lastX;
                        float deltaY = event.getRawY() - lastY;
                        if (!isDragging && (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10)) {
                            isDragging = true;
                            isChildDragging = true;
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                            v.animate().scaleX(1.05f).scaleY(1.05f).alpha(0.8f).setDuration(100).start();
                        }
                        if (isDragging) {
                            v.setX(v.getX() + deltaX);
                            v.setY(v.getY() + deltaY);
                            lastX = event.getRawX();
                            lastY = event.getRawY();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isDragging) {
                            isDragging = false;
                            isChildDragging = false;
                            v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(100).start();
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                            HomeItem item = (HomeItem) v.getTag();
                            if (item != null) {
                                item.x = (int) v.getX();
                                item.y = (int) v.getY();
                                // State persistence will be called from MainActivity or via a callback
                                if (getContext() instanceof MainActivity) {
                                    ((MainActivity) getContext()).saveHomeState();
                                }
                            }
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {
                            v.performClick();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    public int getCurrentPage() {
        return currentPage;
    }

    private void scrollToPage(int page) {
        currentPage = page;
        int targetX = page * getWidth();
        pagesContainer.animate()
                .translationX(-targetX)
                .setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        pageIndicator.setCurrentPage(page);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isChildDragging) return false;
        return gestureDetector.onTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    public void setFavorites(List<AppItem> favorites, LauncherModel model) {
        // No-op in v2.1.0 freeform mode
    }

    public void setWidget(View widget) {
        // No-op in v2.1.0, handled by freeform layout
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
