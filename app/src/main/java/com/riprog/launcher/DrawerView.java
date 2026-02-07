package com.riprog.launcher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class DrawerView extends FrameLayout {
    private final GridView gridView;
    private final AppAdapter adapter;
    private List<AppItem> allApps = new ArrayList<>();
    private List<AppItem> filteredApps = new ArrayList<>();
    private LauncherModel model;
    private final EditText searchBar;

    public DrawerView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setPadding(0, dpToPx(48), 0, 0);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        addView(layout);

        searchBar = new EditText(context);
        searchBar.setHint("Search...");
        searchBar.setHintTextColor(0xFF444444);
        searchBar.setTextColor(Color.WHITE);
        searchBar.setBackgroundColor(0x22FFFFFF);
        searchBar.setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(12));
        searchBar.setSingleLine(true);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        layout.addView(searchBar);

        gridView = new GridView(context);
        gridView.setNumColumns(4);
        gridView.setVerticalSpacing(dpToPx(16));
        gridView.setPadding(dpToPx(8), dpToPx(16), dpToPx(8), dpToPx(16));
        layout.addView(gridView);

        adapter = new AppAdapter();
        gridView.setAdapter(adapter);

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            AppItem item = filteredApps.get(position);
            Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(item.packageName);
            if (intent != null) getContext().startActivity(intent);
        });
    }

    public void setApps(List<AppItem> apps, LauncherModel model) {
        this.allApps = apps;
        this.model = model;
        filter(searchBar.getText().toString());
    }

    public void setColumns(int columns) {
        gridView.setNumColumns(columns);
    }

    public void filter(String query) {
        filteredApps = LauncherModel.filterApps(allApps, query);
        adapter.notifyDataSetChanged();
    }

    public void onOpen() {
        searchBar.setText("");
        searchBar.requestFocus();
        InputMethodManager imm = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(searchBar, InputMethodManager.SHOW_IMPLICIT);
    }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private static class ViewHolder {
        ImageView icon;
        TextView label;
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredApps.size(); }
        @Override public Object getItem(int position) { return filteredApps.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                LinearLayout itemLayout = new LinearLayout(getContext());
                itemLayout.setOrientation(LinearLayout.VERTICAL);
                itemLayout.setGravity(Gravity.CENTER);

                ImageView icon = new ImageView(getContext());
                int size = dpToPx(48);
                itemLayout.addView(icon, new LinearLayout.LayoutParams(size, size));

                TextView label = new TextView(getContext());
                label.setTextColor(Color.LTGRAY);
                label.setTextSize(10);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(1);
                label.setEllipsize(TextUtils.TruncateAt.END);
                itemLayout.addView(label);

                convertView = itemLayout;
                holder = new ViewHolder();
                holder.icon = icon;
                holder.label = label;
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            AppItem item = filteredApps.get(position);
            holder.label.setText(item.label);
            holder.icon.setImageBitmap(null); // Reset icon before loading
            model.loadIcon(item, holder.icon::setImageBitmap);

            return convertView;
        }
    }
}
