package com.riprog.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class FolderManager {
    private final Context context;
    private final SettingsManager settingsManager;
    private final LauncherModel model;

    public FolderManager(Context context, SettingsManager settingsManager, LauncherModel model) {
        this.context = context;
        this.settingsManager = settingsManager;
        this.model = model;
    }

    public void showFolder(HomeItem folderItem, Runnable onUpdate) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int p16 = ThemeUtils.dpToPx(context, 16);
        root.setPadding(p16, p16, p16, p16);

        EditText nameEdit = new EditText(context);
        nameEdit.setText(folderItem.folderName);
        int adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true);
        nameEdit.setTextColor(adaptiveColor);
        nameEdit.setHintTextColor(adaptiveColor & 0x80FFFFFF);
        nameEdit.setGravity(Gravity.CENTER);
        nameEdit.setBackground(null);
        nameEdit.setTextSize(24f);
        nameEdit.setTypeface(null, Typeface.BOLD);
        root.addView(nameEdit);

        GridView gridView = new GridView(context);
        gridView.setNumColumns(4);
        gridView.setPadding(0, p16, 0, 0);
        gridView.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return folderItem.folderItems != null ? folderItem.folderItems.size() : 0; }
            @Override public Object getItem(int position) { return folderItem.folderItems.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    ImageView iv = new ImageView(context);
                    int size = ThemeUtils.dpToPx(context, 60);
                    iv.setLayoutParams(new ViewGroup.LayoutParams(size, size));
                    iv.setPadding(p16/2, p16/2, p16/2, p16/2);
                    convertView = iv;
                }
                HomeItem subItem = folderItem.folderItems.get(position);
                AppItem app = new AppItem("", subItem.packageName, subItem.className);
                model.loadIcon(app, ((ImageView) convertView)::setImageBitmap);
                convertView.setOnClickListener(v -> {
                    Intent intent = context.getPackageManager().getLaunchIntentForPackage(subItem.packageName);
                    if (intent != null) context.startActivity(intent);
                });
                return convertView;
            }
        });
        root.addView(gridView);

        AlertDialog dialog = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(root)
                .setOnDismissListener(d -> {
                    folderItem.folderName = nameEdit.getText().toString();
                    onUpdate.run();
                })
                .create();

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(ThemeUtils.getGlassDrawable(context, settingsManager, 28));
            ThemeUtils.applyWindowBlur(dialog.getWindow(), settingsManager.isLiquidGlass());
        }
    }

    public HomeItem createFolder(HomeItem item1, HomeItem item2, int page, float col, float row) {
        HomeItem folder = HomeItem.createFolder("New Folder", col, row, page);
        folder.folderItems.add(item1);
        folder.folderItems.add(item2);
        return folder;
    }
}
