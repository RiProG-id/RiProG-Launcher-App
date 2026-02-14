package com.riprog.launcher;

import android.app.Activity;
import android.os.Handler;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.RectF;
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
    private List<HomeItem> homeItems;
    private int currentPage = 0;
    private int accentColor = Color.WHITE;
    private LauncherModel model;
    private List<AppItem> allApps;

    private View draggingView = null;
    private float lastX, lastY;
    private final Handler mainHandler = new Handler();
    private final Handler edgeScrollHandler = new Handler();
    private boolean isEdgeScrolling = false;
    private long edgeHoldStart = 0;
    private long lastPageCreateTime = 0;
    private static final long PAGE_CREATE_DELAY = 1000;

    private final Runnable edgeScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (draggingView != null || (getContext() instanceof MainActivity && ((MainActivity) getContext()).isTransforming())) {
                checkAndPerformEdgeAction(lastX);
                edgeScrollHandler.postDelayed(this, 400);
            } else {
                isEdgeScrolling = false;
                edgeHoldStart = 0;
            }
        }
    };

    public void checkAndPerformEdgeAction(float x) {
        lastX = x;
        if (x < getWidth() * 0.05f) {
            if (currentPage > 0) {
                scrollToPage(currentPage - 1);
                edgeHoldStart = 0;
            } else {
                handleEdgePageCreation(x);
            }
        } else if (x > getWidth() * 0.95f) {
            if (currentPage < pages.size() - 1) {
                scrollToPage(currentPage + 1);
                edgeHoldStart = 0;
            } else {
                handleEdgePageCreation(x);
            }
        } else {
            edgeHoldStart = 0;
        }
    }

    private void handleEdgePageCreation(float x) {
        if (System.currentTimeMillis() - lastPageCreateTime < PAGE_CREATE_DELAY) return;

        if (edgeHoldStart == 0) {
            edgeHoldStart = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - edgeHoldStart > 1000) {
            if (x < getWidth() * 0.05f && currentPage == 0) {
                addPageAtIndex(0);
                scrollToPage(0);
                lastPageCreateTime = System.currentTimeMillis();
            } else if (x > getWidth() * 0.95f && currentPage == pages.size() - 1) {
                addPage();
                scrollToPage(pages.size() - 1);
                lastPageCreateTime = System.currentTimeMillis();
            }
            edgeHoldStart = 0;
        }
    }

    public void addPageAtIndex(int index) {
        FrameLayout page = new FrameLayout(getContext());
        pages.add(index, page);
        pagesContainer.addView(page, index, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        if (homeItems != null) {
            for (HomeItem item : homeItems) {
                if (item.page >= index) {
                    item.page++;
                }
            }
        }

        for (int i = 0; i < pages.size(); i++) {
            FrameLayout p = pages.get(i);
            for (int j = 0; j < p.getChildCount(); j++) {
                View v = p.getChildAt(j);
                if (v != null && v.getTag() instanceof HomeItem) {
                    ((HomeItem) v.getTag()).page = i;
                }
            }
        }
        pageIndicator.setPageCount(pages.size());
        pageIndicator.setCurrentPage(currentPage);

        if (getContext() instanceof MainActivity) {
            ((MainActivity) getContext()).saveHomeState();
        }
    }

    public HomeView(Context context) {
        super(context);
        settingsManager = new SettingsManager(context);

        pagesContainer = new LinearLayout(context);
        pagesContainer.setOrientation(LinearLayout.HORIZONTAL);
        addView(pagesContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        pageIndicator = new PageIndicator(context);
        LayoutParams indicatorParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        indicatorParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        indicatorParams.bottomMargin = dpToPx(48);
        addView(pageIndicator, indicatorParams);


        addPage();
        addPage();

        addDrawerHint();
        post(this::cleanupEmptyPages);
    }

    private void addDrawerHint() {
        if (settingsManager.getDrawerOpenCount() >= 5) return;

        TextView hint = new TextView(getContext());
        hint.setText(getContext().getString(R.string.drawer_hint));
        hint.setTextSize(12);
        hint.setTextColor(getContext().getColor(R.color.foreground_dim));
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
        FrameLayout page = new FrameLayout(getContext());
        pages.add(page);
        pagesContainer.addView(page, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        pageIndicator.setPageCount(pages.size());
        pageIndicator.setCurrentPage(currentPage);
    }

    public void addItemView(HomeItem item, View view) {
        if (item == null || view == null) return;
        while (item.page >= pages.size()) {
            addPage();
        }
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        FrameLayout page = pages.get(item.page);

        view.setTag(item);
        page.addView(view);
        updateViewPosition(item, view);
    }

    public void updateViewPosition(HomeItem item, View view) {
        int availW = getWidth() - getPaddingLeft() - getPaddingRight();
        int availH = getHeight() - getPaddingTop() - getPaddingBottom();

        int cellWidth = availW / GRID_COLUMNS;
        int cellHeight = availH / GRID_ROWS;

        if (cellWidth <= 0 || cellHeight <= 0) {
            post(() -> updateViewPosition(item, view));
            return;
        }

        LayoutParams lp;
        if (item.type == HomeItem.Type.WIDGET || (item.type == HomeItem.Type.FOLDER && (item.spanX > 1.0f || item.spanY > 1.0f))) {
            lp = new LayoutParams((int) (cellWidth * item.spanX), (int) (cellHeight * item.spanY));
        } else {
            int size = getResources().getDimensionPixelSize(R.dimen.grid_icon_size);
            lp = new LayoutParams(size * 2, size * 2);
        }
        view.setLayoutParams(lp);

        // Center the icon within the grid area and respect padding
        if (item.type == HomeItem.Type.APP || (item.type == HomeItem.Type.FOLDER && item.spanX <= 1.0f && item.spanY <= 1.0f)) {
            view.setX(getPaddingLeft() + item.col * cellWidth + (cellWidth - lp.width) / 2f);
            view.setY(getPaddingTop() + item.row * cellHeight + (cellHeight - lp.height) / 2f);
        } else {
            view.setX(getPaddingLeft() + item.col * cellWidth);
            view.setY(getPaddingTop() + item.row * cellHeight);
        }

        view.setRotation(item.rotation);
        view.setScaleX(item.scaleX);
        view.setScaleY(item.scaleY);
        view.setRotationX(item.tiltX);
        view.setRotationY(item.tiltY);
    }

    private float dragOffsetX, dragOffsetY;

    public void startDragging(View v, float x, float y) {
        draggingView = v;
        lastX = x;
        lastY = y;

        int[] vPos = new int[2];
        v.getLocationOnScreen(vPos);
        dragOffsetX = x - vPos[0];
        dragOffsetY = y - vPos[1];

        // Reparent to MainLayout to prevent clipping and allow moving between pages
        if (getContext() instanceof MainActivity) {
            ViewGroup mainLayout = ((MainActivity) getContext()).findViewById(android.R.id.content);
            // Actually, MainActivity has a private mainLayout field.
            // But it's the root view of the activity if we set it in setContentView.
            // Let's find the actual MainLayout if possible, or just use the decor view's content.
            // In MainActivity: setContentView(mainLayout);
            ViewGroup root = (ViewGroup) v.getRootView().findViewById(android.R.id.content);

            float absX = v.getX();
            float absY = v.getY();
            android.view.ViewParent p = v.getParent();
            while (p != null && p instanceof View && p != root) {
                View pv = (View) p;
                absX += pv.getX();
                absY += pv.getY();
                p = p.getParent();
            }

            if (v.getParent() instanceof ViewGroup) {
                ((ViewGroup) v.getParent()).removeView(v);
            }
            root.addView(v);
            v.setX(absX);
            v.setY(absY);
        }

        v.animate().scaleX(1.1f).scaleY(1.1f).alpha(0.8f).setDuration(150).start();
        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
    }

    public void handleDrag(float x, float y) {
        if (draggingView != null) {
            draggingView.setX(x - dragOffsetX);
            draggingView.setY(y - dragOffsetY);
            lastX = x;
            lastY = y;

            checkEdgeScroll(x);
        }
    }

    public void checkEdgeScroll(float x) {
        lastX = x;
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
            edgeHoldStart = 0;
            edgeScrollHandler.removeCallbacks(edgeScrollRunnable);
            cleanupEmptyPages();
            if (model != null && allApps != null) {
                refreshIcons(model, allApps);
            }
        }
    }

    public void cleanupEmptyPages() {
        if (pages.size() <= 1 || homeItems == null) return;

        int oldCurrentPage = currentPage;
        int pagesRemovedBefore = 0;
        boolean changed = false;
        List<Integer> removedIndices = new ArrayList<>();

        for (int i = pages.size() - 1; i >= 0; i--) {
            if (pages.get(i).getChildCount() == 0) {
                if (pages.size() > 1) {
                    removePage(i);
                    removedIndices.add(0, i);
                    changed = true;
                    if (i < oldCurrentPage) {
                        pagesRemovedBefore++;
                    }
                }
            }
        }

        if (changed) {
            currentPage -= pagesRemovedBefore;
            if (currentPage < 0) currentPage = 0;
            if (currentPage >= pages.size()) currentPage = Math.max(0, pages.size() - 1);

            // Update page index for all items in the master list
            for (HomeItem item : homeItems) {
                int newPage = item.page;
                for (int removedIdx : removedIndices) {
                    if (item.page > removedIdx) {
                        newPage--;
                    }
                }
                item.page = Math.max(0, Math.min(pages.size() - 1, newPage));
            }

            // Sync views with their new page indices just in case
            for (int i = 0; i < pages.size(); i++) {
                FrameLayout p = pages.get(i);
                for (int j = 0; j < p.getChildCount(); j++) {
                    View v = p.getChildAt(j);
                    if (v != null && v.getTag() instanceof HomeItem) {
                        ((HomeItem) v.getTag()).page = i;
                    }
                }
            }

            scrollToPage(currentPage);
            pageIndicator.setPageCount(pages.size());
            pageIndicator.setCurrentPage(currentPage);

            if (getContext() instanceof MainActivity) {
                ((MainActivity) getContext()).saveHomeState();
            }
        }
    }

    public void removePage(int index) {
        if (index < 0 || index >= pages.size()) return;
        FrameLayout page = pages.remove(index);
        pagesContainer.removeView(page);
    }

    public void removeItemsByPackage(String packageName) {
        if (packageName == null) return;
        boolean changed = false;
        for (FrameLayout page : pages) {
            for (int i = page.getChildCount() - 1; i >= 0; i--) {
                View v = page.getChildAt(i);
                if (v != null && v.getTag() instanceof HomeItem) {
                    HomeItem item = (HomeItem) v.getTag();
                    boolean shouldRemove = false;
                    if (item.type == HomeItem.Type.APP && packageName.equals(item.packageName)) {
                        shouldRemove = true;
                    } else if (item.type == HomeItem.Type.WIDGET) {
                        if (getContext() instanceof MainActivity) {
                            android.appwidget.AppWidgetManager awm = android.appwidget.AppWidgetManager.getInstance(getContext());
                            android.appwidget.AppWidgetProviderInfo info = awm.getAppWidgetInfo(item.widgetId);
                            if (info != null && info.provider != null && packageName.equals(info.provider.getPackageName())) {
                                shouldRemove = true;
                            }
                        }
                    } else if (item.type == HomeItem.Type.FOLDER && item.folderItems != null) {
                        // Folder itself might be removed if empty, but we also need to refresh its preview
                        // The HomeItem.folderItems was already updated in MainActivity.removePackageItems
                        // So we just check if it's now empty or needs refresh.
                        if (item.folderItems.isEmpty()) {
                            shouldRemove = true;
                        } else {
                            // If not empty, it might still have had some items removed.
                            // Handled by refreshIcons call in MainActivity.
                        }
                    }

                    if (shouldRemove) {
                        page.removeView(v);
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            cleanupEmptyPages();
        }
    }

    public void cancelDragging() {
        if (draggingView != null) {
            draggingView.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start();
        }
        draggingView = null;
        isEdgeScrolling = false;
        edgeScrollHandler.removeCallbacks(edgeScrollRunnable);
    }

    private HomeItem findCollision(View draggedView, float currentCol, float currentRow) {
        HomeItem draggedItem = (HomeItem) draggedView.getTag();
        if (draggedItem == null) return null;

        float oldCol = draggedItem.col;
        float oldRow = draggedItem.row;
        draggedItem.col = currentCol;
        draggedItem.row = currentRow;

        try {
            FrameLayout currentPageLayout = pages.get(currentPage);
            for (int i = 0; i < currentPageLayout.getChildCount(); i++) {
                View child = currentPageLayout.getChildAt(i);
                if (child == draggedView) continue;

                HomeItem targetItem = (HomeItem) child.getTag();
                if (targetItem == null) continue;

                if (isOverlapping(draggedItem, targetItem)) {
                    return targetItem;
                }
            }
        } finally {
            draggedItem.col = oldCol;
            draggedItem.row = oldRow;
        }
        return null;
    }

    private float[] getRelativeCoords(View v) {
        int[] homePos = new int[2];
        this.getLocationOnScreen(homePos);
        int[] vPos = new int[2];
        v.getLocationOnScreen(vPos);
        float xInHome = vPos[0] - homePos[0];
        float yInHome = vPos[1] - homePos[1];
        return new float[]{xInHome, yInHome};
    }

    private void snapToGrid(HomeItem item, View v) {
        int availW = getWidth() - getPaddingLeft() - getPaddingRight();
        int availH = getHeight() - getPaddingTop() - getPaddingBottom();

        int cellWidth = availW / GRID_COLUMNS;
        int cellHeight = availH / GRID_ROWS;

        float[] coords = getRelativeCoords(v);
        float xInHome = coords[0] - getPaddingLeft();
        float yInHome = coords[1] - getPaddingTop();

        float currentCol = cellWidth > 0 ? xInHome / (float) cellWidth : 0;
        float currentRow = cellHeight > 0 ? yInHome / (float) cellHeight : 0;

        item.page = currentPage;

        HomeItem target = findCollision(v, currentCol, currentRow);
        if (target != null && item.type == HomeItem.Type.APP) {
            if (target.type == HomeItem.Type.APP || target.type == HomeItem.Type.FOLDER) {
                if (getContext() instanceof MainActivity) {
                    // Remove view from root layout before merging
                    if (v.getParent() instanceof ViewGroup) {
                        ((ViewGroup) v.getParent()).removeView(v);
                    }
                    if (target.type == HomeItem.Type.APP) ((MainActivity) getContext()).mergeToFolder(target, item);
                    else ((MainActivity) getContext()).addToFolder(target, item);
                    return;
                }
            }
        }

        if (settingsManager.isFreeformHome()) {
            item.col = xInHome / (float) cellWidth;
            item.row = yInHome / (float) cellHeight;
            item.rotation = v.getRotation();
            item.scaleX = v.getScaleX();
            item.scaleY = v.getScaleY();
            item.tiltX = v.getRotationX();
            item.tiltY = v.getRotationY();
        } else {
            item.col = Math.max(0, Math.min(GRID_COLUMNS - (int) item.spanX, Math.round(xInHome / (float) cellWidth)));
            item.row = Math.max(0, Math.min(GRID_ROWS - (int) item.spanY, Math.round(yInHome / (float) cellHeight)));
            item.rotation = 0;
            item.scaleX = 1.0f;
            item.scaleY = 1.0f;
            item.tiltX = 0;
            item.tiltY = 0;
        }

        addItemView(item, v);
        shiftCollidingItems(item);

        if (getContext() instanceof MainActivity) {
            ((MainActivity) getContext()).saveHomeState();
        }
    }

    public void swipePages(float dx) {


    }

    public void scrollToPage(int page) {
        if (page < 0 || page >= pages.size()) return;
        currentPage = page;
        int targetX = page * getWidth();
        pagesContainer.animate()
                .translationX(-targetX)
                .setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    refreshIconsDebounced();
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

    private final Runnable refreshIconsRunnable = () -> {
        if (model != null && allApps != null) {
            refreshIconsInternal(model, allApps);
        }
    };

    public void refreshIconsDebounced() {
        mainHandler.removeCallbacks(refreshIconsRunnable);
        mainHandler.postDelayed(refreshIconsRunnable, 100);
    }

    public void setHomeItems(List<HomeItem> items) {
        this.homeItems = items;
    }

    public void refreshIcons(LauncherModel model, List<AppItem> allApps) {
        this.model = model;
        this.allApps = allApps;
        refreshIconsDebounced();
    }

    public void shiftCollidingItems(HomeItem movedItem) {
        shiftCollidingItemsRecursive(movedItem, 0);
    }

    private void shiftCollidingItemsRecursive(HomeItem movedItem, int depth) {
        if (homeItems == null || depth > 10) return;

        boolean isFreeform = settingsManager.isFreeformHome();

        for (HomeItem other : homeItems) {
            if (other == movedItem || other.page != movedItem.page) continue;

            if (isOverlapping(movedItem, other)) {
                // Exceptions: App -> App, App -> Folder (keep previous behavior: no repelling)
                if (movedItem.type == HomeItem.Type.APP && (other.type == HomeItem.Type.APP || other.type == HomeItem.Type.FOLDER)) {
                    continue;
                }

                boolean movedToNextPage = false;
                if (!isFreeform) {
                    // Grid Shifting Logic
                    float targetRow = other.row;
                    float targetCol = other.col;

                    if (movedItem.row + movedItem.spanY <= GRID_ROWS - other.spanY) {
                        targetRow = movedItem.row + movedItem.spanY;
                    } else if (movedItem.col + movedItem.spanX <= GRID_COLUMNS - other.spanX) {
                        targetCol = movedItem.col + movedItem.spanX;
                    } else {
                        other.page++;
                        other.row = 0;
                        other.col = 0;
                        movedToNextPage = true;
                    }

                    if (!movedToNextPage) {
                        other.row = (float) Math.ceil(targetRow);
                        other.col = (float) Math.ceil(targetCol);
                        if (isOverlapping(movedItem, other)) {
                            if (other.row < GRID_ROWS - other.spanY) other.row++;
                            else if (other.col < GRID_COLUMNS - other.spanX) other.col++;
                            else {
                                other.page++;
                                other.row = 0;
                                other.col = 0;
                                movedToNextPage = true;
                            }
                        }
                    }
                } else {
                    // Freeform Repelling Logic: Shift 'other' by minimum distance to resolve overlap
                    RectF rMoved = getVisualRect(movedItem);
                    RectF rOther = getVisualRect(other);

                    float overlapX1 = rMoved.right - rOther.left;
                    float overlapX2 = rOther.right - rMoved.left;
                    float overlapY1 = rMoved.bottom - rOther.top;
                    float overlapY2 = rOther.bottom - rMoved.top;

                    float dx = Math.min(overlapX1, overlapX2);
                    float dy = Math.min(overlapY1, overlapY2);

                    int availW = getWidth() - getPaddingLeft() - getPaddingRight();
                    int availH = getHeight() - getPaddingTop() - getPaddingBottom();
                    int cellWidth = availW / GRID_COLUMNS;
                    int cellHeight = availH / GRID_ROWS;

                    // Convert pixel overlap to grid units, adding a tiny epsilon to ensure they no longer touch
                    float epsilon = 0.01f;
                    float shiftCol = cellWidth > 0 ? (dx / cellWidth) + epsilon : epsilon;
                    float shiftRow = cellHeight > 0 ? (dy / cellHeight) + epsilon : epsilon;

                    if (dx < dy) {
                        if (overlapX1 < overlapX2) other.col += shiftCol;
                        else other.col -= shiftCol;
                    } else {
                        if (overlapY1 < overlapY2) other.row += shiftRow;
                        else other.row -= shiftRow;
                    }

                    // Clamp to page bounds in freeform
                    other.col = Math.max(0, Math.min(GRID_COLUMNS - other.spanX, other.col));
                    other.row = Math.max(0, Math.min(GRID_ROWS - other.spanY, other.row));

                    // If still overlapping after clamp, move along the other axis
                    if (isOverlapping(movedItem, other)) {
                        if (dx < dy) other.row += (overlapY1 < overlapY2 ? shiftRow : -shiftRow);
                        else other.col += (overlapX1 < overlapX2 ? shiftCol : -shiftCol);
                    }
                }

                View otherView = findViewForItem(other);
                if (otherView != null) {
                    if (movedToNextPage) {
                        addItemView(other, otherView);
                    } else {
                        updateViewPosition(other, otherView);
                    }
                }
                shiftCollidingItemsRecursive(other, depth + 1);
            }
        }
    }

    private RectF getVisualRect(HomeItem item) {
        int availW = getWidth() - getPaddingLeft() - getPaddingRight();
        int availH = getHeight() - getPaddingTop() - getPaddingBottom();
        int cellWidth = availW / GRID_COLUMNS;
        int cellHeight = availH / GRID_ROWS;

        if (cellWidth <= 0 || cellHeight <= 0) return new RectF();

        float width, height;
        if (item.type == HomeItem.Type.WIDGET || (item.type == HomeItem.Type.FOLDER && (item.spanX > 1.0f || item.spanY > 1.0f))) {
            width = cellWidth * item.spanX;
            height = cellHeight * item.spanY;
        } else {
            int size = getResources().getDimensionPixelSize(R.dimen.grid_icon_size);
            width = size * 2;
            height = size * 2;
        }

        float x, y;
        if (item.type == HomeItem.Type.APP || (item.type == HomeItem.Type.FOLDER && item.spanX <= 1.0f && item.spanY <= 1.0f)) {
            x = getPaddingLeft() + item.col * cellWidth + (cellWidth - width) / 2f;
            y = getPaddingTop() + item.row * cellHeight + (cellHeight - height) / 2f;
        } else {
            x = getPaddingLeft() + item.col * cellWidth;
            y = getPaddingTop() + item.row * cellHeight;
        }

        float visualWidth = width * item.scaleX;
        float visualHeight = height * item.scaleY;
        float visualX = x + (width - visualWidth) / 2f;
        float visualY = y + (height - visualHeight) / 2f;

        return new RectF(visualX, visualY, visualX + visualWidth, visualY + visualHeight);
    }

    private boolean isOverlapping(HomeItem a, HomeItem b) {
        RectF rA = getVisualRect(a);
        RectF rB = getVisualRect(b);

        // Allow objects to be placed as close as possible as long as the edges do not touch or overlap.
        // This means we repel if they touch or overlap.
        return rA.left <= rB.right &&
               rA.right >= rB.left &&
               rA.top <= rB.bottom &&
               rA.bottom >= rB.top;
    }

    public View findViewForItem(HomeItem item) {
        for (int p = 0; p < pages.size(); p++) {
            ViewGroup pageLayout = pages.get(p);
            if (pageLayout == null) continue;
            for (int i = 0; i < pageLayout.getChildCount(); i++) {
                View v = pageLayout.getChildAt(i);
                if (v.getTag() == item) return v;
            }
        }
        return null;
    }

    private void refreshIconsInternal(LauncherModel model, List<AppItem> allApps) {
        float globalScale = settingsManager.getIconScale();
        int baseSize = getResources().getDimensionPixelSize(R.dimen.grid_icon_size);
        int targetIconSize = (int) (baseSize * globalScale);
        boolean hideLabels = settingsManager.isHideLabels();

        Map<String, AppItem> appMap = new HashMap<>();
        if (allApps != null) {
            for (AppItem a : allApps) {
                appMap.put(a.packageName, a);
            }
        }

        int adaptiveColor = ThemeUtils.getAdaptiveColor(getContext(), settingsManager, false);

        for (FrameLayout page : pages) {
            for (int i = 0; i < page.getChildCount(); i++) {
                View view = page.getChildAt(i);
                HomeItem item = (HomeItem) view.getTag();
                if (item == null || !(view instanceof ViewGroup)) continue;

                ViewGroup container = (ViewGroup) view;
                if (item.type == HomeItem.Type.APP) {
                    ImageView iv = container.findViewWithTag("item_icon");
                    TextView tv = container.findViewWithTag("item_label");

                    if (iv != null) {
                        ViewGroup.LayoutParams lp = iv.getLayoutParams();
                        if (lp.width != targetIconSize) {
                            lp.width = targetIconSize;
                            lp.height = targetIconSize;
                            iv.setLayoutParams(lp);
                        }
                    }
                    if (tv != null) {
                        tv.setTextColor(adaptiveColor);
                        tv.setTextSize(10 * globalScale);
                        tv.setVisibility(hideLabels ? View.GONE : View.VISIBLE);
                    }

                    AppItem app = appMap.get(item.packageName);
                    if (iv != null && app != null) {
                        final AppItem finalApp = app;
                        final TextView finalTv = tv;
                        model.loadIcon(app, bitmap -> {
                            if (bitmap != null) {
                                iv.setImageBitmap(bitmap);
                                if (finalTv != null) finalTv.setText(finalApp.label);
                            }
                        });
                    }
                } else if (item.type == HomeItem.Type.FOLDER) {
                    TextView tv = container.findViewWithTag("item_label");
                    if (tv != null) {
                        tv.setTextColor(adaptiveColor);
                        tv.setTextSize(10 * globalScale);
                        tv.setVisibility(hideLabels ? View.GONE : View.VISIBLE);
                        tv.setText(item.folderName == null || item.folderName.isEmpty() ? "" : item.folderName);
                    }
                    if (getContext() instanceof MainActivity) {
                        android.widget.GridLayout grid = container.findViewWithTag("folder_grid");
                        if (grid != null) {
                            ((MainActivity) getContext()).refreshFolderPreview(item, grid);
                        }
                    }
                }
            }
        }
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
                            item.col = Math.max(0, Math.min(GRID_COLUMNS - (int) item.spanX, Math.round(item.col)));
                            item.row = Math.max(0, Math.min(GRID_ROWS - (int) item.spanY, Math.round(item.row)));
                            item.rotation = 0;
                            item.scaleX = 1.0f;
                            item.scaleY = 1.0f;
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
                    shape.setColor(getContext().getColor(R.color.foreground_dim) & 0x80FFFFFF);
                }
                dot.setBackground(shape);
                addView(dot);
            }
        }
    }
}
