package com.riprog.launcher;

import android.app.Application;

public class LauncherApplication extends Application {
    private LauncherModel model;

    @Override
    public void onCreate() {
        super.onCreate();
        model = new LauncherModel(this);
    }

    public LauncherModel getModel() {
        return model;
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (model != null) {
            model.onTrimMemory(level);
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (model != null) {
            model.shutdown();
        }
    }
}
