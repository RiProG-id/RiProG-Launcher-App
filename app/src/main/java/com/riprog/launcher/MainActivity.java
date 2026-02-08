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
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
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
    private float lastLongPressX, lastLongPressY;

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
        HomeItem clockItem = HomeItem.createClock(0, 0, 0);
        // Center it roughly
        clockItem.x = (homeView.getWidth() - dpToPx(200)) / 2;
        clockItem.y = (homeView.getHeight() - dpToPx(100)) / 3;
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
            iconView.setOnClickListener(v -> {
                Intent intent = getPackageManager().getLaunchIntentForPackage(item.packageName);
                if (intent != null) startActivity(intent);
            });
            iconView.setOnLongClickListener(v -> {
                showAppOptions(item, iconView);
                return true;
            });
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
        hostView.setOnLongClickListener(v -> {
            showWidgetOptions(item, hostView);
            return true;
        });
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
        String[] sizes = {"Small", "Medium", "Large", "Full Width"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Resize Widget")
            .setItems(sizes, (dialog, which) -> {
                switch (which) {
                    case 0: item.width = 150; item.height = 100; break;
                    case 1: item.width = 250; item.height = 150; break;
                    case 2: item.width = 350; item.height = 250; break;
                    case 3: item.width = pxToDp(homeView.getWidth()) - 40; item.height = 150; break;
                }
                updateViewSize(item, hostView);
                saveHomeState();
            }).show();
    }

    private void updateViewSize(HomeItem item, View view) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        lp.width = dpToPx(item.width);
        lp.height = dpToPx(item.height);
        view.setLayoutParams(lp);
    }

    private void removeHomeItem(HomeItem item, View view) {
        homeItems.remove(item);
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        saveHomeState();
    }

    private int pxToDp(int px) {
        return (int) (px / getResources().getDisplayMetrics().density);
    }

    private void showAppOptions(HomeItem item, View view) {
        String[] options = {"Remove"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setItems(options, (dialog, which) -> {
                if (which == 0) removeHomeItem(item, view);
            }).show();
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

    private void showContextMenu(float x, float y, int page) {
        String[] options = {"Add App", "Widgets", "Wallpaper", "Launcher Settings", "Layout Options"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Home Menu")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: pickAppForHome((int)x, (int)y, page); break;
                    case 1: pickWidget(); break;
                    case 2: openWallpaperPicker(); break;
                    case 3: openSettings(); break;
                    case 4: showLayoutOptions(); break;
                }
            }).show();
    }

    private void pickAppForHome(int x, int y, int page) {
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
                HomeItem item = HomeItem.createApp(selected.packageName, selected.className, x, y, page);
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
        HomeItem item = HomeItem.createWidget(appWidgetId, (int)lastLongPressX, (int)lastLongPressY, 200, 100, homeView.getCurrentPage());
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
        private GestureDetector gestureDetector;

        public MainLayout(Context context) {
            super(context);
            setBackgroundResource(R.color.background);
            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (e1 == null || e2 == null) return false;
                    float diffY = e2.getY() - e1.getY();
                    if (Math.abs(diffY) > 100 && velocityY < -100) {
                        if (!isDrawerOpen) { openDrawer(); return true; }
                    } else if (Math.abs(diffY) > 100 && velocityY > 100) {
                        if (isDrawerOpen) { closeDrawer(); return true; }
                    }
                    return false;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    if (!isDrawerOpen) {
                        lastLongPressX = e.getX();
                        lastLongPressY = e.getY();
                        MainActivity.this.showContextMenu(e.getX(), e.getY(), homeView.getCurrentPage());
                    }
                }
            });
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            gestureDetector.onTouchEvent(ev);
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return gestureDetector.onTouchEvent(event);
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
