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
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);

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

        restoreWidget();
        loadApps();
        registerAppInstallReceiver();
    }

    private void showSettings() {
        String[] options = {"4 Columns", "5 Columns", "6 Columns", "Add Widget", "Remove Widget"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Settings")
               .setItems(options, (dialog, which) -> {
                   if (which < 3) {
                       int columns = which + 4;
                       settingsManager.setColumns(columns);
                       drawerView.setColumns(columns);
                   } else if (which == 3) {
                       pickWidget();
                   } else {
                       removeWidget();
                   }
               })
               .show();
    }

    private void removeWidget() {
        int appWidgetId = settingsManager.getWidgetId();
        if (appWidgetId != -1) {
            appWidgetHost.deleteAppWidgetId(appWidgetId);
            settingsManager.setWidgetId(-1);
            homeView.setWidget(null);
        }
    }

    private void restoreWidget() {
        int appWidgetId = settingsManager.getWidgetId();
        if (appWidgetId != -1) {
            AppWidgetProviderInfo appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId);
            if (appWidgetInfo != null) {
                AppWidgetHostView hostView = appWidgetHost.createView(this, appWidgetId, appWidgetInfo);
                hostView.setAppWidget(appWidgetId, appWidgetInfo);
                homeView.setWidget(hostView);
            } else {
                settingsManager.setWidgetId(-1);
            }
        }
    }

    private void loadApps() {
        model.loadApps(apps -> {
            drawerView.setApps(apps, model);
            homeView.setFavorites(apps.subList(0, Math.min(apps.size(), 8)), model);
        });
    }

    private void registerAppInstallReceiver() {
        appInstallReceiver = new AppInstallReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        registerReceiver(appInstallReceiver, filter);
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
    protected void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (model != null) {
            model.onTrimMemory(level);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(appInstallReceiver);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_PICK_APPWIDGET) {
                configureWidget(data);
            } else if (requestCode == REQUEST_CREATE_APPWIDGET) {
                createWidget(data);
            }
        } else if (resultCode == RESULT_CANCELED && data != null) {
            int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (appWidgetId != -1) {
                appWidgetHost.deleteAppWidgetId(appWidgetId);
            }
        }
    }

    private void configureWidget(Intent data) {
        Bundle extras = data.getExtras();
        int appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        AppWidgetProviderInfo appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId);
        if (appWidgetInfo.configure != null) {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(appWidgetInfo.configure);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            startActivityForResult(intent, REQUEST_CREATE_APPWIDGET);
        } else {
            createWidget(data);
        }
    }

    private void createWidget(Intent data) {
        Bundle extras = data.getExtras();
        int appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        settingsManager.setWidgetId(appWidgetId);
        AppWidgetProviderInfo appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId);
        AppWidgetHostView hostView = appWidgetHost.createView(this, appWidgetId, appWidgetInfo);
        hostView.setAppWidget(appWidgetId, appWidgetInfo);
        homeView.setWidget(hostView);
    }

    public void pickWidget() {
        int appWidgetId = appWidgetHost.allocateAppWidgetId();
        Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET);
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
            setBackgroundColor(Color.BLACK);
            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (e1 == null || e2 == null) return false;
                    float diffY = e2.getY() - e1.getY();
                    if (Math.abs(diffY) > 100 && Math.abs(velocityY) > 100) {
                        if (diffY < 0 && !isDrawerOpen) {
                            openDrawer();
                            return true;
                        } else if (diffY > 0 && isDrawerOpen) {
                            closeDrawer();
                            return true;
                        } else if (diffY > 0 && !isDrawerOpen) {
                            openDrawer();
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    if (!isDrawerOpen) {
                        showSettings();
                    }
                }
            });
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return gestureDetector.onTouchEvent(ev);
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
