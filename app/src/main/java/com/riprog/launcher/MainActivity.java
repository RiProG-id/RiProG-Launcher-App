package com.riprog.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

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
    private int lastGridCol, lastGridRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);
        }

        model = new LauncherModel(this);
        settingsManager = new SettingsManager(this);

        mainLayout = new MainLayout(this);
        homeView = new HomeView(this);
        drawerView = new DrawerView(this);
        drawerView.setColumns(settingsManager.getColumns());

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
        // Clock centered horizontally at the top
        HomeItem clockItem = HomeItem.createClock(0, 0, 4, 2, 0);
        homeItems.add(clockItem);
        renderHomeItem(clockItem);
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
        ImageView iconView = new ImageView(this);
        int size = dpToPx(56);
        iconView.setLayoutParams(new FrameLayout.LayoutParams(size, size));

        AppItem app = findApp(item.packageName);
        if (app != null) {
            model.loadIcon(app, iconView::setImageBitmap);
        } else {
            iconView.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        return iconView;
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
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setItems(options, (dialog, which) -> {
                if (which == 0) showResizeDialog(item, hostView);
                else removeHomeItem(item, hostView);
            }).show();
    }

    private void showResizeDialog(HomeItem item, View hostView) {
        String[] sizes = {"1x1", "2x1", "2x2", "4x2", "4x1"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Resize Widget")
            .setItems(sizes, (dialog, which) -> {
                switch (which) {
                    case 0: item.spanX = 1; item.spanY = 1; break;
                    case 1: item.spanX = 2; item.spanY = 1; break;
                    case 2: item.spanX = 2; item.spanY = 2; break;
                    case 3: item.spanX = 4; item.spanY = 2; break;
                    case 4: item.spanX = 4; item.spanY = 1; break;
                }
                homeView.updateViewPosition(item, hostView);
                saveHomeState();
            }).show();
    }

    private void removeHomeItem(HomeItem item, View view) {
        homeItems.remove(item);
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        saveHomeState();
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

    private void showHomeContextMenu(int col, int row, int page) {
        String[] options = {"Add App", "Widgets", "Wallpaper", "Launcher Settings", "Layout Options"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Home Menu")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: pickAppForHome(col, row, page); break;
                    case 1:
                        lastGridCol = col;
                        lastGridRow = row;
                        pickWidget();
                        break;
                    case 2: openWallpaperPicker(); break;
                    case 3: openSettings(); break;
                    case 4: showLayoutOptions(); break;
                }
            }).show();
    }

    private void pickAppForHome(int col, int row, int page) {
        if (allApps.isEmpty()) {
            Toast.makeText(this, "Apps not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[allApps.size()];
        for (int i = 0; i < allApps.size(); i++) labels[i] = allApps.get(i).label;

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Pick App")
            .setItems(labels, (dialog, which) -> {
                AppItem selected = allApps.get(which);
                HomeItem item = HomeItem.createApp(selected.packageName, selected.className, col, row, page);
                homeItems.add(item);
                renderHomeItem(item);
                saveHomeState();
            }).show();
    }

    private void openWallpaperPicker() {
        Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
        startActivity(Intent.createChooser(intent, "Select Wallpaper"));
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, 100);
    }

    private void showLayoutOptions() {
        String[] options = {"Add Page", "Remove Last Page"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Layout Options")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    homeView.addPage();
                    Toast.makeText(this, "Page added", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Remove page not implemented yet", Toast.LENGTH_SHORT).show();
                }
            }).show();
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
        int appWidgetId = appWidgetHost.allocateAppWidgetId();
        Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET);
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

        private final Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                longPressTriggered = true;
                if (touchedView != null) {
                    isDragging = true;
                    homeView.startDragging(touchedView, startX, startY);
                } else {
                    int cellWidth = getWidth() / HomeView.GRID_COLUMNS;
                    int cellHeight = getHeight() / HomeView.GRID_ROWS;
                    int col = (int) (startX / (cellWidth > 0 ? cellWidth : 1));
                    int row = (int) (startY / (cellHeight > 0 ? cellHeight : 1));
                    showHomeContextMenu(col, row, homeView.getCurrentPage());
                }
            }
        };

        public MainLayout(Context context) {
            super(context);
            setBackgroundResource(R.color.background);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (isDrawerOpen) return false;

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
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        longPressHandler.removeCallbacks(longPressRunnable);
                        if (!longPressTriggered) {
                            return true; // Intercept for swipe
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
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    float dy = event.getY() - startY;
                    if (dy > touchSlop * 3) closeDrawer();
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
                        homeView.endDragging();
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
            isDrawerOpen = true;
            homeView.animate().alpha(0).setDuration(200).withEndAction(() -> homeView.setVisibility(View.GONE));
            drawerView.setVisibility(View.VISIBLE);
            drawerView.setAlpha(0);
            drawerView.animate().alpha(1).setDuration(200).start();
            drawerView.onOpen();
        }

        public void closeDrawer() {
            isDrawerOpen = false;
            drawerView.animate().alpha(0).setDuration(200).withEndAction(() -> drawerView.setVisibility(View.GONE));
            homeView.setVisibility(View.VISIBLE);
            homeView.setAlpha(0);
            homeView.animate().alpha(1).setDuration(200).start();
            InputMethodManager imm = (InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }
}
