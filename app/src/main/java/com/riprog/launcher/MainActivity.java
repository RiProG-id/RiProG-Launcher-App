package com.riprog.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQUEST_PICK_APPWIDGET = 1;
    private static final int REQUEST_CREATE_APPWIDGET = 2;
    private static final int APPWIDGET_HOST_ID = 1024;
    private static final ExecutorService widgetPreviewExecutor = Executors.newFixedThreadPool(4);

    private LauncherModel model;
    private SettingsManager settingsManager;
    private AppWidgetHost appWidgetHost;
    private AppWidgetManager appWidgetManager;
    private MainLayout mainLayout;
    private HomeView homeView;
    private DrawerView drawerView;
    private View currentFolderOverlay = null;
    private TransformOverlay currentTransformOverlay = null;
    private ViewGroup transformingViewOriginalParent;
    private int transformingViewOriginalIndex;
    private View transformingView;
    private AppInstallReceiver appInstallReceiver;
    private List<HomeItem> homeItems = new ArrayList<>();
    private List<AppItem> allApps = new ArrayList<>();
    private float lastGridCol, lastGridRow;
    private boolean isStateRestored = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(this);
        applyThemeMode(settingsManager.getThemeMode());

        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);
        }

        model = ((LauncherApplication) getApplication()).getModel();

        mainLayout = new MainLayout(this);
        homeView = new HomeView(this);
        drawerView = new DrawerView(this);
        drawerView.setColumns(settingsManager.getColumns());
        drawerView.setOnAppLongClickListener(app -> {
            mainLayout.closeDrawer();
            HomeItem item = HomeItem.createApp(app.packageName, app.className, 0, 0, homeView.getCurrentPage());
            homeItems.add(item);
            View view = createAppView(item, false);
            homeView.addItemView(item, view);
            saveHomeState();
            mainLayout.startExternalDrag(view);
        });

        mainLayout.addView(homeView);
        mainLayout.addView(drawerView);
        drawerView.setVisibility(View.GONE);

        mainLayout.setOnApplyWindowInsetsListener((v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                drawerView.setSystemInsets(bars.left, bars.top, bars.right, bars.bottom);
                homeView.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                drawerView.setSystemInsets(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                homeView.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        setContentView(mainLayout);

        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new AppWidgetHost(this, APPWIDGET_HOST_ID);
        appWidgetHost.startListening();

        applyDynamicColors();
        loadApps();
        registerAppInstallReceiver();

        homeView.post(() -> {
            restoreHomeState();
            homeView.setHomeItems(homeItems);
            showDefaultLauncherPrompt();
        });
    }

    private boolean isDefaultLauncher() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo resolveInfo = getPackageManager().resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo == null || resolveInfo.activityInfo == null) return false;
        return getPackageName().equals(resolveInfo.activityInfo.packageName);
    }

    private void showDefaultLauncherPrompt() {
        if (isDefaultLauncher()) return;

        long lastShown = settingsManager.getLastDefaultPromptTimestamp();
        int count = settingsManager.getDefaultPromptCount();

        if (System.currentTimeMillis() - lastShown < 24 * 60 * 60 * 1000) return;
        if (count >= 5) return;

        LinearLayout prompt = new LinearLayout(this);
        prompt.setOrientation(LinearLayout.VERTICAL);
        prompt.setBackground(ThemeUtils.getGlassDrawable(this, settingsManager, 12));
        prompt.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
        prompt.setGravity(Gravity.CENTER);
        prompt.setElevation(dpToPx(8));

        int adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true);

        TextView title = new TextView(this);
        title.setText(R.string.prompt_default_launcher_title);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(adaptiveColor);
        prompt.addView(title);

        TextView message = new TextView(this);
        message.setText(R.string.prompt_default_launcher_message);
        message.setPadding(0, dpToPx(8), 0, dpToPx(16));
        message.setGravity(Gravity.CENTER);
        message.setTextColor(adaptiveColor & 0xBBFFFFFF);
        prompt.addView(message);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);

        TextView btnLater = new TextView(this);
        btnLater.setText(R.string.action_later);
        btnLater.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        btnLater.setTextColor(adaptiveColor);
        btnLater.setOnClickListener(v -> mainLayout.removeView(prompt));
        buttons.addView(btnLater);

        TextView btnSet = new TextView(this);
        btnSet.setText(R.string.action_set_default);
        btnSet.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        btnSet.setTextColor(getColor(android.R.color.holo_blue_dark));
        btnSet.setTypeface(null, Typeface.BOLD);
        btnSet.setOnClickListener(v -> {
            mainLayout.removeView(prompt);
            Intent intent = new Intent(android.provider.Settings.ACTION_HOME_SETTINGS);
            startActivity(intent);
        });
        buttons.addView(btnSet);

        prompt.addView(buttons);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dpToPx(300), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        mainLayout.addView(prompt, lp);

        settingsManager.setLastDefaultPromptTimestamp(System.currentTimeMillis());
        settingsManager.incrementDefaultPromptCount();
    }

    public void saveHomeState() {
        if (isStateRestored) {
            int pageCount = homeView != null ? homeView.getPageCount() : 1;
            settingsManager.saveHomeItems(homeItems, pageCount);
        }
    }

    public boolean isTransforming() {
        return currentTransformOverlay != null;
    }

    private void restoreHomeState() {
        homeItems = settingsManager.getHomeItems();
        if (homeView != null) homeView.setHomeItems(homeItems);
        if (homeItems.isEmpty()) {
            setupDefaultHome();
        } else {
            for (HomeItem item : homeItems) {
                renderHomeItem(item);
            }
        }
        isStateRestored = true;
    }

    private void setupDefaultHome() {
        saveHomeState();
    }

    private void setOverlayBlur(boolean enabled) {
        setOverlayBlur(enabled, false);
    }

    private void setOverlayBlur(boolean enabled, boolean isTransform) {
        if (!settingsManager.isLiquidGlass()) return;
        if (!isTransform) {
            ThemeUtils.applyBlurIfSupported(homeView, enabled);
        } else {
            ThemeUtils.applyBlurIfSupported(homeView, false);
        }
        ThemeUtils.applyWindowBlur(getWindow(), enabled);
    }

    private void renderHomeItem(HomeItem item) {
        if (item == null || item.type == null) return;
        View view = null;
        switch (item.type) {
            case APP:
                view = createAppView(item, false);
                break;
            case FOLDER:
                view = createFolderView(item, false);
                break;
            case WIDGET:
                view = createWidgetView(item);
                break;
            case CLOCK:
                view = createClockView(item);
                break;
        }
        if (view != null && homeView != null) {
            homeView.addItemView(item, view);
        }
    }

    private View createAppView(HomeItem item, boolean isOnGlass) {
        if (item == null) return null;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);

        ImageView iconView = new ImageView(this);
        iconView.setTag("item_icon");
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int baseSize = getResources().getDimensionPixelSize(R.dimen.grid_icon_size);
        float scale = settingsManager.getIconScale();
        int size = (int) (baseSize * scale);

        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(size, size);
        iconView.setLayoutParams(iconParams);

        TextView labelView = new TextView(this);
        labelView.setTag("item_label");
        labelView.setTextColor(ThemeUtils.getAdaptiveColor(this, settingsManager, isOnGlass));
        labelView.setTextSize(10 * scale);
        labelView.setGravity(Gravity.CENTER);
        labelView.setMaxLines(1);
        labelView.setEllipsize(android.text.TextUtils.TruncateAt.END);

        AppItem app = findApp(item.packageName);
        if (app != null) {
            model.loadIcon(app, iconView::setImageBitmap);
            labelView.setText(app.label);
        } else {
            iconView.setImageResource(android.R.drawable.sym_def_app_icon);
            labelView.setText("...");
        }

        container.addView(iconView);
        container.addView(labelView);
        if (settingsManager.isHideLabels()) {
            labelView.setVisibility(View.GONE);
        }
        return container;
    }

    private View createFolderView(HomeItem item, boolean isOnGlass) {
        if (item == null) return null;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);

        int cellWidth = homeView.getWidth() / HomeView.GRID_COLUMNS;
        int cellHeight = homeView.getHeight() / HomeView.GRID_ROWS;

        FrameLayout previewContainer = new FrameLayout(this);
        int sizeW, sizeH;
        float scale = settingsManager.getIconScale();

        if (item.spanX <= 1.0f && item.spanY <= 1.0f) {
            int baseSize = getResources().getDimensionPixelSize(R.dimen.grid_icon_size);
            sizeW = (int) (baseSize * scale);
            sizeH = sizeW;
        } else {
            sizeW = (int) (cellWidth * item.spanX);
            sizeH = (int) (cellHeight * item.spanY);
        }

        previewContainer.setLayoutParams(new LinearLayout.LayoutParams(sizeW, sizeH));
        previewContainer.setBackground(ThemeUtils.getGlassDrawable(this, settingsManager, 12));
        int padding = dpToPx(6);
        previewContainer.setPadding(padding, padding, padding, padding);

        GridLayout grid = new GridLayout(this);
        grid.setTag("folder_grid");
        grid.setColumnCount(2);
        grid.setRowCount(2);
        previewContainer.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        refreshFolderPreview(item, grid);

        TextView labelView = new TextView(this);
        labelView.setTextColor(ThemeUtils.getAdaptiveColor(this, settingsManager, isOnGlass));
        labelView.setTextSize(10 * scale);
        labelView.setGravity(Gravity.CENTER);
        labelView.setMaxLines(1);
        labelView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labelView.setText(item.folderName == null || item.folderName.isEmpty() ? "" : item.folderName);

        container.addView(previewContainer);
        container.addView(labelView);
        if (settingsManager.isHideLabels()) {
            labelView.setVisibility(View.GONE);
        }
        return container;
    }

    public void refreshFolderPreview(HomeItem folder, GridLayout grid) {
        grid.removeAllViews();
        if (folder.folderItems == null) return;

        int count = folder.folderItems.size();
        if (count == 0) return;

        int cellWidth = homeView.getWidth() / HomeView.GRID_COLUMNS;
        int cellHeight = homeView.getHeight() / HomeView.GRID_ROWS;

        int folderW, folderH;
        float scale = settingsManager.getIconScale();
        boolean isSmall = folder.spanX <= 1.0f && folder.spanY <= 1.0f;

        if (isSmall) {
            int baseSize = getResources().getDimensionPixelSize(R.dimen.grid_icon_size);
            folderW = (int) (baseSize * scale);
            folderH = folderW;
        } else {
            folderW = (int) (cellWidth * folder.spanX);
            folderH = (int) (cellHeight * folder.spanY);
        }

        int padding = dpToPx(isSmall ? 6 : 12);
        int availableW = folderW - 2 * padding;
        int availableH = folderH - 2 * padding;

        int columns = isSmall ? 2 : (int) Math.max(2, Math.round(folder.spanX));
        if (columns > 4) columns = 4;

        int maxIcons = columns * columns;
        int iconsToShow = Math.min(count, maxIcons);
        if (isSmall) iconsToShow = Math.min(count, 4);

        grid.setColumnCount(columns);
        grid.setRowCount((int) Math.ceil(iconsToShow / (double) columns));

        int iconSize = Math.min(availableW / columns, availableH / columns);
        int iconMargin = dpToPx(isSmall ? 1 : 4);
        iconSize -= 2 * iconMargin;

        for (int i = 0; i < iconsToShow; i++) {
            HomeItem sub = folder.folderItems.get(i);
            ImageView iv = new ImageView(this);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = iconSize;
            lp.height = iconSize;
            lp.setMargins(iconMargin, iconMargin, iconMargin, iconMargin);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            AppItem app = findApp(sub.packageName);
            if (app != null) {
                model.loadIcon(app, iv::setImageBitmap);
            }
            grid.addView(iv);
        }
    }

    private void openFolder(HomeItem folderItem, View folderView) {
        if (currentFolderOverlay != null) closeFolder();
        setOverlayBlur(true);

        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(0x33000000);
        container.setOnClickListener(v -> closeFolder());

        container.setOnTouchListener(new View.OnTouchListener() {
            float startY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getY();
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

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackground(ThemeUtils.getGlassDrawable(this, settingsManager, 12));
        overlay.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
        overlay.setElevation(dpToPx(16));
        overlay.setGravity(Gravity.CENTER_HORIZONTAL);
        overlay.setOnClickListener(v -> {});

        TextView titleText = new TextView(this);
        String name = folderItem.folderName == null || folderItem.folderName.isEmpty() ? "Folder" : folderItem.folderName;
        titleText.setText(name);
        titleText.setTextColor(ThemeUtils.getAdaptiveColor(this, settingsManager, true));
        titleText.setTextSize(20);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setGravity(Gravity.CENTER);
        titleText.setPadding(0, 0, 0, dpToPx(16));
        overlay.addView(titleText);

        EditText titleEdit = new EditText(this);
        titleEdit.setText(folderItem.folderName);
        titleEdit.setTextColor(ThemeUtils.getAdaptiveColor(this, settingsManager, true));
        titleEdit.setBackground(null);
        titleEdit.setGravity(Gravity.CENTER);
        titleEdit.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        titleEdit.setSingleLine(true);
        titleEdit.setVisibility(View.GONE);
        titleEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                folderItem.folderName = s.toString();
                saveHomeState();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        overlay.addView(titleEdit);

        titleText.setOnClickListener(v -> {
            titleText.setVisibility(View.GONE);
            titleEdit.setVisibility(View.VISIBLE);
            titleEdit.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(titleEdit, InputMethodManager.SHOW_IMPLICIT);
        });

        titleEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                String newName = titleEdit.getText().toString();
                folderItem.folderName = newName;
                titleText.setText(newName.isEmpty() ? "Folder" : newName);
                titleEdit.setVisibility(View.GONE);
                titleText.setVisibility(View.VISIBLE);
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                saveHomeState();
                if (homeView != null) homeView.refreshIcons(model, allApps);
                return true;
            }
            return false;
        });

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setAlignmentMode(GridLayout.ALIGN_MARGINS);
        grid.setUseDefaultMargins(true);

        int folderPadding = dpToPx(8);

        for (HomeItem sub : folderItem.folderItems) {
            View subView = createAppView(sub, true);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.setMargins(folderPadding, folderPadding, folderPadding, folderPadding);
            subView.setLayoutParams(glp);

            subView.setTag(sub);
            subView.setOnClickListener(v -> {
                handleAppLaunch(sub.packageName);
                closeFolder();
            });
            subView.setOnLongClickListener(v -> {
                closeFolder();
                removeFromFolder(folderItem, sub);
                homeItems.add(sub);
                sub.page = homeView.getCurrentPage();
                homeView.addItemView(sub, subView);
                mainLayout.startExternalDrag(subView);
                return true;
            });
            grid.addView(subView);
        }
        overlay.addView(grid);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.setMargins(dpToPx(24), 0, dpToPx(24), 0);
        container.addView(overlay, lp);

        mainLayout.addView(container, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        currentFolderOverlay = container;
    }

    private void handleAppLaunch(String packageName) {
        if (packageName == null) return;
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                startActivity(intent);
            }
        } catch (Exception ignored) {}
    }

    public void mergeToFolder(HomeItem target, HomeItem dragged) {
        homeItems.remove(dragged);
        homeItems.remove(target);

        HomeItem folder = HomeItem.createFolder("", target.col, target.row, target.page);
        folder.folderItems.add(target);
        folder.folderItems.add(dragged);
        // Reset transformations for new 1x1 folder
        folder.rotation = 0;
        folder.scaleX = 1.0f;
        folder.scaleY = 1.0f;
        folder.tiltX = 0;
        folder.tiltY = 0;
        homeItems.add(folder);

        if (homeView != null) {
            homeView.removeItemsByPackage(target.packageName);
            homeView.removeItemsByPackage(dragged.packageName);
            renderHomeItem(folder);
        }
        saveHomeState();
    }

    public void addToFolder(HomeItem folder, HomeItem dragged) {
        homeItems.remove(dragged);
        folder.folderItems.add(dragged);

        if (homeView != null) {
            homeView.removeItemsByPackage(dragged.packageName);
            refreshFolderIconsOnHome(folder);
        }
        saveHomeState();
    }

    private void removeFromFolder(HomeItem folder, HomeItem item) {
        folder.folderItems.remove(item);
        if (folder.folderItems.size() == 1) {
            HomeItem lastItem = folder.folderItems.get(0);
            homeItems.remove(folder);
            lastItem.col = folder.col;
            lastItem.row = folder.row;
            lastItem.page = folder.page;
            lastItem.rotation = folder.rotation;
            lastItem.scaleX = folder.scaleX;
            lastItem.scaleY = folder.scaleY;
            lastItem.tiltX = folder.tiltX;
            lastItem.tiltY = folder.tiltY;
            homeItems.add(lastItem);

            if (homeView != null) {
                removeFolderView(folder);
                renderHomeItem(lastItem);
            }
        } else {
            refreshFolderIconsOnHome(folder);
        }
        saveHomeState();
    }

    private void removeFolderView(HomeItem folder) {
        ViewGroup pagesContainer = (ViewGroup) homeView.getChildAt(0);
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

    private void refreshFolderIconsOnHome(HomeItem folder) {
        ViewGroup pagesContainer = (ViewGroup) homeView.getChildAt(0);
        for (int i = 0; i < pagesContainer.getChildCount(); i++) {
            ViewGroup page = (ViewGroup) pagesContainer.getChildAt(i);
            for (int j = 0; j < page.getChildCount(); j++) {
                View v = page.getChildAt(j);
                if (v.getTag() == folder) {
                    GridLayout grid = findGridLayout((ViewGroup) v);
                    if (grid != null) refreshFolderPreview(folder, grid);
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

    private TextView findTextView(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof TextView) return (TextView) child;
            if (child instanceof ViewGroup) {
                TextView tv = findTextView((ViewGroup) child);
                if (tv != null) return tv;
            }
        }
        return null;
    }

    private View createWidgetView(HomeItem item) {
        if (appWidgetManager == null || item == null || appWidgetHost == null) return null;
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(item.widgetId);
        if (info == null) return null;
        try {
            AppWidgetHostView hostView = appWidgetHost.createView(this, item.widgetId, info);
            if (hostView != null) {
                hostView.setAppWidget(item.widgetId, info);
            }
            return hostView;
        } catch (Exception e) {
            return null;
        }
    }

    private void showWidgetOptions(HomeItem item, View hostView) {
        String[] options = {getString(R.string.action_resize), getString(R.string.action_remove)};
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setItems(options, (d, which) -> {
                if (which == 0) showResizeDialog(item, hostView);
                else removeHomeItem(item, hostView);
            }).create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(ThemeUtils.getGlassDrawable(this, settingsManager));
            ThemeUtils.applyWindowBlur(dialog.getWindow(), settingsManager.isLiquidGlass());
        }
    }

    private void showResizeDialog(HomeItem item, View hostView) {
        String[] sizes = {"1x1", "2x1", "2x2", "4x2", "4x1"};
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.title_resize_widget)
            .setItems(sizes, (d, which) -> {
                switch (which) {
                    case 0: item.spanX = 1; item.spanY = 1; break;
                    case 1: item.spanX = 2; item.spanY = 1; break;
                    case 2: item.spanX = 2; item.spanY = 2; break;
                    case 3: item.spanX = 4; item.spanY = 2; break;
                    case 4: item.spanX = 4; item.spanY = 1; break;
                }
                homeView.updateViewPosition(item, hostView);
                saveHomeState();
            }).create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(ThemeUtils.getGlassDrawable(this, settingsManager));
            ThemeUtils.applyWindowBlur(dialog.getWindow(), settingsManager.isLiquidGlass());
        }
    }

    private void removeHomeItem(HomeItem item, View view) {
        homeItems.remove(item);
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        saveHomeState();
        if (homeView != null) {
            homeView.cleanupEmptyPages();
            homeView.refreshIcons(model, allApps);
        }
    }

    private void removePackageItems(String packageName) {
        if (packageName == null) return;
        boolean changed = false;
        for (int i = homeItems.size() - 1; i >= 0; i--) {
            HomeItem item = homeItems.get(i);
            if (item.type == HomeItem.Type.APP && packageName.equals(item.packageName)) {
                homeItems.remove(i);
                changed = true;
            }
        }
        if (changed) {
            if (homeView != null) {
                homeView.removeItemsByPackage(packageName);
                homeView.refreshIcons(model, allApps);
            }
            saveHomeState();
        }
    }

    private void showAppInfo(HomeItem item) {
        if (item == null || item.packageName == null || item.packageName.isEmpty()) return;
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + item.packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.app_info_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private View createClockView(HomeItem item) {
        LinearLayout clockRoot = new LinearLayout(this);
        clockRoot.setOrientation(LinearLayout.VERTICAL);
        clockRoot.setGravity(Gravity.CENTER);

        TextView tvTime = new TextView(this);
        tvTime.setTextSize(64);
        tvTime.setTextColor(ThemeUtils.getAdaptiveColor(this, settingsManager, false));
        tvTime.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));

        TextView tvDate = new TextView(this);
        tvDate.setTextSize(18);
        int adaptiveDim = ThemeUtils.getAdaptiveColor(this, settingsManager, false) & 0xBBFFFFFF;
        tvDate.setTextColor(adaptiveDim);
        tvDate.setGravity(Gravity.CENTER);

        clockRoot.addView(tvTime);
        clockRoot.addView(tvDate);

        Runnable updateTask = new Runnable() {
            @Override
            public void run() {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                tvTime.setText(android.text.format.DateFormat.getTimeFormat(MainActivity.this).format(cal.getTime()));
                tvDate.setText(android.text.format.DateFormat.getMediumDateFormat(MainActivity.this).format(cal.getTime()));
                tvTime.postDelayed(this, 10000);
            }
        };
        tvTime.post(updateTask);
        return clockRoot;
    }

    private AppItem findApp(String packageName) {
        if (allApps == null) return null;
        for (AppItem app : allApps) {
            if (app.packageName.equals(packageName)) return app;
        }
        return null;
    }

    private void showHomeContextMenu(float col, float row, int page) {
        List<String> optionsList = new ArrayList<>();
        List<Integer> iconsList = new ArrayList<>();

        optionsList.add(getString(R.string.menu_widgets));
        iconsList.add(R.drawable.ic_widgets);
        optionsList.add(getString(R.string.menu_wallpaper));
        iconsList.add(R.drawable.ic_wallpaper);
        optionsList.add(getString(R.string.menu_settings));
        iconsList.add(R.drawable.ic_settings);

        optionsList.add(getString(R.string.layout_add_page));
        iconsList.add(R.drawable.ic_layout);

        if (homeView != null && homeView.getPageCount() > 1) {
            optionsList.add(getString(R.string.layout_remove_page));
            iconsList.add(R.drawable.ic_remove);
        }

        String[] options = optionsList.toArray(new String[0]);
        int[] icons = new int[iconsList.size()];
        for (int i = 0; i < iconsList.size(); i++) icons[i] = iconsList.get(i);

        int adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, android.R.id.text1, options) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = view.findViewById(android.R.id.text1);
                tv.setCompoundDrawablesWithIntrinsicBounds(icons[position], 0, 0, 0);
                tv.setCompoundDrawablePadding(dpToPx(16));
                tv.setTextColor(adaptiveColor);
                Drawable d = tv.getCompoundDrawables()[0];
                if (d != null) d.setTint(adaptiveColor);
                return view;
            }
        };

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.title_home_menu)
            .setAdapter(adapter, (d, which) -> {
                String option = options[which];
                if (option.equals(getString(R.string.menu_widgets))) {
                    if (!settingsManager.isFreeformHome()) {
                        lastGridCol = Math.round(col);
                        lastGridRow = Math.round(row);
                    } else {
                        lastGridCol = col;
                        lastGridRow = row;
                    }
                    pickWidget();
                } else if (option.equals(getString(R.string.menu_wallpaper))) {
                    openWallpaperPicker();
                } else if (option.equals(getString(R.string.menu_settings))) {
                    openSettings();
                } else if (option.equals(getString(R.string.layout_add_page))) {
                    if (homeView != null) {
                        homeView.addPage();
                        saveHomeState();
                        Toast.makeText(this, R.string.page_added, Toast.LENGTH_SHORT).show();
                    }
                } else if (option.equals(getString(R.string.layout_remove_page))) {
                    if (homeView != null && homeView.getPageCount() > 1) {
                        homeView.removePage(homeView.getCurrentPage());
                        saveHomeState();
                    }
                }
            }).create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(ThemeUtils.getGlassDrawable(this, settingsManager));
            ThemeUtils.applyWindowBlur(dialog.getWindow(), settingsManager.isLiquidGlass());
        }
    }

    private void pickAppForHome(int col, int row, int page) {
        if (allApps.isEmpty()) {
            Toast.makeText(this, getString(R.string.apps_not_loaded), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[allApps.size()];
        for (int i = 0; i < allApps.size(); i++) labels[i] = allApps.get(i).label;

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.title_pick_app)
            .setItems(labels, (d, which) -> {
                AppItem selected = allApps.get(which);
                HomeItem item = HomeItem.createApp(selected.packageName, selected.className, col, row, page);
                homeItems.add(item);
                renderHomeItem(item);
                saveHomeState();
            }).create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(ThemeUtils.getGlassDrawable(this, settingsManager));
            ThemeUtils.applyWindowBlur(dialog.getWindow(), settingsManager.isLiquidGlass());
        }
    }

    private void openWallpaperPicker() {
        Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
        try {
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT);
                pickIntent.setType("image/*");
                startActivity(Intent.createChooser(pickIntent, getString(R.string.title_select_wallpaper)));
            } catch (Exception e2) {
                Toast.makeText(this, getString(R.string.wallpaper_picker_failed), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openSettings() {
        try {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivityForResult(intent, 100);
        } catch (Exception e) {
            Toast.makeText(this, "Launcher settings could not be opened", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyDynamicColors() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                int accentColor = getResources().getColor(android.R.color.system_accent1_400, getTheme());
                if (homeView != null) homeView.setAccentColor(accentColor);
                if (drawerView != null) drawerView.setAccentColor(accentColor);
            } catch (Exception ignored) {}
        }
    }

    private void loadApps() {
        if (model == null) return;
        model.loadApps(apps -> {
            if (apps == null) return;
            this.allApps = apps;
            if (drawerView != null) {
                drawerView.setApps(apps, model);
            }
            if (homeView != null) {
                homeView.refreshIcons(model, apps);
            }
        });
    }

    private void registerAppInstallReceiver() {
        appInstallReceiver = new AppInstallReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(appInstallReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(appInstallReceiver, filter);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (model != null) {
            model.onTrimMemory(level);
        }
        if (level >= TRIM_MEMORY_MODERATE) {
            if (allApps != null) {
                allApps.clear();
                allApps = null;
            }
            System.gc();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (mainLayout != null) {
            mainLayout.closeDrawer();
        }
        if (homeView != null) {
            homeView.scrollToPage(0);
        }
    }

    @Override
    public void onBackPressed() {
        if (currentTransformOverlay != null) {
            closeTransformOverlay();
            return;
        }
        if (currentFolderOverlay != null) {
            closeFolder();
            return;
        }
        if (mainLayout != null && mainLayout.isDrawerOpen) {
            mainLayout.closeDrawer();
        }
    }

    private void updateHomeItemFromTransform() {
        if (transformingView == null || transformingViewOriginalParent == null) return;
        HomeItem item = (HomeItem) transformingView.getTag();
        boolean isFreeform = settingsManager.isFreeformHome();

        if (homeView != null) {
            item.page = homeView.getCurrentPage();
            // Important: update the original parent so closeTransformOverlay puts it in the current page
            ViewGroup pagesContainer = (ViewGroup) homeView.getChildAt(0);
            if (item.page < pagesContainer.getChildCount()) {
                transformingViewOriginalParent = (ViewGroup) pagesContainer.getChildAt(item.page);
                transformingViewOriginalIndex = -1;
            }
        }

        int cellWidth = transformingViewOriginalParent.getWidth() / HomeView.GRID_COLUMNS;
        int cellHeight = transformingViewOriginalParent.getHeight() / HomeView.GRID_ROWS;

        if (isFreeform) {
            item.rotation = transformingView.getRotation();
            item.scaleX = transformingView.getScaleX();
            item.scaleY = transformingView.getScaleY();
            item.tiltX = transformingView.getRotationX();
            item.tiltY = transformingView.getRotationY();
        } else {
            item.rotation = 0;
            item.scaleX = 1.0f;
            item.scaleY = 1.0f;
            item.tiltX = 0;
            item.tiltY = 0;
            if ((item.type == HomeItem.Type.WIDGET || item.type == HomeItem.Type.FOLDER) && cellWidth > 0 && cellHeight > 0) {
                item.spanX = Math.round(transformingView.getWidth() / (float) cellWidth);
                item.spanY = Math.round(transformingView.getHeight() / (float) cellHeight);
                if (item.spanX < 1) item.spanX = 1;
                if (item.spanY < 1) item.spanY = 1;
            }
        }

        int[] pagePos = new int[2];
        transformingViewOriginalParent.getLocationOnScreen(pagePos);
        int[] rootPos = new int[2];
        mainLayout.getLocationOnScreen(rootPos);

        float xInParent = transformingView.getX() - (pagePos[0] - rootPos[0]);
        float yInParent = transformingView.getY() - (pagePos[1] - rootPos[1]);

        if (cellWidth > 0) {
            float col = xInParent / (float) cellWidth;
            item.col = isFreeform ? col : Math.round(col);
        }
        if (cellHeight > 0) {
            float row = yInParent / (float) cellHeight;
            item.row = isFreeform ? row : Math.round(row);
        }

    }

    private void showTransformOverlay(View targetView) {
        if (currentTransformOverlay != null) return;
        setOverlayBlur(true, true);
        transformingView = targetView;
        transformingViewOriginalParent = (ViewGroup) targetView.getParent();
        transformingViewOriginalIndex = transformingViewOriginalParent.indexOfChild(targetView);

        float x = targetView.getX();
        float y = targetView.getY();
        android.view.ViewParent p = targetView.getParent();
        while (p != null && p != mainLayout) {
            if (p instanceof View) {
                View pv = (View) p;
                x += pv.getX();
                y += pv.getY();
            }
            p = p.getParent();
        }

        transformingViewOriginalParent.removeView(targetView);
        mainLayout.addView(targetView);
        targetView.setX(x);
        targetView.setY(y);

        currentTransformOverlay = new TransformOverlay(this, targetView, settingsManager, new TransformOverlay.OnSaveListener() {
            @Override public void onMove(float x, float y) {
                if (homeView != null) {
                    homeView.checkEdgeScrollLoopStart(x);
                }
            }
            @Override public void onMoveStart(float x, float y) {
                if (homeView != null) {
                    homeView.initialDragX = x;
                }
            }
            @Override public void onSave() {
                updateHomeItemFromTransform();
                saveHomeState();
                closeTransformOverlay();
            }
            @Override public void onCancel() {
                closeTransformOverlay();
            }
            @Override public void onRemove() {
                removeHomeItem((HomeItem) targetView.getTag(), targetView);
                transformingView = null; // Prevent re-adding in closeTransformOverlay
                closeTransformOverlay();
            }
            @Override public void onAppInfo() {
                showAppInfo((HomeItem) targetView.getTag());
            }
            @Override public void onCollision(View otherView) {
                updateHomeItemFromTransform();
                saveHomeState();
                closeTransformOverlay();
                showTransformOverlay(otherView);
            }
            @Override public View findItemAt(float x, float y, View exclude) {
                return findHomeItemAtRoot(x, y, exclude);
            }
        });
        mainLayout.addView(currentTransformOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private View findHomeItemAtRoot(float x, float y, View exclude) {
        if (homeView == null) return null;
        int page = homeView.getCurrentPage();
        ViewGroup pagesContainer = (ViewGroup) homeView.getChildAt(0);
        if (pagesContainer != null && page < pagesContainer.getChildCount()) {
            ViewGroup pageLayout = (ViewGroup) pagesContainer.getChildAt(page);

            int[] pagePos = new int[2];
            pageLayout.getLocationOnScreen(pagePos);
            int[] rootPos = new int[2];
            mainLayout.getLocationOnScreen(rootPos);

            float adjustedX = x - (pagePos[0] - rootPos[0]);
            float adjustedY = y - (pagePos[1] - rootPos[1]);

            for (int i = pageLayout.getChildCount() - 1; i >= 0; i--) {
                View child = pageLayout.getChildAt(i);
                if (child == exclude) continue;

                RectF visualRect = homeView.getVisualRect(child);
                if (visualRect.contains(adjustedX, adjustedY)) {
                    return child;
                }
            }
        }
        return null;
    }

    private void closeTransformOverlay() {
        if (currentTransformOverlay != null) {
            mainLayout.removeView(currentTransformOverlay);
            currentTransformOverlay = null;

            if (transformingView != null && transformingViewOriginalParent != null) {
                mainLayout.removeView(transformingView);
                transformingViewOriginalParent.addView(transformingView, transformingViewOriginalIndex);
                homeView.updateViewPosition((HomeItem) transformingView.getTag(), transformingView);
            }
            setOverlayBlur(false, true);
            transformingView = null;
            transformingViewOriginalParent = null;

            if (homeView != null) {
                homeView.cleanupEmptyPages();
                homeView.refreshIcons(model, allApps);
            }
        }
    }

    private void closeFolder() {
        if (currentFolderOverlay != null) {
            mainLayout.removeView(currentFolderOverlay);
            currentFolderOverlay = null;
            setOverlayBlur(false);
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mainLayout.getWindowToken(), 0);
            if (homeView != null) homeView.refreshIcons(model, allApps);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeUtils.updateStatusBarContrast(this);
        if (mainLayout != null) {
            mainLayout.updateDimVisibility();
        }
        if (homeView != null) {
            homeView.refreshLayout();
            homeView.refreshIcons(model, allApps);
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        appWidgetHost.startListening();
        if (allApps == null || allApps.isEmpty()) {
            loadApps();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        appWidgetHost.stopListening();
        if (allApps != null) {
            allApps.clear();
            allApps = null;
        }
        System.gc();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (appInstallReceiver != null) {
            unregisterReceiver(appInstallReceiver);
        }
        model = null;
        mainLayout = null;
        homeView = null;
        drawerView = null;
        appInstallReceiver = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) {
            loadApps();
            if (homeView != null) {
                homeView.refreshLayout();
                homeView.refreshIcons(model, allApps);
            }
            return;
        }
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == REQUEST_PICK_APPWIDGET) {
                configureWidget(data);
            } else if (requestCode == REQUEST_CREATE_APPWIDGET) {
                createWidget(data);
            }
        }
    }

    private void configureWidget(Intent data) {
        int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(appWidgetId);
        if (info == null) return;
        if (info.configure != null) {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(info.configure);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            startActivityForResult(intent, REQUEST_CREATE_APPWIDGET);
        } else {
            createWidget(data);
        }
    }

    private void createWidget(Intent data) {
        int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        HomeItem item = HomeItem.createWidget(appWidgetId, lastGridCol, lastGridRow, 2, 1, homeView.getCurrentPage());
        homeItems.add(item);
        renderHomeItem(item);
        saveHomeState();
    }

    public void pickWidget() {
        if (appWidgetManager == null) return;
        List<AppWidgetProviderInfo> providers = appWidgetManager.getInstalledProviders();
        if (providers == null) return;
        Map<String, List<AppWidgetProviderInfo>> grouped = new HashMap<>();
        for (AppWidgetProviderInfo info : providers) {
            String pkg = info.provider.getPackageName();
            if (!grouped.containsKey(pkg)) grouped.put(pkg, new ArrayList<>());
            grouped.get(pkg).add(info);
        }

        List<String> packages = new ArrayList<>(grouped.keySet());
        Collections.sort(packages, (a, b) -> getAppName(a).compareToIgnoreCase(getAppName(b)));

        final android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            ThemeUtils.applyWindowBlur(dialog.getWindow(), settingsManager.isLiquidGlass());
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackground(ThemeUtils.getGlassDrawable(this, settingsManager, 0));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        root.addView(container);

        int adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true);
        int secondaryColor = (adaptiveColor & 0x00FFFFFF) | 0x80000000;

        LinearLayout titleLayout = new LinearLayout(this);
        titleLayout.setOrientation(LinearLayout.HORIZONTAL);
        titleLayout.setGravity(Gravity.CENTER_VERTICAL);
        titleLayout.setPadding(0, 0, 0, dpToPx(32));

        ImageView titleIcon = new ImageView(this);
        titleIcon.setImageResource(R.drawable.ic_widgets);
        titleIcon.setColorFilter(adaptiveColor);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dpToPx(32), dpToPx(32));
        iconParams.rightMargin = dpToPx(16);
        titleLayout.addView(titleIcon, iconParams);

        TextView title = new TextView(this);
        title.setText(R.string.title_pick_widget);
        title.setTextSize(32);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setTextColor(adaptiveColor);
        titleLayout.addView(title);

        container.addView(titleLayout);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        LinearLayout itemsContainer = new LinearLayout(this);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(itemsContainer);
        container.addView(scrollView);

        for (String pkg : packages) {
            TextView header = new TextView(this);
            header.setText(getAppName(pkg));
            header.setTextSize(12);
            header.setTypeface(null, Typeface.BOLD);
            header.setTextColor(secondaryColor);
            header.setAllCaps(true);
            header.setPadding(0, dpToPx(24), 0, dpToPx(12));
            itemsContainer.addView(header);

            for (AppWidgetProviderInfo info : grouped.get(pkg)) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
                card.setClickable(true);
                card.setFocusable(true);

                GradientDrawable cardBg = new GradientDrawable();
                boolean isNight = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                int cardColor = settingsManager.isLiquidGlass() ? 0x1AFFFFFF : (isNight ? 0x1AFFFFFF : 0x0D000000);
                cardBg.setColor(cardColor);
                cardBg.setCornerRadius(dpToPx(16));
                card.setBackground(cardBg);

                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardLp.bottomMargin = dpToPx(12);
                itemsContainer.addView(card, cardLp);

                ImageView preview = new ImageView(this);
                float sX = info.minWidth / (float) (getResources().getDisplayMetrics().widthPixels / HomeView.GRID_COLUMNS);
                float sY = info.minHeight / (float) (getResources().getDisplayMetrics().heightPixels / HomeView.GRID_ROWS);
                if (!settingsManager.isFreeformHome()) {
                    sX = Math.max(1, (int) Math.ceil(sX));
                    sY = Math.max(1, (int) Math.ceil(sY));
                }
                final float spanX = sX;
                final float spanY = sY;

                preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                widgetPreviewExecutor.execute(() -> {
                    try {
                        Drawable previewDrawable = info.loadPreviewImage(MainActivity.this, 0);
                        if (previewDrawable == null) previewDrawable = info.loadIcon(MainActivity.this, 0);
                        final Drawable finalDrawable = previewDrawable;
                        runOnUiThread(() -> { if (finalDrawable != null) preview.setImageDrawable(finalDrawable); });
                    } catch (Exception e) { e.printStackTrace(); }
                });

                LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dpToPx(64), dpToPx(64));
                previewParams.rightMargin = dpToPx(16);
                card.addView(preview, previewParams);

                LinearLayout textLayout = new LinearLayout(this);
                textLayout.setOrientation(LinearLayout.VERTICAL);

                TextView label = new TextView(this);
                label.setText(info.label);
                label.setTextColor(adaptiveColor);
                label.setTextSize(16);
                label.setTypeface(null, Typeface.BOLD);
                textLayout.addView(label);

                TextView size = new TextView(this);
                size.setText(getString(R.string.widget_size_format, (int) Math.ceil(spanX), (int) Math.ceil(spanY)));
                size.setTextSize(12);
                size.setTextColor(secondaryColor);
                textLayout.addView(size);

                card.addView(textLayout);

                card.setOnClickListener(v -> {
                    dialog.dismiss();
                    int appWidgetId = appWidgetHost.allocateAppWidgetId();
                    boolean allowed = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider);
                    if (allowed) {
                        HomeItem homeItem = HomeItem.createWidget(appWidgetId, lastGridCol, lastGridRow, spanX, spanY, homeView.getCurrentPage());
                        homeItems.add(homeItem);
                        renderHomeItem(homeItem);
                        saveHomeState();
                    } else {
                        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
                        startActivityForResult(intent, REQUEST_PICK_APPWIDGET);
                    }
                });
            }
        }

        ImageView closeBtn = new ImageView(this);
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeBtn.setColorFilter(adaptiveColor);
        closeBtn.setAlpha(0.6f);
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dpToPx(48), dpToPx(48), Gravity.TOP | Gravity.RIGHT);
        closeLp.topMargin = dpToPx(16);
        closeLp.rightMargin = dpToPx(16);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        root.addView(closeBtn, closeLp);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = 0, bottom = 0;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.graphics.Insets systemInsets = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                top = systemInsets.top;
                bottom = systemInsets.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            container.setPadding(dpToPx(24), top + dpToPx(64), dpToPx(24), bottom + dpToPx(24));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) closeBtn.getLayoutParams();
            lp.topMargin = top + dpToPx(16);
            closeBtn.setLayoutParams(lp);
            return insets;
        });

        dialog.setContentView(root);
        dialog.show();
    }

    private String getAppName(String packageName) {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0)).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    private void applyThemeMode(String mode) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
            int nightMode = UiModeManager.MODE_NIGHT_AUTO;
            if ("light".equals(mode)) nightMode = UiModeManager.MODE_NIGHT_NO;
            else if ("dark".equals(mode)) nightMode = UiModeManager.MODE_NIGHT_YES;

            if (uiModeManager.getNightMode() != nightMode) {
                uiModeManager.setApplicationNightMode(nightMode);
            }
        }
    }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private class AppInstallReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            android.net.Uri data = intent.getData();
            if (data == null) return;
            String packageName = data.getSchemeSpecificPart();

            if (model != null) {
                model.invalidateAppListCache();
                if (Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
                    model.clearAppIconCache(packageName);
                }
            }

            if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                boolean isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                if (!isReplacing) {
                    removePackageItems(packageName);
                }
            }
            loadApps();
        }
    }

    private class MainLayout extends FrameLayout {
        private View dimView;
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
                if (isDragging || isTransforming() || isDrawerOpen || currentFolderOverlay != null) return;
                longPressTriggered = true;
                if (touchedView != null) {
                    Object tag = touchedView.getTag();
                    if (tag instanceof HomeItem) {
                        HomeItem item = (HomeItem) tag;
                        if (settingsManager.isFreeformHome() || item.type == HomeItem.Type.WIDGET) {
                            showTransformOverlay(touchedView);
                            if (currentTransformOverlay != null) {
                                currentTransformOverlay.startDirectMove(startX, startY);
                            }
                            return;
                        }
                        isDragging = true;
                        isExternalDrag = false;
                        origCol = item.col;
                        origRow = item.row;
                        origPage = item.page;

                        if (dragOverlay != null) {
                            boolean isApp = item.type == HomeItem.Type.APP;
                            if (ivAppInfo != null) ivAppInfo.setVisibility(isApp ? View.VISIBLE : View.GONE);
                            dragOverlay.setVisibility(View.VISIBLE);
                            dragOverlay.bringToFront();
                        }
                        if (homeView != null) homeView.startDragging(touchedView, startX, startY);
                    }
                } else {
                    int cellWidth = getWidth() / HomeView.GRID_COLUMNS;
                    int cellHeight = getHeight() / HomeView.GRID_ROWS;
                    float col = startX / (cellWidth > 0 ? (float) cellWidth : 1.0f);
                    float row = startY / (cellHeight > 0 ? (float) cellHeight : 1.0f);
                    showHomeContextMenu(col, row, homeView.getCurrentPage());
                }
            }
        };

        public MainLayout(Context context) {
            super(context);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            setupDimView();
            setupDragOverlay();
        }

        private void setupDimView() {
            dimView = new View(getContext());
            dimView.setBackgroundColor(Color.BLACK);
            dimView.setAlpha(0.3f);
            dimView.setVisibility(View.GONE);
            addView(dimView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }

        public void updateDimVisibility() {
            boolean isNight = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            if (isNight && settingsManager.isDarkenWallpaper()) {
                dimView.setVisibility(View.VISIBLE);
            } else {
                dimView.setVisibility(View.GONE);
            }
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        private void setupDragOverlay() {
            dragOverlay = new LinearLayout(getContext());
            dragOverlay.setOrientation(LinearLayout.HORIZONTAL);
            dragOverlay.setBackground(ThemeUtils.getGlassDrawable(getContext(), settingsManager, 12));
            dragOverlay.setGravity(Gravity.CENTER);
            dragOverlay.setVisibility(View.GONE);
            dragOverlay.setElevation(dpToPx(8));

            int adaptiveColor = ThemeUtils.getAdaptiveColor(getContext(), settingsManager, true);

            ivRemove = new ImageView(getContext());
            ivRemove.setImageResource(R.drawable.ic_remove);
            ivRemove.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16));
            ivRemove.setColorFilter(adaptiveColor);
            ivRemove.setContentDescription(getContext().getString(R.string.drag_remove));
            dragOverlay.addView(ivRemove);

            ivAppInfo = new ImageView(getContext());
            ivAppInfo.setImageResource(R.drawable.ic_info);
            ivAppInfo.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16));
            ivAppInfo.setColorFilter(adaptiveColor);
            ivAppInfo.setContentDescription(getContext().getString(R.string.drag_app_info));
            dragOverlay.addView(ivAppInfo);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            lp.topMargin = dpToPx(48);
            addView(dragOverlay, lp);
        }

        private void resetDragState() {
            isDragging = false;
            longPressHandler.removeCallbacks(longPressRunnable);
            if (dragOverlay != null) {
                dragOverlay.setVisibility(View.GONE);
                ivRemove.setBackgroundColor(Color.TRANSPARENT);
                ivAppInfo.setBackgroundColor(Color.TRANSPARENT);
            }
            if (homeView != null) homeView.cancelDragging();
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
            if ((ev.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
                startX = ev.getX();
                startY = ev.getY();
                downTime = System.currentTimeMillis();
            }
            if (currentTransformOverlay != null) return false;
            if (currentFolderOverlay != null) {
                longPressHandler.removeCallbacks(longPressRunnable);
                return false;
            }
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
                            if (drawerView.isAtTop() || dy > touchSlop * 4) {
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
                    resetDragState();
                    touchedView = findTouchedHomeItem(startX, startY);
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
            if (currentTransformOverlay != null) {
                return currentTransformOverlay.onTouchEvent(event);
            }
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
                    if (isDragging && settingsManager.isFreeformHome()) {
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
                        homeView.handleDrag(event.getX(), event.getY());
                        updateDragHighlight(event.getX(), event.getY());
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
                            if (dx > touchSlop * 2) {
                                homeView.scrollToPage(homeView.getCurrentPage() - 1);
                                isGestureCanceled = true;
                            } else if (dx < -touchSlop * 2) {
                                homeView.scrollToPage(homeView.getCurrentPage() + 1);
                                isGestureCanceled = true;
                            }
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging) {
                        if (dragOverlay != null) {
                            int overlayHeight = dragOverlay.getHeight();
                            int overlayWidth = dragOverlay.getWidth();
                            float left = (getWidth() - overlayWidth) / 2f;

                            if (touchedView != null &&
                                event.getY() < dragOverlay.getBottom() + touchSlop * 2 &&
                                event.getX() >= left && event.getX() <= left + overlayWidth) {
                                Object tag = touchedView.getTag();
                                if (tag instanceof HomeItem) {
                                    HomeItem item = (HomeItem) tag;
                                    boolean isApp = ivAppInfo != null && ivAppInfo.getVisibility() == View.VISIBLE;
                                    if (!isApp) {
                                        removeHomeItem(item, touchedView);
                                    } else {
                                        float x = event.getX();
                                        if (x < left + overlayWidth / 2f) {
                                            removeHomeItem(item, touchedView);
                                        } else {
                                            showAppInfo(item);
                                            revertPosition(item, touchedView);
                                        }
                                    }
                                }
                                resetDragState();
                            } else {
                                if (homeView != null) homeView.endDragging();
                                resetDragState();
                            }
                        } else {
                            if (homeView != null) homeView.endDragging();
                            resetDragState();
                        }
                        return true;
                    }
                    resetDragState();
                    if (!isGestureCanceled && !longPressTriggered) {
                        long duration = System.currentTimeMillis() - downTime;
                        float finalDx = event.getX() - startX;
                        float finalDy = event.getY() - startY;
                        float dist = (float) Math.sqrt(finalDx * finalDx + finalDy * finalDy);
                        if (duration >= 50 && duration < 300 && dist < touchSlop) {
                            if (touchedView != null) handleItemClick(touchedView);
                            else performClick();
                        }
                    }
                    return true;
            }
            return true;
        }

        private View findTouchedHomeItem(float x, float y) {
            int page = homeView.getCurrentPage();
            ViewGroup pagesContainer = (ViewGroup) homeView.getChildAt(0);
            if (pagesContainer != null && page < pagesContainer.getChildCount()) {
                ViewGroup pageLayout = (ViewGroup) pagesContainer.getChildAt(page);

                int[] pagePos = new int[2];
                pageLayout.getLocationOnScreen(pagePos);
                int[] rootPos = new int[2];
                this.getLocationOnScreen(rootPos);

                float adjustedX = x - (pagePos[0] - rootPos[0]);
                float adjustedY = y - (pagePos[1] - rootPos[1]);

                for (int i = pageLayout.getChildCount() - 1; i >= 0; i--) {
                    View child = pageLayout.getChildAt(i);
                    RectF visualRect = homeView.getVisualRect(child);
                    if (visualRect.contains(adjustedX, adjustedY)) {
                        return child;
                    }
                }
            }
            return null;
        }

        private void handleItemClick(View v) {
            if (v == null) return;
            HomeItem item = (HomeItem) v.getTag();
            if (item == null || item.type == null) return;
            if (item.type == HomeItem.Type.APP) {
                if (item.packageName == null) return;
                try {
                    Intent intent = getPackageManager().getLaunchIntentForPackage(item.packageName);
                    if (intent != null) {
                        startActivity(intent);
                    } else {
                        Toast.makeText(MainActivity.this, R.string.app_info_failed, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, R.string.app_info_failed, Toast.LENGTH_SHORT).show();
                }
            } else if (item.type == HomeItem.Type.FOLDER) {
                openFolder(item, v);
            } else if (item.type == HomeItem.Type.WIDGET) {
                showWidgetOptions(item, v);
            }
        }

        public void openDrawer() {
            if (isDrawerOpen) return;
            isDrawerOpen = true;
            settingsManager.incrementDrawerOpenCount();
            drawerView.setVisibility(View.VISIBLE);
            drawerView.setAlpha(0f);
            drawerView.setTranslationY(getHeight() / 4f);
            drawerView.animate()
                .translationY(0)
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

            float targetAlpha = settingsManager.isLiquidGlass() ? 0.4f : 0f;
            homeView.animate().alpha(targetAlpha).setDuration(250).start();
            setOverlayBlur(true);
            drawerView.onOpen();
        }

        public void startExternalDrag(View v) {
            isDragging = true;
            isExternalDrag = true;
            if (dragOverlay != null) {
                HomeItem item = (HomeItem) v.getTag();
                boolean isApp = item != null && item.type == HomeItem.Type.APP;
                ivAppInfo.setVisibility(isApp ? View.VISIBLE : View.GONE);
                dragOverlay.setVisibility(View.VISIBLE);
                dragOverlay.bringToFront();
            }
            touchedView = v;

            int iconSize = getResources().getDimensionPixelSize(R.dimen.grid_icon_size);
            v.setX(startX - iconSize);
            v.setY(startY - iconSize - dpToPx(24));

            homeView.startDragging(v, startX, startY);
        }

        private void updateDragHighlight(float x, float y) {
            if (dragOverlay == null || dragOverlay.getVisibility() != View.VISIBLE) return;
            dragOverlay.bringToFront();
            if (ivRemove == null || ivAppInfo == null) return;

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
            if (item == null || v == null) return;
            if (isExternalDrag) {
                removeHomeItem(item, v);
            } else {
                item.col = origCol;
                item.row = origRow;
                item.page = origPage;
                homeView.addItemView(item, v);
                v.setRotation(item.rotation);
                v.setScaleX(item.scaleX);
                v.setScaleY(item.scaleY);
                v.setRotationX(item.tiltX);
                v.setRotationY(item.tiltY);
                homeView.updateViewPosition(item, v);
                saveHomeState();
            }
        }

        public void closeDrawer() {
            if (!isDrawerOpen) return;
            isDrawerOpen = false;
            drawerView.animate()
                .translationY(getHeight() / 4f)
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    drawerView.setVisibility(View.GONE);
                    homeView.setVisibility(View.VISIBLE);
                    drawerView.onClose();
                    System.gc();
                })
                .start();
            homeView.setVisibility(View.VISIBLE);
            homeView.animate().alpha(1).setDuration(200).start();
            setOverlayBlur(false);
            InputMethodManager imm = (InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }
}
