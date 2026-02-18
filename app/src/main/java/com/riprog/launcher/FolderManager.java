package com.riprog.launcher;

import android.annotation.SuppressLint;
import android.content.Context;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public class FolderManager {
    private final MainActivity activity;
    private final SettingsManager settingsManager;
    private View currentFolderOverlay = null;

    public FolderManager(MainActivity activity, SettingsManager settingsManager, LauncherModel model) {
        this.activity = activity;
        this.settingsManager = settingsManager;
    }

    @SuppressLint("ClickableViewAccessibility")
    public void openFolder(HomeItem folderItem, View folderView, List<HomeItem> homeItems, List<AppItem> allApps, LauncherModel model) {
        if (currentFolderOverlay != null) closeFolder();
        activity.setOverlayBlur(true);

        FrameLayout container = new FrameLayout(activity) {
            @Override
            public boolean performClick() {
                super.performClick();
                return true;
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
                        if (event.getY() - startY > ThemeUtils.dpToPx(activity, 100)) {
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
        int p24 = ThemeUtils.dpToPx(activity, 24);
        overlay.setPadding(p24, p24, p24, p24);
        overlay.setElevation(ThemeUtils.dpToPx(activity, 16));
        overlay.setGravity(Gravity.CENTER_HORIZONTAL);
        overlay.setOnClickListener(v -> {});

        TextView titleText = new TextView(activity);
        titleText.setText(folderItem.folderName == null || folderItem.folderName.isEmpty() ? "Folder" : folderItem.folderName);
        titleText.setTextColor(ThemeUtils.getAdaptiveColor(activity, settingsManager, true));
        titleText.setTextSize(20f);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setGravity(Gravity.CENTER);
        titleText.setPadding(0, 0, 0, ThemeUtils.dpToPx(activity, 16));
        overlay.addView(titleText);

        EditText titleEdit = new EditText(activity);
        titleEdit.setText(folderItem.folderName);
        titleEdit.setTextColor(ThemeUtils.getAdaptiveColor(activity, settingsManager, true));
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
                if (activity.getHomeView() != null) activity.getHomeView().refreshIcons(model, allApps);
                return true;
            }
            return false;
        });

        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(4);
        grid.setAlignmentMode(GridLayout.ALIGN_MARGINS);
        grid.setUseDefaultMargins(true);

        int folderPadding = ThemeUtils.dpToPx(activity, 8);
        if (folderItem.folderItems != null) {
            for (HomeItem sub : folderItem.folderItems) {
                View subView = activity.renderHomeItemForFolder(sub);
                if (subView == null) continue;
                GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                glp.setMargins(folderPadding, folderPadding, folderPadding, folderPadding);
                subView.setLayoutParams(glp);
                subView.setTag(sub);
                subView.setOnClickListener(v -> {
                    activity.handleItemClick(subView);
                    closeFolder();
                });
                subView.setOnLongClickListener(v -> {
                    closeFolder();
                    removeFromFolder(folderItem, sub, homeItems);
                    homeItems.add(sub);
                    sub.page = activity.getHomeView().getCurrentPage();
                    activity.getHomeView().addItemView(sub, subView);
                    // Need a way to start dragging from current touch point
                    // For now we just add it to home screen.
                    // Ideally we'd trigger a drag in MainActivity.
                    return true;
                });
                grid.addView(subView);
            }
        }
        overlay.addView(grid);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.setMargins(p24, 0, p24, 0);
        container.addView(overlay, lp);

        ViewGroup root = activity.findViewById(android.R.id.content);
        root.addView(container, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        currentFolderOverlay = container;
    }

    public void closeFolder() {
        if (currentFolderOverlay != null) {
            ViewGroup root = (ViewGroup) currentFolderOverlay.getParent();
            if (root != null) root.removeView(currentFolderOverlay);
            currentFolderOverlay = null;
            activity.setOverlayBlur(false);
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);
            if (activity.getHomeView() != null) {
                activity.getHomeView().refreshIcons(activity.getModel(), activity.getAllApps());
            }
        }
    }

    public HomeItem createFolder(HomeItem item1, HomeItem item2, int page, float col, float row) {
        HomeItem folder = HomeItem.createFolder("", col, row, page);
        folder.folderItems.add(item1);
        folder.folderItems.add(item2);
        folder.rotation = 0f;
        folder.scale = 1.0f;
        folder.tiltX = 0f;
        folder.tiltY = 0f;
        return folder;
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
            lastItem.rotation = folder.rotation;
            lastItem.scale = folder.scale;
            lastItem.tiltX = folder.tiltX;
            lastItem.tiltY = folder.tiltY;
            homeItems.add(lastItem);

            removeFolderView(folder);
            activity.renderHomeItem(lastItem);
        } else {
            refreshFolderIconsOnHome(folder);
        }
        activity.saveHomeState();
    }

    private void removeFolderView(HomeItem folder) {
        if (activity.getHomeView() != null) {
            activity.getHomeView().removeViewForItem(folder);
        }
    }

    public void refreshFolderIconsOnHome(HomeItem folder) {
        if (activity.getHomeView() != null) {
            activity.getHomeView().updateFolderPreview(folder);
        }
    }

    public boolean isFolderOpen() {
        return currentFolderOverlay != null;
    }
}
