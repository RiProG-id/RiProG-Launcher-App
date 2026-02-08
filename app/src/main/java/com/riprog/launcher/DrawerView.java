package com.riprog.launcher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
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
import java.util.Collections;
import java.util.List;

public class DrawerView extends LinearLayout {
    private final GridView gridView;
    private final AppAdapter adapter;
    private OnAppLongClickListener longClickListener;
    private List<AppItem> allApps = new ArrayList<>();

    public interface OnAppLongClickListener {
        void onAppLongClick(AppItem app);
    }

    public void setOnAppLongClickListener(OnAppLongClickListener listener) {
        this.longClickListener = listener;
    }
    private List<AppItem> filteredApps = new ArrayList<>();
    private LauncherModel model;
    private final EditText searchBar;
    private final LinearLayout indexBar;

    public DrawerView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.glass_bg);
        setPadding(0, dpToPx(48), 0, 0);

        searchBar = new EditText(context);
        searchBar.setHint("🔍 Search apps...");
        searchBar.setHintTextColor(context.getColor(R.color.foreground_dim));
        searchBar.setTextColor(context.getColor(R.color.foreground));
        searchBar.setBackgroundColor(context.getColor(R.color.search_background));
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
        addView(searchBar);

        FrameLayout contentFrame = new FrameLayout(context);
        addView(contentFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        gridView = new GridView(context);
        gridView.setNumColumns(4);
        gridView.setVerticalSpacing(dpToPx(16));
        gridView.setPadding(dpToPx(8), dpToPx(16), dpToPx(32), dpToPx(16));
        gridView.setVerticalScrollBarEnabled(false);
        contentFrame.addView(gridView);

        indexBar = new LinearLayout(context);
        indexBar.setOrientation(LinearLayout.VERTICAL);
        indexBar.setGravity(Gravity.CENTER);
        indexBar.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                float y = event.getY();
                int childCount = indexBar.getChildCount();
                if (childCount > 0) {
                    float itemHeight = (float) indexBar.getHeight() / childCount;
                    int index = (int) (y / itemHeight);
                    if (index >= 0 && index < childCount) {
                        View child = indexBar.getChildAt(index);
                        if (child instanceof TextView) {
                            scrollToLetter(((TextView) child).getText().toString());
                        }
                    }
                }
                return true;
            }
            return false;
        });
        FrameLayout.LayoutParams indexParams = new FrameLayout.LayoutParams(dpToPx(30), ViewGroup.LayoutParams.MATCH_PARENT);
        indexParams.gravity = Gravity.END;
        contentFrame.addView(indexBar, indexParams);
        setupIndexBar();

        adapter = new AppAdapter();
        gridView.setAdapter(adapter);

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            AppItem item = filteredApps.get(position);
            Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(item.packageName);
            if (intent != null) getContext().startActivity(intent);
        });
        gridView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (longClickListener != null) {
                AppItem item = filteredApps.get(position);
                longClickListener.onAppLongClick(item);
                return true;
            }
            return false;
        });
    }

    public void setApps(List<AppItem> apps, LauncherModel model) {
        this.allApps = apps;
        this.model = model;
        sortAppsAlphabetically();
        filter(searchBar.getText().toString());
    }

    private void sortAppsAlphabetically() {
        Collections.sort(allApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
    }

    private void setupIndexBar() {
        indexBar.removeAllViews();
        String[] alphabet = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".split("");
        for (String letter : alphabet) {
            if (letter.isEmpty()) continue;
            TextView tv = new TextView(getContext());
            tv.setText(letter);
            tv.setTextSize(10);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(getContext().getColor(R.color.foreground_dim));
            tv.setPadding(0, dpToPx(2), 0, dpToPx(2));
            indexBar.addView(tv);
        }
    }

    private void scrollToLetter(String letter) {
        for (int i = 0; i < filteredApps.size(); i++) {
            if (filteredApps.get(i).label.toUpperCase().startsWith(letter)) {
                gridView.setSelection(i);
                break;
            }
        }
    }

    public void setColumns(int columns) {
        gridView.setNumColumns(columns);
    }

    public boolean isAtTop() {
        if (gridView.getChildCount() == 0) return true;
        return gridView.getFirstVisiblePosition() == 0 && gridView.getChildAt(0).getTop() >= gridView.getPaddingTop();
    }

    public void setAccentColor(int color) {
        if (searchBar != null) searchBar.setHintTextColor(color & 0x80FFFFFF);
    }

    public void filter(String query) {
        filteredApps = LauncherModel.filterApps(allApps, query);
        adapter.notifyDataSetChanged();
    }

    public void onOpen() {
        searchBar.setText("");
        searchBar.clearFocus();
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
                int size = getResources().getDimensionPixelSize(R.dimen.grid_icon_size);
                itemLayout.addView(icon, new LinearLayout.LayoutParams(size, size));

                TextView label = new TextView(getContext());
                label.setTextColor(getContext().getColor(R.color.foreground_dim));
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
            holder.icon.setImageBitmap(null);
            holder.icon.setTag(item.packageName);
            if (model != null) {
                model.loadIcon(item, bitmap -> {
                    if (bitmap != null && item.packageName.equals(holder.icon.getTag())) {
                        holder.icon.setImageBitmap(bitmap);
                    }
                });
            }

            return convertView;
        }
    }
}
