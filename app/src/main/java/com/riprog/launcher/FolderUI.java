package com.riprog.launcher;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FolderUI {
    private final Context context;
    private final SettingsManager settingsManager;

    public FolderUI(Context context, SettingsManager settingsManager) {
        this.context = context;
        this.settingsManager = settingsManager;
    }

    public View createFolderView(HomeItem item, boolean isOnGlass, int cellWidth, int cellHeight, LauncherModel model) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);

        FrameLayout previewContainer = new FrameLayout(context);
        float scale = settingsManager.getIconScale();
        int sizeW, sizeH;

        if (item.spanX <= 1.0f && item.spanY <= 1.0f) {
            int baseSize = context.getResources().getDimensionPixelSize(R.dimen.grid_icon_size);
            sizeW = (int) (baseSize * scale);
            sizeH = sizeW;
        } else {
            sizeW = (int) (cellWidth * item.spanX);
            sizeH = (int) (cellHeight * item.spanY);
        }

        previewContainer.setLayoutParams(new LinearLayout.LayoutParams(sizeW, sizeH));
        previewContainer.setBackground(ThemeUtils.getGlassDrawable(context, settingsManager, 12f));
        int padding = ThemeUtils.dpToPx(context, 6);
        previewContainer.setPadding(padding, padding, padding, padding);

        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(2);
        grid.setRowCount(2);
        previewContainer.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        refreshFolderPreview(item, grid, model);

        TextView labelView = new TextView(context);
        labelView.setTextColor(ThemeUtils.getAdaptiveColor(context, settingsManager, isOnGlass));
        labelView.setTextSize(10 * scale);
        labelView.setGravity(Gravity.CENTER);
        labelView.setMaxLines(1);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        labelView.setText(item.folderName == null ? "" : item.folderName);

        container.addView(previewContainer);
        container.addView(labelView);
        if (settingsManager.isHideLabels()) {
            labelView.setVisibility(View.GONE);
        }
        return container;
    }

    public void refreshFolderPreview(HomeItem item, GridLayout grid, LauncherModel model) {
        grid.removeAllViews();
        if (item.folderItems == null) return;

        int count = Math.min(item.folderItems.size(), 4);
        for (int i = 0; i < count; i++) {
            HomeItem subItem = item.folderItems.get(i);
            ImageView iv = new ImageView(context);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = 0;
            lp.columnSpec = GridLayout.spec(i % 2, 1, 1f);
            lp.rowSpec = GridLayout.spec(i / 2, 1, 1f);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(2, 2, 2, 2);
            grid.addView(iv);

            if (model != null) {
                AppItem app = new AppItem("", subItem.packageName, subItem.className);
                model.loadIcon(app, iv::setImageBitmap);
            }
        }
    }
}
