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

    private LauncherModel model;
    private SettingsManager settingsManager;
    private AppWidgetHost appWidgetHost;
    private AppWidgetManager appWidgetManager;
    private MainLayout mainLayout;
    private HomeView homeView;
    private DrawerView drawerView;
    private AppInstallReceiver appInstallReceiver;
    private List<HomeItem> homeItems = new ArrayList<>();
    private List<AppItem> allApps = new ArrayList<>();
    private float lastGridCol, lastGridRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(this);
        applyThemeMode(settingsManager.getThemeMode());

        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);
        }

        model = new LauncherModel(this);

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

        setContentView(mainLayout);

        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new AppWidgetHost(this, APPWIDGET_HOST_ID);
        appWidgetHost.startListening();

        applyDynamicColors();
        loadApps();
        registerAppInstallReceiver();

        homeView.post(this::restoreHomeState);
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

    private void renderHomeItem(HomeItem item) {
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
        }
        if (view != null) {
            homeView.addItemView(item, view);
        }
    }

    private View createAppView(HomeItem item) {
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
        return container;
    }

    private View createWidgetView(HomeItem item) {
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(item.widgetId);
        if (info == null) return null;
        AppWidgetHostView hostView = appWidgetHost.createView(this, item.widgetId, info);
        hostView.setAppWidget(item.widgetId, info);
        return hostView;
    }

    private void showWidgetOptions(HomeItem item, View hostView) {
        String[] options = {"Resize", "Remove"};
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
            .setTitle("Resize Widget")
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

    private void removeHomeItem(HomeItem item, View view) {
        homeItems.remove(item);
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        saveHomeState();
    }

    private void uninstallApp(HomeItem item, View view) {
        if (item == null || item.type != HomeItem.Type.APP) {
            removeHomeItem(item, view);
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(android.net.Uri.fromParts("package", item.packageName, null));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            // Remove from home screen as requested by dragging to uninstall zone
            removeHomeItem(item, view);
        } catch (Exception e) {
            Toast.makeText(this, "Uninstall failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

    private void showHomeContextMenu(float col, float row, int page) {
        String[] options = {"Widgets", "Wallpaper", "Launcher Settings", "Layout Options"};
        int[] icons = {R.drawable.ic_widgets, R.drawable.ic_wallpaper, R.drawable.ic_settings, R.drawable.ic_layout};

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.select_dialog_item, android.R.id.text1, options) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = view.findViewById(android.R.id.text1);
                tv.setCompoundDrawablesWithIntrinsicBounds(icons[position], 0, 0, 0);
                tv.setCompoundDrawablePadding(dpToPx(16));
                tv.setTextColor(getColor(R.color.foreground));
                Drawable d = tv.getCompoundDrawables()[0];
                if (d != null) d.setTint(getColor(R.color.foreground));
                return view;
            }
        };

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Home Menu")
            .setAdapter(adapter, (d, which) -> {
                switch (which) {
                    case 0:
                        lastGridCol = col;
                        lastGridRow = row;
                        pickWidget();
                        break;
                    case 1: openWallpaperPicker(); break;
                    case 2: openSettings(); break;
                    case 3: showLayoutOptions(); break;
                }
            }).create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(R.drawable.glass_bg);
    }

    private void pickAppForHome(int col, int row, int page) {
        if (allApps.isEmpty()) {
            Toast.makeText(this, "Apps not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[allApps.size()];
        for (int i = 0; i < allApps.size(); i++) labels[i] = allApps.get(i).label;

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Pick App")
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
                // Fallback to gallery/internal chooser
                Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT);
                pickIntent.setType("image/*");
                startActivity(Intent.createChooser(pickIntent, "Select Wallpaper"));
            } catch (Exception e2) {
                Toast.makeText(this, "Wallpaper picker not available", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, 100);
    }

    private void showLayoutOptions() {
        String[] options = {"Add Page", "Remove Last Page"};
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Layout Options")
            .setItems(options, (d, which) -> {
                if (which == 0) {
                    homeView.addPage();
                    Toast.makeText(this, "Page added", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Remove page not implemented yet", Toast.LENGTH_SHORT).show();
                }
            }).create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(R.drawable.glass_bg);
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
    protected void onResume() {
        super.onResume();
        if (homeView != null) homeView.refreshLayout();
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
        if (model != null) model.shutdown();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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
        List<AppWidgetProviderInfo> providers = appWidgetManager.getInstalledProviders();
        Map<String, List<AppWidgetProviderInfo>> grouped = new HashMap<>();
        for (AppWidgetProviderInfo info : providers) {
            String pkg = info.provider.getPackageName();
            if (!grouped.containsKey(pkg)) grouped.put(pkg, new ArrayList<>());
            grouped.get(pkg).add(info);
        }

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        scrollView.addView(root);

        List<String> packages = new ArrayList<>(grouped.keySet());
        Collections.sort(packages, (a, b) -> {
            String labelA = getAppName(a);
            String labelB = getAppName(b);
            return labelA.compareToIgnoreCase(labelB);
        });

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🧩 Pick Widget")
                .setView(scrollView)
                .setNegativeButton("Cancel", null)
                .create();

        for (String pkg : packages) {
            TextView header = new TextView(this);
            header.setText(getAppName(pkg));
            header.setTextSize(18);
            header.setTypeface(null, Typeface.BOLD);
            header.setTextColor(getColor(R.color.foreground));
            header.setPadding(0, dpToPx(16), 0, dpToPx(8));
            root.addView(header);

            for (AppWidgetProviderInfo info : grouped.get(pkg)) {
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
                item.setClickable(true);
                item.setBackgroundResource(android.R.drawable.list_selector_background);

                // Visual preview
                ImageView preview = new ImageView(this);
                int spanX = Math.max(1, info.minWidth / (getResources().getDisplayMetrics().widthPixels / HomeView.GRID_COLUMNS));
                int spanY = Math.max(1, info.minHeight / (getResources().getDisplayMetrics().heightPixels / HomeView.GRID_ROWS));

                Drawable previewDrawable = info.loadPreviewImage(this, 0);
                if (previewDrawable == null) {
                    previewDrawable = info.loadIcon(this, 0);
                }
                preview.setImageDrawable(previewDrawable);
                preview.setScaleType(ImageView.ScaleType.FIT_CENTER);

                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                shape.setColor(getColor(R.color.search_background));
                shape.setCornerRadius(dpToPx(4));
                shape.setStroke(dpToPx(1), getColor(R.color.foreground_dim));
                preview.setBackground(shape);

                LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dpToPx(60), dpToPx(60));
                previewParams.rightMargin = dpToPx(12);
                item.addView(preview, previewParams);

                LinearLayout textLayout = new LinearLayout(this);
                textLayout.setOrientation(LinearLayout.VERTICAL);

                TextView label = new TextView(this);
                label.setText(info.label);
                label.setTextColor(getColor(R.color.foreground));
                textLayout.addView(label);

                TextView size = new TextView(this);
                size.setText(spanX + "x" + spanY);
                size.setTextSize(12);
                size.setTextColor(getColor(R.color.foreground_dim));
                textLayout.addView(size);

                item.addView(textLayout);

                item.setOnClickListener(v -> {
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

                root.addView(item);
            }
        }

        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(R.drawable.glass_bg);
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
            loadApps();
        }
    }

    private class MainLayout extends FrameLayout {
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
        private TextView tvRemove, tvUninstall;

        private final Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                longPressTriggered = true;
                if (touchedView != null) {
                    isDragging = true;
                    if (dragOverlay != null) dragOverlay.setVisibility(View.VISIBLE);
                    homeView.startDragging(touchedView, startX, startY);
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
            setupDragOverlay();
        }

        private void setupDragOverlay() {
            dragOverlay = new LinearLayout(getContext());
            dragOverlay.setOrientation(LinearLayout.HORIZONTAL);
            dragOverlay.setBackgroundResource(R.drawable.glass_bg);
            dragOverlay.setGravity(Gravity.CENTER);
            dragOverlay.setVisibility(View.GONE);

            tvRemove = new TextView(getContext());
            tvRemove.setText("REMOVE");
            tvRemove.setTextColor(Color.WHITE);
            tvRemove.setPadding(dpToPx(32), dpToPx(16), dpToPx(32), dpToPx(16));
            dragOverlay.addView(tvRemove);

            tvUninstall = new TextView(getContext());
            tvUninstall.setText("UNINSTALL");
            tvUninstall.setTextColor(Color.WHITE);
            tvUninstall.setPadding(dpToPx(32), dpToPx(16), dpToPx(32), dpToPx(16));
            dragOverlay.addView(tvUninstall);

            addView(dragOverlay, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP));
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
                        // Swipe down to close (more sensitive if at top)
                        if (dy > touchSlop && dy > Math.abs(dx)) {
                            if (drawerView.isAtTop() || dy > touchSlop * 4) {
                                return true;
                            }
                        }
                        break;
                }
                return false;
            }

            switch (ev.getAction()) {
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
                    // Explicitly intercept upward swipe for drawer
                    if (dy < -touchSlop && Math.abs(dy) > Math.abs(dx)) {
                        longPressHandler.removeCallbacks(longPressRunnable);
                        return true;
                    }
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        longPressHandler.removeCallbacks(longPressRunnable);
                        if (!longPressTriggered) {
                            return true; // Intercept for other swipes
                        }
                    }
                    return isDragging;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging) return true;
                    long duration = System.currentTimeMillis() - downTime;
                    if (duration < 80) { // Accidental touch/debounce
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
                // Handle swipe down to close drawer
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

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Should not happen as we return false in intercept,
                    // unless no child handles it.
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
                            if (dx > touchSlop * 2 && homeView.getCurrentPage() > 0) {
                                homeView.scrollToPage(homeView.getCurrentPage() - 1);
                                isGestureCanceled = true;
                            } else if (dx < -touchSlop * 2 && homeView.getCurrentPage() < homeView.getPageCount() - 1) {
                                homeView.scrollToPage(homeView.getCurrentPage() + 1);
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
                            dragOverlay.setVisibility(View.GONE);
                            tvRemove.setBackgroundColor(Color.TRANSPARENT);
                            tvUninstall.setBackgroundColor(Color.TRANSPARENT);

                            // Hitbox: Top of the screen including a bit of extra margin (touchSlop)
                            if (event.getY() < overlayHeight + touchSlop * 2) {
                                HomeItem item = (HomeItem) touchedView.getTag();
                                if (item != null) {
                                    if (event.getX() < overlayWidth / 2f) {
                                        removeHomeItem(item, touchedView);
                                    } else {
                                        uninstallApp(item, touchedView);
                                    }
                                }
                                homeView.cancelDragging();
                            } else {
                                homeView.endDragging();
                            }
                        } else {
                            homeView.endDragging();
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
                            if (touchedView != null) handleItemClick(touchedView);
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

        private void handleItemClick(View v) {
            HomeItem item = (HomeItem) v.getTag();
            if (item == null) return;
            if (item.type == HomeItem.Type.APP) {
                Intent intent = getPackageManager().getLaunchIntentForPackage(item.packageName);
                if (intent != null) startActivity(intent);
            } else if (item.type == HomeItem.Type.WIDGET) {
                showWidgetOptions(item, v);
            }
        }

        public void openDrawer() {
            if (isDrawerOpen) return;
            isDrawerOpen = true;
            drawerView.setVisibility(View.VISIBLE);
            drawerView.setAlpha(0f);
            drawerView.setTranslationY(getHeight() / 4f);
            drawerView.animate()
                .translationY(0)
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
            homeView.animate().alpha(0).setDuration(250).start();
            drawerView.onOpen();
        }

        public void startExternalDrag(View v) {
            isDragging = true;
            if (dragOverlay != null) dragOverlay.setVisibility(View.VISIBLE);
            touchedView = v;

            int iconSize = getResources().getDimensionPixelSize(R.dimen.grid_icon_size);
            v.setX(startX - iconSize / 2f);
            v.setY(startY - iconSize / 2f - dpToPx(48));

            homeView.startDragging(v, startX, startY);
        }

        private void updateDragHighlight(float x, float y) {
            if (dragOverlay == null || dragOverlay.getVisibility() != View.VISIBLE) return;

            int overlayHeight = dragOverlay.getHeight();
            int overlayWidth = dragOverlay.getWidth();

            if (y < overlayHeight + touchSlop * 2) {
                if (x < overlayWidth / 2f) {
                    tvRemove.setBackgroundColor(0x40FFFFFF);
                    tvUninstall.setBackgroundColor(Color.TRANSPARENT);
                } else {
                    tvRemove.setBackgroundColor(Color.TRANSPARENT);
                    tvUninstall.setBackgroundColor(0x40FF0000);
                }
            } else {
                tvRemove.setBackgroundColor(Color.TRANSPARENT);
                tvUninstall.setBackgroundColor(Color.TRANSPARENT);
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
                })
                .start();
            homeView.setVisibility(View.VISIBLE);
            homeView.animate().alpha(1).setDuration(200).start();
            InputMethodManager imm = (InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }
}
