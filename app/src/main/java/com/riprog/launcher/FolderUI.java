package com.riprog.launcher;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.riprog.launcher.HomeItem;

public class FolderUI {
    private final Context context;
    private final SettingsManager preferences;

    public FolderUI(Context context, SettingsManager preferences) {
        this.context = context;
        this.preferences = preferences;
    }

    public View createFolderView(HomeItem item, boolean isOnGlass, int cellWidth, int cellHeight) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);

        FrameLayout previewContainer = new FrameLayout(context);
        float scale = preferences.getIconScale();
        int sizeW;
        int sizeH;

        if (item.spanX <= 1 && item.spanY <= 1) {
            int baseSize = context.getResources().getDimensionPixelSize(R.dimen.grid_icon_size);
            sizeW = (int) (baseSize * scale);
            sizeH = sizeW;
        } else {
            sizeW = (int) (cellWidth * item.spanX);
            sizeH = (int) (cellHeight * item.spanY);
        }

        previewContainer.setLayoutParams(new LinearLayout.LayoutParams(sizeW, sizeH));
        previewContainer.setBackground(ThemeUtils.getGlassDrawable(context, preferences, 12f));
        int padding = dpToPx(6);
        previewContainer.setPadding(padding, padding, padding, padding);

        GridLayout grid = new GridLayout(context);
        grid.setTag("folder_grid");
        grid.setColumnCount(2);
        grid.setRowCount(2);
        previewContainer.addView(grid, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView labelView = new TextView(context);
        labelView.setTag("item_label");
        labelView.setTextColor(ThemeUtils.getAdaptiveColor(context, preferences, isOnGlass));
        labelView.setTextSize(10 * scale);
        labelView.setGravity(Gravity.CENTER);
        labelView.setMaxLines(1);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        labelView.setText(item.folderName == null ? "" : item.folderName);

        container.addView(previewContainer);
        container.addView(labelView);
        if (preferences.isHideLabels()) {
            labelView.setVisibility(View.GONE);
        }
        return container;
    }

    private int dpToPx(float dp) {
        return (int) android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
