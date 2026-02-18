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
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
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

public class MainActivity extends Activity {

    private static final int REQUEST_PICK_APPWIDGET = 1;
    private static final int REQUEST_CREATE_APPWIDGET = 2;
    private static final int APPWIDGET_HOST_ID = 1024;

    public LauncherModel model;
    public SettingsManager settingsManager;
    private AutoDimmingBackground autoDimmingBackground;
    public FolderManager folderManager;
    private FolderUI folderUI;
    private FreeformInteraction freeformInteraction;
    private WidgetManager widgetManager;
    private AppWidgetHost appWidgetHost;
    private AppWidgetManager appWidgetManager;
    public MainLayout mainLayout;
    public HomeView homeView;
    private DrawerView drawerView;
    private AppInstallReceiver appInstallReceiver;
    public List<HomeItem> homeItems = new ArrayList<>();
    public List<AppItem> allApps = new ArrayList<>();
    private float lastGridCol, lastGridRow;

    @Override
    protected void attachBaseContext(Context newBase) {
        SettingsManager sm = new SettingsManager(newBase);
        super.attachBaseContext(ThemeMechanism.applyThemeToContext(newBase, sm.getThemeMode()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(this);
        ThemeMechanism.applyThemeMode(this, settingsManager.getThemeMode());
        ThemeUtils.updateStatusBarContrast(this);

        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
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
            View view = createAppView(item);
            homeView.addItemView(item, view);
            saveHomeState();
            mainLayout.startExternalDrag(view);
        });

        mainLayout.addView(homeView);
        mainLayout.addView(drawerView);
        drawerView.setVisibility(View.GONE);

        freeformInteraction = new FreeformInteraction(this, mainLayout, settingsManager, new FreeformInteraction.InteractionCallback() {
            @Override public void onSaveState() { saveHomeState(); }
            @Override public void onRemoveItem(HomeItem item, View view) { removeHomeItem(item, view); }
            @Override public void onShowAppInfo(HomeItem item) { showAppInfo(item); }
        });

        setContentView(mainLayout);

        autoDimmingBackground = new AutoDimmingBackground(this, mainLayout, settingsManager);

        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new AppWidgetHost(this, APPWIDGET_HOST_ID);
        appWidgetHost.startListening();

        applyDynamicColors();
        folderManager = new FolderManager(this, settingsManager);
        folderUI = new FolderUI(this, settingsManager);
        widgetManager = new WidgetManager(this, settingsManager, AppWidgetManager.getInstance(this), new AppWidgetHost(this, APPWIDGET_HOST_ID));
        loadApps();
        registerAppInstallReceiver();

        homeView.post(() -> {
            restoreHomeState();
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
        prompt.setBackgroundResource(R.drawable.glass_bg);
        prompt.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
        prompt.setGravity(Gravity.CENTER);
        prompt.setElevation(dpToPx(8));

        TextView title = new TextView(this);
        title.setText(R.string.prompt_default_launcher_title);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(getColor(R.color.foreground));
        prompt.addView(title);

        TextView message = new TextView(this);
        message.setText(R.string.prompt_default_launcher_message);
        message.setPadding(0, dpToPx(8), 0, dpToPx(16));
        message.setGravity(Gravity.CENTER);
        message.setTextColor(getColor(R.color.foreground_dim));
        prompt.addView(message);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);

        TextView btnLater = new TextView(this);
        btnLater.setText(R.string.action_later);
        btnLater.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        btnLater.setTextColor(getColor(R.color.foreground));
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
        settingsManager.saveHomeItems(homeItems);
    }

    private void restoreHomeState() {
        homeItems = settingsManager.getHomeItems();
        if (homeItems.isEmpty()) {
            setupDefaultHome();
        } else {
            for (HomeItem item : homeItems) {
                renderHomeItem(item);
            }
        }
    }

    private void setupDefaultHome() {
        saveHomeState();
    }

    public void renderHomeItem(HomeItem item) {
        if (item == null) return;
        View view = null;
        switch (item.type) {
            case APP:
                view = createAppView(item);
                break;
            case WIDGET:
                view = createWidgetView(item);
                break;
            case CLOCK:
                view = createClockView(item);
                break;
            case FOLDER:
                view = folderUI.createFolderView(item, true, homeView.getWidth() / HomeView.GRID_COLUMNS, homeView.getHeight() / HomeView.GRID_ROWS);
                break;
        }
        if (view != null) {
            homeView.addItemView(item, view);
            if (item.type == HomeItem.Type.FOLDER) {
                GridLayout grid = findGridLayout((ViewGroup) view);
                if (grid != null) refreshFolderPreview(item, grid);
            }
        }
    }

    public void refreshFolderPreview(HomeItem folder, GridLayout grid) {
        grid.removeAllViews();
        if (folder.folderItems == null) return;
        int count = Math.min(folder.folderItems.size(), 4);
        for (int i = 0; i < count; i++) {
            HomeItem sub = folder.folderItems.get(i);
            ImageView iv = new ImageView(this);
            int size = dpToPx(20);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = size;
            lp.height = size;
            iv.setLayoutParams(lp);
            model.loadIcon(AppItem.fromPackage(this, sub.packageName), iv::setImageBitmap);
            grid.addView(iv);
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

    public View createAppView(HomeItem item) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);

        ImageView iconView = new ImageView(this);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int baseSize = getResources().getDimensionPixelSize(R.dimen.grid_icon_size);
        float scale = settingsManager.getIconScale();
        int size = (int) (baseSize * scale);

        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(size, size);
        iconView.setLayoutParams(iconParams);

        TextView labelView = new TextView(this);
        labelView.setTextColor(getColor(R.color.foreground));
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

    private View createWidgetView(HomeItem item) {
        if (appWidgetManager == null || item == null) return null;
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(item.widgetId);
        if (info == null) return null;
        try {
            AppWidgetHostView hostView = appWidgetHost.createView(this, item.widgetId, info);
            hostView.setAppWidget(item.widgetId, info);
            return hostView;
        } catch (Exception e) {
            return null;
        }
    }

    public void showWidgetOptions(HomeItem item, View hostView) {
        String[] options = {getString(R.string.action_resize), getString(R.string.action_remove)};
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setItems(options, (d, which) -> {
                if (which == 0) showResizeDialog(item, hostView);
                else removeHomeItem(item, hostView);
            }).create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(R.drawable.glass_bg);
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
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(R.drawable.glass_bg);
    }

    public void removeHomeItem(HomeItem item, View view) {
        homeItems.remove(item);
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        saveHomeState();
        if (homeView != null) {
            homeView.refreshIcons(model, allApps);
        }
    }

    public void showAppInfo(HomeItem item) {
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
        tvTime.setTextColor(getColor(R.color.foreground));
        tvTime.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));

        TextView tvDate = new TextView(this);
        tvDate.setTextSize(18);
        tvDate.setTextColor(getColor(R.color.foreground_dim));
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

    public void showHomeContextMenu(float col, float row, int page) {
        lastGridCol = col;
        lastGridRow = row;

        List<String> options = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();

        options.add(getString(R.string.menu_widgets));
        icons.add(R.drawable.ic_widgets);

        options.add(getString(R.string.menu_wallpaper));
        icons.add(R.drawable.ic_wallpaper);

        options.add(getString(R.string.menu_settings));
        icons.add(R.drawable.ic_settings);

        options.add(getString(R.string.layout_add_page));
        icons.add(R.drawable.ic_layout);

        if (homeView.getPageCount() > 1) {
            options.add(getString(R.string.layout_remove_page));
            icons.add(R.drawable.ic_remove);
        }

        int adaptiveColor = ThemeUtils.getAdaptiveColor(this, settingsManager, true);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, android.R.id.text1, options) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = view.findViewById(android.R.id.text1);
                tv.setCompoundDrawablesWithIntrinsicBounds(icons.get(position), 0, 0, 0);
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
                String selected = options.get(which);
                if (selected.equals(getString(R.string.menu_widgets))) {
                    pickWidget();
                } else if (selected.equals(getString(R.string.menu_wallpaper))) {
                    openWallpaperPicker();
                } else if (selected.equals(getString(R.string.menu_settings))) {
                    openSettings();
                } else if (selected.equals(getString(R.string.layout_add_page))) {
                    homeView.onAddPage();
                } else if (selected.equals(getString(R.string.layout_remove_page))) {
                    homeView.onRemovePage();
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
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(R.drawable.glass_bg);
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
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, 100);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (autoDimmingBackground != null) {
            autoDimmingBackground.updateDimVisibility();
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
    }

    @Override
    protected void onStop() {
        super.onStop();
        appWidgetHost.stopListening();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (appInstallReceiver != null) unregisterReceiver(appInstallReceiver);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) {
            loadApps();
            if (homeView != null) homeView.refreshLayout();
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
        if (widgetManager != null) {
            widgetManager.pickWidget(lastGridCol, lastGridRow);
        }
    }

    public void startNewWidgetDrag(AppWidgetProviderInfo info, int spanX, int spanY) {
        int appWidgetId = appWidgetHost.allocateAppWidgetId();
        boolean allowed = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider);
        if (allowed) {
            HomeItem item = HomeItem.createWidget(appWidgetId, 0, 0, spanX, spanY, homeView.getCurrentPage());
            homeItems.add(item);
            View view = createWidgetView(item);
            if (view != null) {
                homeView.addItemView(item, view);
                saveHomeState();
                mainLayout.startExternalDrag(view);
            }
        } else {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            startActivityForResult(intent, REQUEST_PICK_APPWIDGET);
        }
    }

    public String getAppName(String packageName) {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0)).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    public DrawerView getDrawerView() { return drawerView; }
    public FreeformInteraction getFreeformInteraction() { return freeformInteraction; }
    public void handleItemClick(View v) { mainLayout.handleItemClick(v); }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private class AppInstallReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadApps();
        }
    }
}
