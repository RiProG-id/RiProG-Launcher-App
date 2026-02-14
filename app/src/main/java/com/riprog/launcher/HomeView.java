package com.riprog.launcher;

import android.app.Activity;
import android.os.Handler;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
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

    // Reusable objects for performance
    private final Matrix tempMatrix = new Matrix();
    private final Path tempPath = new Path();
    private final RectF tempRectF = new RectF();
    private final Region tempRegion1 = new Region();
    private final Region tempRegion2 = new Region();
    private final Region tempIntersect = new Region();

    private View draggingView = null;
    private float lastX, lastY;
    private final Handler mainHandler = new Handler();
    private final Handler edgeScrollHandler = new Handler();
    private boolean isEdgeScrolling = false;

    private final Runnable edgeScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (draggingView != null || (getContext() instanceof MainActivity && ((MainActivity) getContext()).isTransforming())) {
                checkEdgeScrollLoop(lastX);
                edgeScrollHandler.postDelayed(this, 400);
            } else {
                isEdgeScrolling = false;
            }
        }
    };

    public void checkEdgeScrollLoop(float x) {
        lastX = x;
        if (x < getWidth() * 0.05f) {
            scrollToPage(currentPage - 1);
        } else if (x > getWidth() * 0.95f) {
            scrollToPage(currentPage + 1);
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
        addView(pagesContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        pageIndicator = new PageIndicator(context);
        LayoutParams indicatorParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        indicatorParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        indicatorParams.bottomMargin = dpToPx(48);
        addView(pageIndicator, indicatorParams);


        int savedPageCount = settingsManager.getPageCount();
        for (int i = 0; i < savedPageCount; i++) {
            addPage();
        }

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

        // Ensure only one instance of the view for this item exists
        View existing = findViewForItem(item);
        if (existing != null && existing != view) {
            ViewGroup parent = (ViewGroup) existing.getParent();
            if (parent != null) parent.removeView(existing);
        }

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

            performRepulsion(draggingView);
            checkEdgeScrollLoopStart(x);
        }
    }

    public void performRepulsion(View dragged) {
        if (pages.isEmpty() || currentPage >= pages.size()) return;
        FrameLayout currentPageLayout = pages.get(currentPage);

        getVisualRegion(dragged, tempRegion1);
        RectF r1 = getVisualRect(dragged);

        for (int i = 0; i < currentPageLayout.getChildCount(); i++) {
            View other = currentPageLayout.getChildAt(i);
            if (other == dragged || other == null) continue;

            getVisualRegion(other, tempRegion2);
            tempIntersect.set(tempRegion1);
            if (tempIntersect.op(tempRegion2, Region.Op.INTERSECT)) {
                Rect intersectionBounds = tempIntersect.getBounds();
                float overlapArea = intersectionBounds.width() * intersectionBounds.height();

                RectF r2 = getVisualRect(other);
                float otherArea = r2.width() * r2.height();
                float ratio = otherArea > 0 ? overlapArea / otherArea : 0;
                if (ratio > 0.5f) ratio = 0.5f;

                float dx = r2.centerX() - r1.centerX();
                float dy = r2.centerY() - r1.centerY();
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist == 0) dist = 1;

                // Proportional push
                float pushFactor = dpToPx(16) * ratio;
                other.setTranslationX((dx / dist) * pushFactor);
                other.setTranslationY((dy / dist) * pushFactor);
            } else if (other.getTranslationX() != 0 || other.getTranslationY() != 0) {
                other.setTranslationX(other.getTranslationX() * 0.8f);
                other.setTranslationY(other.getTranslationY() * 0.8f);
                if (Math.abs(other.getTranslationX()) < 0.1f) other.setTranslationX(0);
                if (Math.abs(other.getTranslationY()) < 0.1f) other.setTranslationY(0);
            }
        }
    }

    public void clearRepulsion() {
        for (FrameLayout page : pages) {
            for (int i = 0; i < page.getChildCount(); i++) {
                View v = page.getChildAt(i);
                if (v != null) {
                    v.animate().translationX(0).translationY(0).setDuration(200).start();
                }
            }
        }
    }

    public void checkEdgeScrollLoopStart(float x) {
        lastX = x;
        if (x < getWidth() * 0.05f || x > getWidth() * 0.95f) {
            if (!isEdgeScrolling) {
                isEdgeScrolling = true;
                edgeScrollHandler.postDelayed(edgeScrollRunnable, 800);
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
            clearRepulsion();
            cleanupEmptyPages();
            if (model != null && allApps != null) {
                refreshIcons(model, allApps);
            }
        }
    }

    public void cancelDragging() {
        if (draggingView != null) {
            draggingView.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start();
            HomeItem item = (HomeItem) draggingView.getTag();
            if (item != null) {
                addItemView(item, draggingView);
            }
        }
        draggingView = null;
        isEdgeScrolling = false;
        edgeScrollHandler.removeCallbacks(edgeScrollRunnable);
        clearRepulsion();
    }

    public void cleanupEmptyPages() {
        // Now only synchronizes indices, does not remove pages automatically
        for (int i = 0; i < pages.size(); i++) {
            FrameLayout p = pages.get(i);
            for (int j = 0; j < p.getChildCount(); j++) {
                View v = p.getChildAt(j);
                if (v != null && v.getTag() instanceof HomeItem) {
                    HomeItem item = (HomeItem) v.getTag();
                    item.page = i;
                }
            }
        }
        pageIndicator.setPageCount(pages.size());
        pageIndicator.setCurrentPage(currentPage);
    }

    public void removePage(int index) {
        if (index < 0 || index >= pages.size() || pages.size() <= 1) return;

        // Remove items belonging to the deleted page from the master list
        if (homeItems != null) {
            for (int i = homeItems.size() - 1; i >= 0; i--) {
                HomeItem item = homeItems.get(i);
                if (item.page == index) {
                    homeItems.remove(i);
                } else if (item.page > index) {
                    item.page--;
                }
            }
        }

        FrameLayout page = pages.remove(index);
        pagesContainer.removeView(page);

        if (currentPage >= pages.size()) {
            currentPage = pages.size() - 1;
        }

        cleanupEmptyPages();
        if (getContext() instanceof MainActivity) {
            ((MainActivity) getContext()).saveHomeState();
            scrollToPage(currentPage);
        }
    }

    public void removeItemsByPackage(String packageName) {
        if (packageName == null) return;
        boolean changed = false;
        for (FrameLayout page : pages) {
            for (int i = page.getChildCount() - 1; i >= 0; i--) {
                View v = page.getChildAt(i);
                if (v != null && v.getTag() instanceof HomeItem) {
                    HomeItem item = (HomeItem) v.getTag();
                    if (item.type == HomeItem.Type.APP && packageName.equals(item.packageName)) {
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


    private HomeItem findCollision(View draggedView) {
        HomeItem draggedItem = (HomeItem) draggedView.getTag();
        if (draggedItem == null) return null;

        FrameLayout currentPageLayout = pages.get(currentPage);
        float centerX = draggedView.getX() + draggedView.getWidth() / 2f;
        float centerY = draggedView.getY() + draggedView.getHeight() / 2f;

        for (int i = 0; i < currentPageLayout.getChildCount(); i++) {
            View child = currentPageLayout.getChildAt(i);
            if (child == draggedView) continue;

            HomeItem targetItem = (HomeItem) child.getTag();
            if (targetItem == null) continue;

            if (centerX >= child.getX() && centerX <= child.getX() + child.getWidth() &&
                    centerY >= child.getY() && centerY <= child.getY() + child.getHeight()) {
                return targetItem;
            }
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

        item.page = currentPage;

        HomeItem target = findCollision(v);
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
        if (pages.size() <= 1) {
            currentPage = 0;
            pagesContainer.setTranslationX(0);
            pageIndicator.setCurrentPage(0);
            return;
        }

        int n = pages.size();

        if (page == n && currentPage == n - 1) {
            // Forward Loop (N-1 -> 0)
            final View p0 = pages.get(0);
            pagesContainer.removeView(p0);
            pagesContainer.addView(p0);
            pagesContainer.setTranslationX(-(n - 2) * getWidth());

            pagesContainer.animate()
                    .translationX(-(n - 1) * getWidth())
                    .setDuration(300)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withEndAction(() -> {
                        pagesContainer.removeView(p0);
                        pagesContainer.addView(p0, 0);
                        pagesContainer.setTranslationX(0);
                        currentPage = 0;
                        pageIndicator.setCurrentPage(0);
                        refreshIconsDebounced();
                    }).start();
            return;
        } else if (page == -1 && currentPage == 0) {
            // Backward Loop (0 -> N-1)
            final View pLast = pages.get(n - 1);
            pagesContainer.removeView(pLast);
            pagesContainer.addView(pLast, 0);
            pagesContainer.setTranslationX(-getWidth());

            pagesContainer.animate()
                    .translationX(0)
                    .setDuration(300)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withEndAction(() -> {
                        pagesContainer.removeView(pLast);
                        pagesContainer.addView(pLast);
                        pagesContainer.setTranslationX(-(n - 1) * getWidth());
                        currentPage = n - 1;
                        pageIndicator.setCurrentPage(n - 1);
                        refreshIconsDebounced();
                    }).start();
            return;
        }

        int targetPage = (page + n) % n;
        currentPage = targetPage;
        int targetX = currentPage * getWidth();
        pagesContainer.animate()
                .translationX(-targetX)
                .setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    refreshIconsDebounced();
                })
                .start();
        pageIndicator.setCurrentPage(currentPage);
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
        if (settingsManager.isFreeformHome()) return; // Disable auto-shifting in freeform mode

        for (HomeItem other : homeItems) {
            if (other == movedItem || other.page != movedItem.page) continue;

            if (isOverlapping(movedItem, other)) {
                // Shift 'other' away.
                float targetRow = other.row;
                float targetCol = other.col;
                boolean movedToNextPage = false;

                if (movedItem.row + movedItem.spanY <= GRID_ROWS - other.spanY) {
                    targetRow = movedItem.row + movedItem.spanY;
                } else if (movedItem.col + movedItem.spanX <= GRID_COLUMNS - other.spanX) {
                    targetCol = movedItem.col + movedItem.spanX;
                } else {
                    // Move to next page
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
                            // Still no room, move to next page
                            other.page++;
                            other.row = 0;
                            other.col = 0;
                            movedToNextPage = true;
                        }
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

    public RectF getVisualRect(View v) {
        if (v == null) return new RectF();
        RectF rect = new RectF(0, 0, v.getWidth(), v.getHeight());
        tempMatrix.set(v.getMatrix());
        tempMatrix.postTranslate(v.getLeft(), v.getTop());
        tempMatrix.mapRect(rect);
        return rect;
    }

    private Region getVisualRegion(View v, Region outRegion) {
        if (v == null) {
            outRegion.setEmpty();
            return outRegion;
        }
        tempPath.reset();
        tempRectF.set(0, 0, v.getWidth(), v.getHeight());
        tempPath.addRect(tempRectF, Path.Direction.CW);
        tempMatrix.set(v.getMatrix());
        tempMatrix.postTranslate(v.getLeft(), v.getTop());
        tempPath.transform(tempMatrix);

        tempRectF.setEmpty();
        tempPath.computeBounds(tempRectF, true);
        outRegion.setPath(tempPath, new Region((int) tempRectF.left, (int) tempRectF.top, (int) tempRectF.right, (int) tempRectF.bottom));
        return outRegion;
    }

    public boolean isVisuallyOverlapping(View v1, View v2) {
        if (v1 == null || v2 == null) return false;
        getVisualRegion(v1, tempRegion1);
        getVisualRegion(v2, tempRegion2);
        tempIntersect.set(tempRegion1);
        return tempIntersect.op(tempRegion2, Region.Op.INTERSECT);
    }

    private boolean isOverlapping(HomeItem a, HomeItem b) {
        View va = findViewForItem(a);
        View vb = findViewForItem(b);
        if (va != null && vb != null) {
            return isVisuallyOverlapping(va, vb);
        }
        return a.col < b.col + b.spanX &&
               a.col + a.spanX > b.col &&
               a.row < b.row + b.spanY &&
               a.row + a.spanY > b.row;
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
        float globalScale = settingsManager.isFreeformHome() ? 1.0f : settingsManager.getIconScale();
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
