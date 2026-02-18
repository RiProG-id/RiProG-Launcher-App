package com.riprog.launcher;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.riprog.launcher.HomeItem;

public class FreeformInteraction {
    private final Activity activity;
    private final FrameLayout rootLayout;
    private final SettingsManager preferences;
    private final InteractionCallback callback;

    private TransformOverlay currentTransformOverlay;
    private View transformingView;
    private ViewGroup transformingViewOriginalParent;
    private int transformingViewOriginalIndex = -1;

    public interface InteractionCallback {
        void onSaveState();
        void onRemoveItem(HomeItem item, View view);
        void onShowAppInfo(HomeItem item);
    }

    public FreeformInteraction(Activity activity, FrameLayout rootLayout, SettingsManager preferences, InteractionCallback callback) {
        this.activity = activity;
        this.rootLayout = rootLayout;
        this.preferences = preferences;
        this.callback = callback;
    }

    public void showTransformOverlay(View v) {
        if (currentTransformOverlay != null) return;

        transformingView = v;
        transformingViewOriginalParent = (ViewGroup) v.getParent();
        if (transformingViewOriginalParent != null) {
            transformingViewOriginalIndex = transformingViewOriginalParent.indexOfChild(v);
        }

        float x = v.getX();
        float y = v.getY();
        View p = (View) v.getParent();
        while (p != null && p != rootLayout) {
            x += p.getX();
            y += p.getY();
            if (p.getParent() instanceof View) {
                p = (View) p.getParent();
            } else {
                break;
            }
        }

        if (transformingViewOriginalParent != null) {
            transformingViewOriginalParent.removeView(v);
        }
        rootLayout.addView(v);
        v.setX(x);
        v.setY(y);

        currentTransformOverlay = new TransformOverlay(activity, v, preferences, new TransformOverlay.OnSaveListener() {
            @Override public void onMove(float x, float y) {}
            @Override public void onMoveStart(float x, float y) {}
            @Override public void onSave() { saveTransform(); closeTransformOverlay(); }
            @Override public void onCancel() { closeTransformOverlay(); }
            @Override public void onRemove() {
                HomeItem item = (HomeItem) v.getTag();
                callback.onRemoveItem(item, v);
                transformingView = null;
                closeTransformOverlay();
            }
            @Override public void onAppInfo() { callback.onShowAppInfo((HomeItem) v.getTag()); }
            @Override public void onUninstall() {}
            @Override public void onCollision(View otherView) {
                saveTransform();
                closeTransformOverlay();
                showTransformOverlay(otherView);
            }
            @Override public View findItemAt(float x, float y, View exclude) { return null; }
        });

        rootLayout.addView(currentTransformOverlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void saveTransform() {
        if (transformingView == null) return;
        HomeItem item = (HomeItem) transformingView.getTag();
        item.rotation = transformingView.getRotation();
        item.scale = transformingView.getScaleX();
        item.tiltX = transformingView.getRotationX();
        item.tiltY = transformingView.getRotationY();
        callback.onSaveState();
    }

    public void closeTransformOverlay() {
        if (currentTransformOverlay != null) {
            rootLayout.removeView(currentTransformOverlay);
            currentTransformOverlay = null;

            if (transformingView != null && transformingViewOriginalParent != null) {
                rootLayout.removeView(transformingView);
                transformingViewOriginalParent.addView(transformingView, transformingViewOriginalIndex);
            }
            transformingView = null;
            transformingViewOriginalParent = null;
        }
    }

    public boolean isTransforming() {
        return currentTransformOverlay != null;
    }
}
