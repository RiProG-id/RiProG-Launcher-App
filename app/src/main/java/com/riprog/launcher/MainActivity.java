package com.riprog.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

	private List<ResolveInfo> apps = new ArrayList<>();
	private PackageManager pm;
	private GridView gridView;
	private AppAdapter appAdapter;
	private LruCache<String, Bitmap> iconCache;
	private AppInstallReceiver appInstallReceiver;

	private int iconSizePx;
	private static final int ICON_SIZE_DP = 64;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().setStatusBarColor(0x00000000);
		getWindow().setNavigationBarColor(0x00000000);
		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

		pm = getPackageManager();
		iconSizePx = dpToPx(ICON_SIZE_DP);

		final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
		final int cacheSize = maxMemory / 8;
		iconCache = new LruCache<String, Bitmap>(cacheSize) {
			@Override
			protected int sizeOf(String key, Bitmap bitmap) {
				return bitmap.getByteCount() / 1024;
			}
		};

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

		appAdapter = new AppAdapter(this);
		gridView.setAdapter(appAdapter);

		setContentView(gridView);

		loadApps();
		registerAppInstallReceiver();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(appInstallReceiver);
	}

	private void registerAppInstallReceiver() {
		appInstallReceiver = new AppInstallReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_PACKAGE_ADDED);
		filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
		filter.addDataScheme("package");
		registerReceiver(appInstallReceiver, filter);
	}

	private void loadApps() {
		String selfPackage = getPackageName();
		Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
		mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		List<ResolveInfo> allApps = pm.queryIntentActivities(mainIntent, 0);

		apps.clear();
		for (ResolveInfo info : allApps) {
			if (!info.activityInfo.packageName.equals(selfPackage)) {
				apps.add(info);
			}
		}
		Collections.sort(apps, new ResolveInfo.DisplayNameComparator(pm));

		if (appAdapter != null) {
			appAdapter.notifyDataSetChanged();
		}
	}

	private class AppAdapter extends BaseAdapter {
		private Context context;

		public AppAdapter(Context c) {
			context = c;
		}

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
				LinearLayout layout = new LinearLayout(context);
				layout.setOrientation(LinearLayout.VERTICAL);
				layout.setGravity(Gravity.CENTER_HORIZONTAL);

				ImageView iconView = new ImageView(context);
				LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSizePx, iconSizePx);
				iconView.setLayoutParams(iconParams);

				TextView textView = new TextView(context);
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

				convertView = layout;
				holder = new ViewHolder(iconView, textView);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			ResolveInfo app = apps.get(position);
			String pkg = app.activityInfo.packageName;

			holder.textView.setText(app.loadLabel(pm));

			final Bitmap cachedIcon = iconCache.get(pkg);
			if (cachedIcon != null) {
				holder.iconView.setImageBitmap(cachedIcon);
			} else {
				holder.iconView.setImageDrawable(null);
				loadIcon(app, holder.iconView);
			}

			return convertView;
		}
	}

	private void loadIcon(ResolveInfo appInfo, ImageView imageView) {
		if (cancelPotentialWork(appInfo, imageView)) {
			final IconLoaderTask task = new IconLoaderTask(appInfo, imageView, pm);
			final AsyncDrawable asyncDrawable = new AsyncDrawable(task);
			imageView.setImageDrawable(asyncDrawable);
			task.execute();
		}
	}

	private static boolean cancelPotentialWork(ResolveInfo appInfo, ImageView imageView) {
		final IconLoaderTask iconLoaderTask = getIconLoaderTask(imageView);

		if (iconLoaderTask != null) {
			final ResolveInfo taskAppInfo = iconLoaderTask.getAppInfo();
			if (taskAppInfo == null || taskAppInfo.activityInfo.packageName != appInfo.activityInfo.packageName) {
				iconLoaderTask.cancel(true);
			} else {
				return false;
			}
		}
		return true;
	}

	private static IconLoaderTask getIconLoaderTask(ImageView imageView) {
		if (imageView != null) {
			final Drawable drawable = imageView.getDrawable();
			if (drawable instanceof AsyncDrawable) {
				return ((AsyncDrawable) drawable).getIconLoaderTask();
			}
		}
		return null;
	}

	class IconLoaderTask extends AsyncTask<Void, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewReference;
		private final ResolveInfo appInfo;
		private final PackageManager packageManager;

		public IconLoaderTask(ResolveInfo appInfo, ImageView imageView, PackageManager pm) {
			imageViewReference = new WeakReference<>(imageView);
			this.appInfo = appInfo;
			this.packageManager = pm;
		}

		public ResolveInfo getAppInfo() {
			return appInfo;
		}

		@Override
		protected Bitmap doInBackground(Void... params) {
			if (isCancelled())
				return null;
			Drawable icon = appInfo.loadIcon(packageManager);
			Bitmap bitmap = createScaledBitmap(icon, iconSizePx, iconSizePx);
			if (bitmap != null) {
				iconCache.put(appInfo.activityInfo.packageName, bitmap);
			}
			return bitmap;
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (isCancelled() || bitmap == null) {
				return;
			}
			final ImageView imageView = imageViewReference.get();
			if (imageView != null) {
				final IconLoaderTask iconLoaderTask = getIconLoaderTask(imageView);
				if (this == iconLoaderTask) {
					imageView.setImageBitmap(bitmap);
				}
			}
		}
	}

	static class AsyncDrawable extends BitmapDrawable {
		private final WeakReference<IconLoaderTask> iconLoaderTaskReference;
		public AsyncDrawable(IconLoaderTask iconLoaderTask) {
			this.iconLoaderTaskReference = new WeakReference<>(iconLoaderTask);
		}
		public IconLoaderTask getIconLoaderTask() {
			return iconLoaderTaskReference.get();
		}
	}

	private Bitmap createScaledBitmap(Drawable drawable, int width, int height) {
		if (drawable == null)
			return null;
		Bitmap bitmap;
		if (drawable instanceof BitmapDrawable) {
			bitmap = ((BitmapDrawable) drawable).getBitmap();
		} else {
			bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(),
					Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(bitmap);
			drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
			drawable.draw(canvas);
		}
		return Bitmap.createScaledBitmap(bitmap, width, height, true);
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

	public class AppInstallReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			loadApps();
		}
	}
}

