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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeView extends FrameLayout {
    public static final int GRID_COLUMNS = 4;
    public static final int GRID_ROWS = 6;

    private final LinearLayout pagesContainer;
    private final PageIndicator pageIndicator;
    private final List<FrameLayout> pages = new ArrayList<>();
    private final SettingsManager settingsManager;
    private int currentPage = 0;
    private int accentColor = Color.WHITE;
    private long edgeHoldStart = 0;
    private LauncherModel model;
    private List<AppItem> allApps;

    private View draggingView = null;
    private float lastX, lastY;
    private final Handler edgeScrollHandler = new Handler();
    private boolean isEdgeScrolling = false;
    private final Runnable edgeScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (draggingView != null) {
                if (lastX < getWidth() * 0.05f) {
                    if (currentPage > 0) {
                        scrollToPage(currentPage - 1);
                        edgeHoldStart = 0;
                    } else {
                        handleEdgePageCreation();
                    }
                } else if (lastX > getWidth() * 0.95f) {
                    if (currentPage < pages.size() - 1) {
                        scrollToPage(currentPage + 1);
                        edgeHoldStart = 0;
                    } else {
                        handleEdgePageCreation();
                    }
                } else {
                    edgeHoldStart = 0;
                }
                edgeScrollHandler.postDelayed(this, 400);
            } else {
                isEdgeScrolling = false;
                edgeHoldStart = 0;
            }
        }
    };

    private void handleEdgePageCreation() {
        if (edgeHoldStart == 0) {
            edgeHoldStart = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - edgeHoldStart > 1000) {
            if (lastX < getWidth() * 0.05f && currentPage == 0) {
                addPageAtIndex(0);
                scrollToPage(0);
            } else if (lastX > getWidth() * 0.95f && currentPage == pages.size() - 1) {
                addPage();
                scrollToPage(pages.size() - 1);
            }
            edgeHoldStart = 0;
        }
    }

    public void addPageAtIndex(int index) {
        FrameLayout page = new FrameLayout(getContext());
        pages.add(index, page);
        pagesContainer.addView(page, index, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));


        for (int i = 0; i < pages.size(); i++) {
            FrameLayout p = pages.get(i);
            for (int j = 0; j < p.getChildCount(); j++) {
                View v = p.getChildAt(j);
                HomeItem item = (HomeItem) v.getTag();
                if (item != null) item.page = i;
            }
        }
        pageIndicator.setPageCount(pages.size());
        pageIndicator.setCurrentPage(currentPage);
    }

    public HomeView(Context context) {
        super(context);
        settingsManager = new SettingsManager(context);

        pagesContainer = new LinearLayout(context);
        pagesContainer.setOrientation(LinearLayout.HORIZONTAL);
        pagesContainer.setPadding(0, dpToPx(48), 0, 0);
        addView(pagesContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        pageIndicator = new PageIndicator(context);
        LayoutParams indicatorParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        indicatorParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        indicatorParams.bottomMargin = dpToPx(80);
        addView(pageIndicator, indicatorParams);


        addPage();
        addPage();

        addDrawerHint();
    }

    private void addDrawerHint() {
        if (settingsManager.getDrawerOpenCount() >= 5) return;

        TextView hint = new TextView(getContext());
        hint.setText(getContext().getString(R.string.drawer_hint));
        hint.setTextSize(12);
        hint.setTextColor(Color.GRAY & 0x80FFFFFF);
        hint.setAlpha(0);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dpToPx(120);
        addView(hint, lp);


        if (Math.random() < 0.3) {
            hint.animate().alpha(1f).setDuration(1000).setStartDelay(2000).withEndAction(() -> {
                hint.animate().alpha(0f).setDuration(1000).setStartDelay(4000).withEndAction(() -> {
                    removeView(hint);
                }).start();
            }).start();
        }
    }

    public void addPage() {
        addPage(false);
    }

    public void addPage(boolean scroll) {
        FrameLayout page = new FrameLayout(getContext());
        pages.add(page);
        pagesContainer.addView(page, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        pageIndicator.setPageCount(pages.size());

        if (scroll && getWidth() > 0) {
            scrollToPage(pages.size() - 1);
        } else {
            pageIndicator.setCurrentPage(currentPage);
        }
    }

    public void addItemView(HomeItem item, View view) {
        if (item == null || view == null) return;
        while (item.page >= pages.size()) {
            addPage();
        }
        removeViewForItem(item);
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        FrameLayout page = pages.get(item.page);

        updateViewPosition(item, view);
        view.setTag(item);
        page.addView(view);
    }

    public void removeViewForItem(HomeItem item) {
        for (FrameLayout page : pages) {
            for (int i = 0; i < page.getChildCount(); i++) {
                View v = page.getChildAt(i);
                if (v.getTag() == item) {
                    page.removeView(v);
                    return;
                }
            }
        }
    }

    public void updateFolderPreview(HomeItem folder) {
        removeViewForItem(folder);
        if (getContext() instanceof MainActivity) {
            addItemView(folder, ((MainActivity) getContext()).createFolderView(folder));
        }
    }

    public void removeItemsByPackage(String packageName) {
        if (packageName == null) return;
        for (FrameLayout page : pages) {
            for (int i = page.getChildCount() - 1; i >= 0; i--) {
                View v = page.getChildAt(i);
                HomeItem item = (HomeItem) v.getTag();
                if (item != null && packageName.equals(item.packageName)) {
                    page.removeView(v);
                }
            }
        }
    }

    public void updateViewPosition(HomeItem item, View view) {
        int cellWidth = getWidth() / GRID_COLUMNS;
        int cellHeight = getHeight() / GRID_ROWS;

        if (cellWidth == 0 || cellHeight == 0) {
            post(() -> updateViewPosition(item, view));
            return;
        }

        LayoutParams lp;
        if (item.type == HomeItem.Type.WIDGET) {
            lp = new LayoutParams(cellWidth * item.spanX, cellHeight * item.spanY);
        } else {

            int size = getResources().getDimensionPixelSize(R.dimen.grid_icon_size);
            lp = new LayoutParams(size * 2, size * 2);
        }
        view.setLayoutParams(lp);

        view.setX(item.col * cellWidth);
        view.setY(item.row * cellHeight);

        view.setRotation(item.rotation);
        view.setScaleX(item.scale);
        view.setScaleY(item.scale);
        view.setRotationX(item.tiltX);
        view.setRotationY(item.tiltY);
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
            if (model != null && allApps != null) {
                refreshIcons(model, allApps);
            }
        }
    }

    public void cleanupEmptyPages() {
        // Disabled per requirements
    }

    public void removePage(int index) {
        if (index < 0 || index >= pages.size()) return;
        if (pages.size() <= 1) return; // Keep at least one page

        FrameLayout page = pages.remove(index);
        pagesContainer.removeView(page);

        if (currentPage >= pages.size()) {
            currentPage = pages.size() - 1;
        }

        pageIndicator.setPageCount(pages.size());
        scrollToPage(currentPage);
    }

    public void cancelDragging() {
        draggingView = null;
        isEdgeScrolling = false;
        edgeScrollHandler.removeCallbacks(edgeScrollRunnable);
    }

    private void snapToGrid(HomeItem item, View v) {
        int cellWidth = getWidth() / GRID_COLUMNS;
        int cellHeight = getHeight() / GRID_ROWS;

        int oldPage = item.page;
        item.page = currentPage;

        if (settingsManager.isFreeformHome()) {
            item.col = v.getX() / (float) cellWidth;
            item.row = v.getY() / (float) cellHeight;
            item.rotation = v.getRotation();
            item.scale = v.getScaleX();
            item.tiltX = v.getRotationX();
            item.tiltY = v.getRotationY();

            if (oldPage != item.page) {
                addItemView(item, v);
            }
        } else {
            float targetCol = Math.max(0, Math.min(GRID_COLUMNS - item.spanX, Math.round(v.getX() / (float) cellWidth)));
            float targetRow = Math.max(0, Math.min(GRID_ROWS - item.spanY, Math.round(v.getY() / (float) cellHeight)));

            // Check for collision for folder creation
            View other = findViewAt(targetCol, targetRow, item.page, v);
            if (other != null && item.type == HomeItem.Type.APP) {
                HomeItem otherItem = (HomeItem) other.getTag();
                if (otherItem != null && (otherItem.type == HomeItem.Type.APP || otherItem.type == HomeItem.Type.FOLDER)) {
                    if (getContext() instanceof MainActivity) {
                        MainActivity activity = (MainActivity) getContext();
                        if (otherItem.type == HomeItem.Type.APP) {
                            activity.removeHomeItem(item, v);
                            activity.removeHomeItem(otherItem, other);
                            HomeItem folder = activity.getFolderManager().createFolder(item, otherItem, item.page, targetCol, targetRow);
                            activity.getHomeItems().add(folder);
                            activity.renderHomeItem(folder);
                        } else {
                            activity.removeHomeItem(item, v);
                            if (otherItem.folderItems == null) otherItem.folderItems = new ArrayList<>();
                            otherItem.folderItems.add(item);
                            activity.renderHomeItem(otherItem);
                        }
                        activity.saveHomeState();
                        return;
                    }
                }
            }

            item.col = targetCol;
            item.row = targetRow;
            item.rotation = 0;
            item.scale = 1.0f;
            item.tiltX = 0;
            item.tiltY = 0;

            if (oldPage != item.page) {
                addItemView(item, v);
            }

            v.animate()
                    .x(item.col * cellWidth)
                    .y(item.row * cellHeight)
                    .setDuration(200)
                    .start();
        }

        if (getContext() instanceof MainActivity) {
            ((MainActivity) getContext()).saveHomeState();
        }
    }

    private View findViewAt(float col, float row, int page, View exclude) {
        if (page >= pages.size()) return null;
        FrameLayout p = pages.get(page);
        for (int i = 0; i < p.getChildCount(); i++) {
            View v = p.getChildAt(i);
            if (v == exclude) continue;
            HomeItem item = (HomeItem) v.getTag();
            if (item != null && Math.round(item.col) == Math.round(col) && Math.round(item.row) == Math.round(row)) {
                return v;
            }
        }
        return null;
    }

    public void swipePages(float dx) {


    }

    public void scrollToPage(int page) {
        if (pages.isEmpty()) return;
        if (page < 0) page = 0;
        if (page >= pages.size()) page = pages.size() - 1;

        currentPage = page;
        int targetX = page * getWidth();
        pagesContainer.animate()
                .translationX(-targetX)
                .setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    if (model != null && allApps != null) {
                        refreshIcons(model, allApps);
                    }
                })
                .start();
        pageIndicator.setCurrentPage(page);
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getPageCount() {
        return pages.size();
    }

    public void refreshIcons(LauncherModel model, List<AppItem> allApps) {
        this.model = model;
        this.allApps = allApps;
        float scale = settingsManager.getIconScale();
        int baseSize = getResources().getDimensionPixelSize(R.dimen.grid_icon_size);
        int size = (int) (baseSize * scale);

        Map<String, AppItem> appMap = new HashMap<>();
        if (allApps != null) {
            for (AppItem a : allApps) {
                appMap.put(a.packageName, a);
            }
        }

        for (FrameLayout page : pages) {
            for (int i = 0; i < page.getChildCount(); i++) {
                View view = page.getChildAt(i);
                HomeItem item = (HomeItem) view.getTag();
                if (item != null && item.type == HomeItem.Type.APP) {
                    if (view instanceof ViewGroup) {
                        ViewGroup container = (ViewGroup) view;
                        ImageView iv = findImageView(container);
                        TextView tv = findTextView(container);

                        if (iv != null) {
                            ViewGroup.LayoutParams lp = iv.getLayoutParams();
                            if (lp.width != size) {
                                lp.width = size;
                                lp.height = size;
                                iv.setLayoutParams(lp);
                            }
                        }
                        if (tv != null) {
                            tv.setTextSize(10 * scale);
                            tv.setTextColor(ThemeUtils.getAdaptiveColor(getContext(), settingsManager, false));
                            tv.setVisibility(settingsManager.isHideLabels() ? View.GONE : View.VISIBLE);
                        }

                        AppItem app = appMap.get(item.packageName);

                        if (iv != null && app != null) {
                            final AppItem finalApp = app;
                            model.loadIcon(app, bitmap -> {
                                if (bitmap != null) {
                                    iv.setImageBitmap(bitmap);
                                    if (tv != null) tv.setText(finalApp.label);
                                }
                            });
                        }
                    }
                }
            }
        }
    }

    private ImageView findImageView(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof ImageView) {
                return (ImageView) child;
            } else if (child instanceof ViewGroup) {
                ImageView iv = findImageView((ViewGroup) child);
                if (iv != null) return iv;
            }
        }
        return null;
    }

    private TextView findTextView(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof TextView) {
                return (TextView) child;
            } else if (child instanceof ViewGroup) {
                TextView tv = findTextView((ViewGroup) child);
                if (tv != null) return tv;
            }
        }
        return null;
    }

    public void refreshLayout() {
        post(() -> {
            boolean freeform = settingsManager.isFreeformHome();
            for (FrameLayout page : pages) {
                for (int i = 0; i < page.getChildCount(); i++) {
                    View v = page.getChildAt(i);
                    HomeItem item = (HomeItem) v.getTag();
                    if (item != null) {
                        if (!freeform) {
                            item.col = Math.max(0, Math.min(GRID_COLUMNS - item.spanX, Math.round(item.col)));
                            item.row = Math.max(0, Math.min(GRID_ROWS - item.spanY, Math.round(item.row)));
                            item.rotation = 0;
                            item.scale = 1.0f;
                            item.tiltX = 0;
                            item.tiltY = 0;
                        }
                        updateViewPosition(item, v);
                    }
                }
            }
            if (!freeform && getContext() instanceof MainActivity) {
                ((MainActivity) getContext()).saveHomeState();
            }
        });
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
