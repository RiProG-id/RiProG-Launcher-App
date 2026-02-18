package com.riprog.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class PageMenuUI {
    private final Context context;
    private final SettingsManager settingsManager;
    private final PageActionCallback callback;

    public interface PageActionCallback {
        void onAddPage();
        void onRemovePage();
        int getPageCount();
    }

    public PageMenuUI(Context context, SettingsManager settingsManager, PageActionCallback callback) {
        this.context = context;
        this.settingsManager = settingsManager;
        this.callback = callback;
    }

    public void showPageMenu() {
        List<String> optionsList = new ArrayList<>();
        List<Integer> iconsList = new ArrayList<>();

        optionsList.add(context.getString(R.string.layout_add_page));
        iconsList.add(R.drawable.ic_layout);

        if (callback.getPageCount() > 1) {
            optionsList.add(context.getString(R.string.layout_remove_page));
            iconsList.add(R.drawable.ic_remove);
        }

        int adaptiveColor = ThemeUtils.getAdaptiveColor(context, settingsManager, true);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.select_dialog_item, android.R.id.text1, optionsList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = view.findViewById(android.R.id.text1);
                tv.setCompoundDrawablesWithIntrinsicBounds(iconsList.get(position), 0, 0, 0);
                tv.setCompoundDrawablePadding(ThemeUtils.dpToPx(context, 16));
                tv.setTextColor(adaptiveColor);
                if (tv.getCompoundDrawables()[0] != null) {
                    tv.getCompoundDrawables()[0].setTint(adaptiveColor);
                }
                return view;
            }
        };

        AlertDialog dialog = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.title_home_menu)
                .setAdapter(adapter, (d, which) -> {
                    String selected = optionsList.get(which);
                    if (selected.equals(context.getString(R.string.layout_add_page))) {
                        callback.onAddPage();
                    } else if (selected.equals(context.getString(R.string.layout_remove_page))) {
                        callback.onRemovePage();
                    }
                }).create();

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(ThemeUtils.getGlassDrawable(context, settingsManager, 28));
            ThemeUtils.applyWindowBlur(dialog.getWindow(), settingsManager.isLiquidGlass());
        }
    }
}
