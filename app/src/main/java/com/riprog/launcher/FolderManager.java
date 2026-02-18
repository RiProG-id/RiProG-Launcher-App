package com.riprog.launcher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.riprog.launcher.HomeItem;

import java.util.List;

public class FolderManager {
    private final MainActivity activity;
    private final SettingsManager settingsManager;
    private View currentFolderOverlay;

    public FolderManager(MainActivity activity, SettingsManager settingsManager) {
        this.activity = activity;
        this.settingsManager = settingsManager;
    }

    @SuppressLint("ClickableViewAccessibility")
    public void openFolder(HomeItem folderItem, View folderView, List<HomeItem> homeItems, List<AppItem> allApps) {
        if (currentFolderOverlay != null) closeFolder();

        ThemeUtils.applyWindowBlur(activity.getWindow(), true);

        FrameLayout container = new FrameLayout(activity) {
            @Override
            public boolean performClick() {
                return super.performClick();
            }
        };
        container.setBackgroundColor(0x33000000);
        container.setOnClickListener(v -> closeFolder());

        container.setOnTouchListener(new View.OnTouchListener() {
            float startY = 0f;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getY();
                        v.performClick();
                        break;
                    case MotionEvent.ACTION_UP:
                        if (event.getY() - startY > dpToPx(100)) {
                            closeFolder();
                            return true;
                        }
                        break;
                }
                return false;
            }
        });

        LinearLayout overlay = new LinearLayout(activity);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackground(ThemeUtils.getGlassDrawable(activity, settingsManager, 12f));
        overlay.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
        overlay.setElevation(dpToPx(16));
        overlay.setGravity(Gravity.CENTER_HORIZONTAL);
        overlay.setOnClickListener(v -> {});

        int adaptiveColor = ThemeUtils.getAdaptiveColor(activity, settingsManager, true);

        TextView titleText = new TextView(activity);
        titleText.setText(folderItem.folderName == null || folderItem.folderName.isEmpty() ? "Folder" : folderItem.folderName);
        titleText.setTextColor(adaptiveColor);
        titleText.setTextSize(20f);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setGravity(Gravity.CENTER);
        titleText.setPadding(0, 0, 0, dpToPx(16));
        overlay.addView(titleText);

        EditText titleEdit = new EditText(activity);
        titleEdit.setText(folderItem.folderName);
        titleEdit.setTextColor(adaptiveColor);
        titleEdit.setBackground(null);
        titleEdit.setGravity(Gravity.CENTER);
        titleEdit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        titleEdit.setSingleLine(true);
        titleEdit.setVisibility(View.GONE);
        titleEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                folderItem.folderName = s.toString();
                activity.saveHomeState();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        overlay.addView(titleEdit);

        titleText.setOnClickListener(v -> {
            titleText.setVisibility(View.GONE);
            titleEdit.setVisibility(View.VISIBLE);
            titleEdit.requestFocus();
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(titleEdit, InputMethodManager.SHOW_IMPLICIT);
        });

        titleEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String newName = titleEdit.getText().toString();
                folderItem.folderName = newName;
                titleText.setText(newName.isEmpty() ? "Folder" : newName);
                titleEdit.setVisibility(View.GONE);
                titleText.setVisibility(View.VISIBLE);
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                activity.saveHomeState();
                activity.homeView.refreshIcons(activity.model, activity.allApps);
                return true;
            }
            return false;
        });

        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(4);
        grid.setAlignmentMode(GridLayout.ALIGN_MARGINS);
        grid.setUseDefaultMargins(true);

        int folderPadding = dpToPx(8);
        if (folderItem.folderItems != null) {
            for (HomeItem sub : folderItem.folderItems) {
                View subView = activity.createAppView(sub);
                if (subView == null) continue;
                GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                glp.setMargins(folderPadding, folderPadding, folderPadding, folderPadding);
                subView.setLayoutParams(glp);
                subView.setTag(sub);
                subView.setOnClickListener(v -> {
                    Intent launchIntent = activity.getPackageManager().getLaunchIntentForPackage(sub.packageName);
                    if (launchIntent != null) activity.startActivity(launchIntent);
                    closeFolder();
                });
                subView.setOnLongClickListener(v -> {
                    closeFolder();
                    removeFromFolder(folderItem, sub, homeItems);
                    homeItems.add(sub);
                    sub.page = activity.homeView.getCurrentPage();
                    activity.homeView.addItemView(sub, subView);
                    activity.mainLayout.startExternalDrag(subView);
                    return true;
                });
                grid.addView(subView);
            }
        }
        overlay.addView(grid);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.setMargins(dpToPx(24), 0, dpToPx(24), 0);
        container.addView(overlay, lp);

        activity.mainLayout.addView(container, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        currentFolderOverlay = container;
    }

    public void closeFolder() {
        if (currentFolderOverlay != null) {
            activity.mainLayout.removeView(currentFolderOverlay);
            currentFolderOverlay = null;
            ThemeUtils.applyWindowBlur(activity.getWindow(), false);
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(activity.mainLayout.getWindowToken(), 0);
            activity.homeView.refreshIcons(activity.model, activity.allApps);
        }
    }

    public void mergeToFolder(HomeItem target, HomeItem dragged, List<HomeItem> homeItems) {
        homeItems.remove(dragged);
        homeItems.remove(target);

        HomeItem folder = HomeItem.createFolder("", target.col, target.row, target.page);
        folder.folderItems.add(target);
        folder.folderItems.add(dragged);
        homeItems.add(folder);

        activity.homeView.refreshIcons(activity.model, activity.allApps);
        activity.renderHomeItem(folder);
        activity.saveHomeState();
    }

    public void addToFolder(HomeItem folder, HomeItem dragged, List<HomeItem> homeItems) {
        homeItems.remove(dragged);
        folder.folderItems.add(dragged);
        refreshFolderIconsOnHome(folder);
        activity.saveHomeState();
    }

    public void removeFromFolder(HomeItem folder, HomeItem item, List<HomeItem> homeItems) {
        int page = folder.page;
        folder.folderItems.remove(item);
        if (folder.folderItems.size() == 1) {
            HomeItem lastItem = folder.folderItems.get(0);
            homeItems.remove(folder);
            lastItem.col = folder.col;
            lastItem.row = folder.row;
            lastItem.page = page;
            homeItems.add(lastItem);

            removeFolderView(folder);
            activity.renderHomeItem(lastItem);
        } else {
            refreshFolderIconsOnHome(folder);
        }
        activity.saveHomeState();
    }

    private void removeFolderView(HomeItem folder) {
        ViewGroup pagesContainer = (ViewGroup) activity.homeView.getChildAt(0);
        for (int i = 0; i < pagesContainer.getChildCount(); i++) {
            ViewGroup page = (ViewGroup) pagesContainer.getChildAt(i);
            for (int j = 0; j < page.getChildCount(); j++) {
                View v = page.getChildAt(j);
                if (v.getTag() == folder) {
                    page.removeView(v);
                    return;
                }
            }
        }
    }

    public void refreshFolderIconsOnHome(HomeItem folder) {
        ViewGroup pagesContainer = (ViewGroup) activity.homeView.getChildAt(0);
        for (int i = 0; i < pagesContainer.getChildCount(); i++) {
            ViewGroup page = (ViewGroup) pagesContainer.getChildAt(i);
            for (int j = 0; j < page.getChildCount(); j++) {
                View v = page.getChildAt(j);
                if (v.getTag() == folder) {
                    GridLayout grid = findGridLayout((ViewGroup) v);
                    if (grid != null) activity.refreshFolderPreview(folder, grid);
                    return;
                }
            }
        }
    }

    private GridLayout findGridLayout(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof GridLayout) return (GridLayout) child;
            if (child instanceof ViewGroup) {
                GridLayout g = findGridLayout((ViewGroup) child);
                if (g != null) return g;
            }
        }
        return null;
    }

    public boolean isFolderOpen() {
        return currentFolderOverlay != null;
    }

    private int dpToPx(float dp) {
        return (int) android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp, activity.getResources().getDisplayMetrics());
    }
}
