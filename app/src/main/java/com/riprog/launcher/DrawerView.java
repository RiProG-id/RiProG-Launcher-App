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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class DrawerView extends LinearLayout {
    private final ListView listView;
    private final AppRowAdapter adapter;
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
    private final SettingsManager settingsManager;
    private final EditText searchBar;
    private final IndexBar indexBar;

    private static class IndexBar extends LinearLayout {
        public IndexBar(Context context) {
            super(context);
        }
        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }

    private int insetTop, insetBottom, insetLeft, insetRight;
    private int numColumns = 4;

    public DrawerView(Context context) {
        super(context);
        settingsManager = new SettingsManager(context);
        setOrientation(VERTICAL);
        setBackground(ThemeUtils.getGlassDrawable(context, settingsManager));

        FrameLayout contentFrame = new FrameLayout(context);
        addView(contentFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        listView = new ListView(context);
        listView.setDivider(null);
        listView.setSelector(android.R.color.transparent);
        listView.setVerticalScrollBarEnabled(false);
        contentFrame.addView(listView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout searchContainer = new LinearLayout(context);
        searchContainer.setOrientation(HORIZONTAL);
        searchContainer.setGravity(Gravity.CENTER_VERTICAL);
        searchContainer.setBackground(ThemeUtils.getGlassDrawable(context, settingsManager, 12));
        searchContainer.setPadding(dpToPx(12), 0, dpToPx(12), 0);

        searchBar = new EditText(context);
        searchBar.setHint(R.string.search_hint);
        searchBar.setHintTextColor(context.getColor(R.color.foreground_dim));
        searchBar.setTextColor(context.getColor(R.color.foreground));
        searchBar.setBackground(null);
        searchBar.setPadding(dpToPx(8), dpToPx(12), dpToPx(8), dpToPx(12));
        searchBar.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        searchBar.setCompoundDrawablePadding(dpToPx(12));
        if (searchBar.getCompoundDrawables()[0] != null) {
            searchBar.getCompoundDrawables()[0].setTint(context.getColor(R.color.foreground_dim));
        }
        searchBar.setSingleLine(true);
        searchBar.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        searchContainer.addView(searchBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout headerContainer = new LinearLayout(context);
        headerContainer.setOrientation(VERTICAL);
        LinearLayout.LayoutParams searchContainerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int searchMargin = dpToPx(16);
        searchContainerParams.setMargins(searchMargin, searchMargin, searchMargin, searchMargin);
        headerContainer.addView(searchContainer, searchContainerParams);

        listView.addHeaderView(headerContainer, null, false);

        adapter = new AppRowAdapter();
        listView.setAdapter(adapter);

        indexBar = new IndexBar(context);
        indexBar.setOrientation(LinearLayout.VERTICAL);
        indexBar.setGravity(Gravity.CENTER);
        indexBar.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.performClick();
                }
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
            if (filteredApps.get(i).label.toUpperCase(Locale.getDefault()).startsWith(letter)) {
                int row = i / numColumns;
                listView.setSelection(row + listView.getHeaderViewsCount());
                break;
            }
        }
    }

    public void setColumns(int columns) {
        this.numColumns = columns;
        adapter.notifyDataSetChanged();
    }

    public boolean isAtTop() {
        if (listView.getChildCount() == 0) return true;
        return listView.getFirstVisiblePosition() == 0 && listView.getChildAt(0).getTop() >= 0;
    }

    public void setAccentColor(int color) {
        if (searchBar != null) searchBar.setHintTextColor(color & 0x80FFFFFF);
    }

    public void filter(String query) {
        filteredApps = LauncherModel.filterApps(allApps, query);
        adapter.notifyDataSetChanged();
    }

    public void setSystemInsets(int left, int top, int right, int bottom) {
        this.insetLeft = left;
        this.insetTop = top;
        this.insetRight = right;
        this.insetBottom = bottom;
        updatePadding();
    }

    private void updatePadding() {
        setPadding(insetLeft, insetTop + dpToPx(8), insetRight, 0);
        listView.setPadding(dpToPx(8), dpToPx(16), dpToPx(32), insetBottom + dpToPx(16));
        listView.setClipToPadding(false);
    }

    public void onOpen() {
        setBackground(ThemeUtils.getGlassDrawable(getContext(), settingsManager));
        updatePadding();
        searchBar.setText("");
        searchBar.clearFocus();
        listView.setSelection(0);
        adapter.notifyDataSetChanged();
    }

    public void onClose() {
        searchBar.setText("");
        filteredApps.clear();
        adapter.notifyDataSetChanged();
    }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private class AppRowAdapter extends BaseAdapter {
        private float lastScale = -1f;
        private int lastSize = -1;

        @Override public int getCount() { return (int) Math.ceil((double) filteredApps.size() / numColumns); }
        @Override public Object getItem(int position) { return null; }
        @Override public long getItemId(int position) { return position; }

        private class ViewHolder {
            final LinearLayout rowLayout;
            final List<ItemViewHolder> itemHolders = new ArrayList<>();
            int columns;

            ViewHolder(LinearLayout rowLayout, int columns) {
                this.rowLayout = rowLayout;
                this.columns = columns;
                rebuild();
            }

            void rebuild() {
                rowLayout.removeAllViews();
                itemHolders.clear();
                for (int i = 0; i < numColumns; i++) {
                    ItemViewHolder ivh = new ItemViewHolder();
                    rowLayout.addView(ivh.root, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
                    itemHolders.add(ivh);
                }
                this.columns = numColumns;
            }
        }

        private class ItemViewHolder {
            final FrameLayout root;
            final LinearLayout container;
            final ImageView icon;
            final TextView label;

            ItemViewHolder() {
                root = new FrameLayout(getContext());
                container = new LinearLayout(getContext());
                container.setOrientation(VERTICAL);
                container.setGravity(Gravity.CENTER);

                icon = new ImageView(getContext());
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                container.addView(icon);

                label = new TextView(getContext());
                label.setTextColor(getContext().getColor(R.color.foreground));
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(1);
                label.setEllipsize(TextUtils.TruncateAt.END);
                container.addView(label);

                root.addView(container);
            }
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                LinearLayout rowLayout = new LinearLayout(getContext());
                rowLayout.setOrientation(HORIZONTAL);
                rowLayout.setPadding(0, dpToPx(8), 0, dpToPx(8));
                holder = new ViewHolder(rowLayout, numColumns);
                convertView = rowLayout;
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
                if (holder.columns != numColumns) {
                    holder.rebuild();
                }
            }

            if (lastScale < 0) {
                lastScale = settingsManager.getIconScale();
                lastSize = (int) (getResources().getDimensionPixelSize(R.dimen.grid_icon_size) * lastScale);
            }

            int start = position * numColumns;
            for (int i = 0; i < numColumns; i++) {
                ItemViewHolder ivh = holder.itemHolders.get(i);
                int index = start + i;

                if (index < filteredApps.size()) {
                    AppItem item = filteredApps.get(index);
                    ivh.root.setVisibility(VISIBLE);
                    ivh.label.setText(item.label);
                    ivh.label.setTextSize(10 * lastScale);

                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) ivh.icon.getLayoutParams();
                    if (lp.width != lastSize) {
                        lp.width = lastSize;
                        lp.height = lastSize;
                        ivh.icon.setLayoutParams(lp);
                    }

                    ivh.root.setOnClickListener(v -> {
                        Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(item.packageName);
                        if (intent != null) getContext().startActivity(intent);
                    });
                    ivh.root.setOnLongClickListener(v -> {
                        if (longClickListener != null) {
                            longClickListener.onAppLongClick(item);
                            return true;
                        }
                        return false;
                    });

                    ivh.icon.setImageDrawable(null);
                    if (model != null) {
                        model.loadIcon(item, bitmap -> {
                            if (bitmap != null) {
                                ivh.icon.setImageBitmap(bitmap);
                            }
                        });
                    }
                } else {
                    ivh.root.setVisibility(INVISIBLE);
                    ivh.root.setOnClickListener(null);
                    ivh.root.setOnLongClickListener(null);
                }
            }
            return convertView;
        }

        @Override
        public void notifyDataSetChanged() {
            lastScale = -1f;
            super.notifyDataSetChanged();
        }
    }
}
