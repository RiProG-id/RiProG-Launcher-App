package com.riprog.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

	private List<ResolveInfo> apps = new ArrayList<>();
	private PackageManager pm;
	private GridView gridView;
	private LruCache<String, Drawable> iconCache;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().setStatusBarColor(0x00000000);
		getWindow().setNavigationBarColor(0x00000000);
		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

		pm = getPackageManager();

		final int cacheSize = 64;
		iconCache = new LruCache<>(cacheSize);

		gridView = new GridView(this);
		gridView.setNumColumns(4);
		gridView.setVerticalSpacing(dpToPx(16));
		gridView.setHorizontalSpacing(dpToPx(16));
		gridView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
		gridView.setGravity(Gravity.CENTER);
		gridView.setPadding(dpToPx(8), dpToPx(16), dpToPx(8), dpToPx(24));
		gridView.setClipToPadding(false);
		gridView.setFitsSystemWindows(true);
		gridView.setVerticalScrollBarEnabled(false);

		gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				ResolveInfo app = apps.get(position);
				Intent launchIntent = pm.getLaunchIntentForPackage(app.activityInfo.packageName);
				if (launchIntent != null) {
					startActivity(launchIntent);
				}
			}
		});

		setContentView(gridView);
		refreshApps();
	}

	@Override
	protected void onResume() {
		super.onResume();
		refreshApps();
	}

	private void refreshApps() {
		String selfPackage = getPackageName();
		Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
		mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		List<ResolveInfo> allApps = pm.queryIntentActivities(mainIntent, 0);
		apps.clear();
		for (ResolveInfo info : allApps) {
			String packageName = info.activityInfo.packageName;
			if (!packageName.equals(selfPackage)) {
				apps.add(info);
			}
		}
		Collections.sort(apps, new ResolveInfo.DisplayNameComparator(pm));

		gridView.setAdapter(new BaseAdapter() {
			@Override
			public int getCount() {
				return apps.size();
			}

			@Override
			public Object getItem(int position) {
				return apps.get(position);
			}

			@Override
			public long getItemId(int position) {
				return position;
			}

			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				ViewHolder holder;

				if (convertView == null) {
					LinearLayout layout = new LinearLayout(MainActivity.this);
					layout.setOrientation(LinearLayout.VERTICAL);
					layout.setGravity(Gravity.CENTER_HORIZONTAL);
					layout.setLayoutParams(new ViewGroup.LayoutParams(dpToPx(80), dpToPx(120)));

					ImageView iconView = new ImageView(MainActivity.this);
					LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dpToPx(64), dpToPx(64));
					iconParams.topMargin = dpToPx(8);
					iconParams.gravity = Gravity.CENTER_HORIZONTAL;
					iconView.setLayoutParams(iconParams);

					TextView textView = new TextView(MainActivity.this);
					textView.setGravity(Gravity.CENTER);
					textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
					textView.setMaxLines(2);
					textView.setEllipsize(TextUtils.TruncateAt.END);
					LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
							ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
					textParams.topMargin = dpToPx(4);
					textView.setLayoutParams(textParams);

					layout.addView(iconView);
					layout.addView(textView);

					holder = new ViewHolder(iconView, textView);
					layout.setTag(holder);
					convertView = layout;
				} else {
					holder = (ViewHolder) convertView.getTag();
				}

				ResolveInfo app = apps.get(position);
				String pkg = app.activityInfo.packageName;

				Drawable icon = iconCache.get(pkg);
				if (icon == null) {
					icon = app.loadIcon(pm);
					iconCache.put(pkg, icon);
				}
				holder.iconView.setImageDrawable(icon);
				holder.textView.setText(app.loadLabel(pm));

				return convertView;
			}
		});
	}

	private static class ViewHolder {
		ImageView iconView;
		TextView textView;

		ViewHolder(ImageView iconView, TextView textView) {
			this.iconView = iconView;
			this.textView = textView;
		}
	}

	private int dpToPx(int dp) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
	}
}

