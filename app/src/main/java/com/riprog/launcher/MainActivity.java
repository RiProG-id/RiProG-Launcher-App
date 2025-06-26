package com.riprog.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

	private List<ResolveInfo> apps = new ArrayList<>();
	private PackageManager pm;
	private GridView gridView;
	private AppAdapter appAdapter;
	private LruCache<String, Bitmap> iconMemoryCache;
	private File iconDiskCacheDir;
	private AppInstallReceiver appInstallReceiver;
	private File appListCacheFile;

	private int iconSizePx;
	private static final int ICON_SIZE_DP = 64;
	private static final String DISK_CACHE_SUBDIR = "thumbnails";
	private static final String APP_LIST_CACHE_FILENAME = "applist.cache";
	private static final long CACHE_EXPIRATION_DAYS = 30;

	private Handler handler = new Handler();
	private Runnable loadAppsRunnable = new Runnable() {
		@Override
		public void run() {
			loadAppsInternal();
		}
	};
	private static final long DEBOUNCE_DELAY_MS = 500;

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
		final int cacheSize = maxMemory / 32;
		iconMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
			@Override
			protected int sizeOf(String key, Bitmap bitmap) {
				return bitmap.getByteCount() / 1024;
			}
		};

		iconDiskCacheDir = new File(getCacheDir(), DISK_CACHE_SUBDIR);
		if (!iconDiskCacheDir.exists()) {
			iconDiskCacheDir.mkdirs();
		}
		appListCacheFile = new File(getCacheDir(), APP_LIST_CACHE_FILENAME);

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
		gridView.setOverScrollMode(View.OVER_SCROLL_NEVER);

		gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (position >= 0 && position < apps.size()) {
					ResolveInfo app = apps.get(position);
					Intent launchIntent = pm.getLaunchIntentForPackage(app.activityInfo.packageName);
					if (launchIntent != null) {
						startActivity(launchIntent);
					}
				}
			}
		});

		appAdapter = new AppAdapter(this);
		gridView.setAdapter(appAdapter);

		setContentView(gridView);

		loadAppsInternal();
		registerAppInstallReceiver();

		new Thread(new Runnable() {
			@Override
			public void run() {
				cleanUpOldDiskCache();
			}
		}).start();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		handler.removeCallbacks(loadAppsRunnable);
		unregisterReceiver(appInstallReceiver);

		if (iconMemoryCache != null) {
			iconMemoryCache.evictAll();
		}
		if (apps != null) {
			apps.clear();
		}

		if (gridView != null) {
			gridView.setAdapter(null);
			gridView = null;
		}
		appAdapter = null;
		pm = null;
	}

	@Override
	public void onTrimMemory(int level) {
		super.onTrimMemory(level);

		if (iconMemoryCache == null)
			return;

		if (level >= TRIM_MEMORY_MODERATE) {
			iconMemoryCache.evictAll();
		} else if (level >= TRIM_MEMORY_BACKGROUND) {
			iconMemoryCache.trimToSize(iconMemoryCache.size() / 2);
		}
	}

	private void registerAppInstallReceiver() {
		appInstallReceiver = new AppInstallReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_PACKAGE_ADDED);
		filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
		filter.addDataScheme("package");
		registerReceiver(appInstallReceiver, filter);
	}

	private void loadAppsInternal() {
		new AsyncTask<Void, Void, List<ResolveInfo>>() {
			@Override
			protected List<ResolveInfo> doInBackground(Void... voids) {
				List<ResolveInfo> cachedApps = loadAppListFromDisk();
				if (cachedApps != null && !cachedApps.isEmpty()) {
					return cachedApps;
				}
				return queryAndCacheApps();
			}

			@Override
			protected void onPostExecute(List<ResolveInfo> result) {
				if (result != null) {
					apps.clear();
					apps.addAll(result);
					if (appAdapter != null) {
						appAdapter.notifyDataSetChanged();
					}
				}
			}
		}.execute();
	}

	private List<ResolveInfo> queryAndCacheApps() {
		Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
		mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		List<ResolveInfo> allApps = pm.queryIntentActivities(mainIntent, 0);
		String selfPackage = getPackageName();

		List<ResolveInfo> currentApps = new ArrayList<>();
		for (ResolveInfo info : allApps) {
			if (!info.activityInfo.packageName.equals(selfPackage)) {
				currentApps.add(info);
			}
		}
		Collections.sort(currentApps, new ResolveInfo.DisplayNameComparator(pm));
		saveAppListToDisk(currentApps);
		return currentApps;
	}

	private void saveAppListToDisk(List<ResolveInfo> appList) {
		ObjectOutputStream oos = null;
		try {
			oos = new ObjectOutputStream(new FileOutputStream(appListCacheFile));
			oos.writeObject(new AppListWrapper(appList));
		} catch (IOException e) {
			appListCacheFile.delete();
		} finally {
			if (oos != null) {
				try {
					oos.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private List<ResolveInfo> loadAppListFromDisk() {
		ObjectInputStream ois = null;
		try {
			if (appListCacheFile.exists()) {
				ois = new ObjectInputStream(new FileInputStream(appListCacheFile));
				AppListWrapper wrapper = (AppListWrapper) ois.readObject();
				return wrapper.getAppList();
			}
		} catch (IOException | ClassNotFoundException e) {
			appListCacheFile.delete();
		} finally {
			if (ois != null) {
				try {
					ois.close();
				} catch (IOException e) {
				}
			}
		}
		return null;
	}

	private static class AppListWrapper implements Serializable {
		private static final long serialVersionUID = 1L;
		private final ArrayList<ResolveInfo> appList;

		public AppListWrapper(List<ResolveInfo> appList) {
			this.appList = new ArrayList<>(appList);
		}

		public List<ResolveInfo> getAppList() {
			return appList;
		}
	}

	private class AppAdapter extends BaseAdapter {
		private final Context context;

		public AppAdapter(Context c) {
			this.context = c;
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
		public boolean hasStableIds() {
			return true;
		}

		@Override
		public long getItemId(int position) {
			if (position >= 0 && position < apps.size()) {
				return apps.get(position).activityInfo.packageName.hashCode();
			}
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
				iconView.setLayoutParams(new LinearLayout.LayoutParams(iconSizePx, iconSizePx));

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

			if (position >= 0 && position < apps.size()) {
				ResolveInfo app = apps.get(position);
				holder.textView.setText(app.loadLabel(pm));

				final String packageName = app.activityInfo.packageName;
				final Bitmap cachedIcon = iconMemoryCache.get(packageName);

				if (cachedIcon != null) {
					holder.iconView.setImageBitmap(cachedIcon);
				} else {
					holder.iconView.setImageDrawable(null);
					loadIcon(app, holder.iconView);
				}
			} else {
				holder.iconView.setImageDrawable(null);
				holder.textView.setText("");
			}
			return convertView;
		}
	}

	private void loadIcon(ResolveInfo appInfo, ImageView imageView) {
		if (cancelPotentialWork(appInfo, imageView)) {
			final IconLoaderTask task = new IconLoaderTask(appInfo, imageView, pm, iconMemoryCache, iconDiskCacheDir);
			final AsyncDrawable asyncDrawable = new AsyncDrawable(task);
			imageView.setImageDrawable(asyncDrawable);
			task.execute();
		}
	}

	private static boolean cancelPotentialWork(ResolveInfo appInfo, ImageView imageView) {
		final IconLoaderTask iconLoaderTask = getIconLoaderTask(imageView);
		if (iconLoaderTask != null) {
			final ResolveInfo taskAppInfo = iconLoaderTask.getAppInfo();
			if (taskAppInfo == null || !taskAppInfo.activityInfo.packageName.equals(appInfo.activityInfo.packageName)) {
				iconLoaderTask.cancel(true);
			} else {
				return false;
			}
		}
		return true;
	}

	private static IconLoaderTask getIconLoaderTask(ImageView imageView) {
		if (imageView != null && imageView.getDrawable() instanceof AsyncDrawable) {
			return ((AsyncDrawable) imageView.getDrawable()).getIconLoaderTask();
		}
		return null;
	}

	class IconLoaderTask extends AsyncTask<Void, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewReference;
		private final ResolveInfo appInfo;
		private final PackageManager packageManager;
		private final LruCache<String, Bitmap> memoryCache;
		private final File diskCacheDir;
		private final String packageName;

		public IconLoaderTask(ResolveInfo appInfo, ImageView imageView, PackageManager pm,
				LruCache<String, Bitmap> memoryCache, File diskCacheDir) {
			this.imageViewReference = new WeakReference<>(imageView);
			this.appInfo = appInfo;
			this.packageManager = pm;
			this.memoryCache = memoryCache;
			this.diskCacheDir = diskCacheDir;
			this.packageName = appInfo.activityInfo.packageName;
		}

		public ResolveInfo getAppInfo() {
			return appInfo;
		}

		@Override
		protected Bitmap doInBackground(Void... params) {
			if (isCancelled())
				return null;

			File cacheFile = new File(diskCacheDir, packageName);
			if (cacheFile.exists()) {
				cacheFile.setLastModified(System.currentTimeMillis());
				Bitmap bitmap = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
				if (bitmap != null) {
					if (!isCancelled()) {
						memoryCache.put(packageName, bitmap);
					}
					return bitmap;
				}
			}

			if (isCancelled())
				return null;

			Drawable icon = appInfo.loadIcon(packageManager);
			Bitmap bitmap = createScaledBitmapFromDrawable(icon, iconSizePx, iconSizePx);

			if (bitmap != null) {
				if (!isCancelled()) {
					saveBitmapToDiskCache(bitmap, cacheFile);
					memoryCache.put(packageName, bitmap);
				}
			}
			return bitmap;
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (isCancelled() || bitmap == null)
				return;

			final ImageView imageView = imageViewReference.get();
			if (imageView != null) {
				final IconLoaderTask iconLoaderTask = getIconLoaderTask(imageView);
				if (this == iconLoaderTask) {
					imageView.setImageBitmap(bitmap);
				}
			}
		}

		private void saveBitmapToDiskCache(Bitmap bitmap, File file) {
			FileOutputStream out = null;
			try {
				out = new FileOutputStream(file);
				bitmap.compress(Bitmap.CompressFormat.PNG, 80, out);
			} catch (IOException e) {
			} finally {
				if (out != null) {
					try {
						out.close();
					} catch (IOException e) {
					}
				}
			}
		}
	}

	static class AsyncDrawable extends BitmapDrawable {
		private final WeakReference<IconLoaderTask> iconLoaderTaskReference;
		public AsyncDrawable(IconLoaderTask iconLoaderTask) {
			super((Bitmap) null);
			this.iconLoaderTaskReference = new WeakReference<>(iconLoaderTask);
		}
		public IconLoaderTask getIconLoaderTask() {
			return iconLoaderTaskReference.get();
		}
	}

	private Bitmap createScaledBitmapFromDrawable(Drawable drawable, int width, int height) {
		if (drawable == null)
			return null;

		Bitmap bitmap;
		if (drawable instanceof BitmapDrawable) {
			bitmap = ((BitmapDrawable) drawable).getBitmap();
		} else {
			int intrinsicWidth = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : width;
			int intrinsicHeight = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : height;

			bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.RGB_565);
			Canvas canvas = new Canvas(bitmap);
			drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
			drawable.draw(canvas);
		}

		if (bitmap.getWidth() == width && bitmap.getHeight() == height) {
			return bitmap;
		}
		return Bitmap.createScaledBitmap(bitmap, width, height, true);
	}

	private static class ViewHolder {
		final ImageView iconView;
		final TextView textView;
		ViewHolder(ImageView iconView, TextView textView) {
			this.iconView = iconView;
			this.textView = textView;
		}
	}

	private int dpToPx(int dp) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
	}

	private void cleanUpOldDiskCache() {
		if (iconDiskCacheDir == null || !iconDiskCacheDir.isDirectory()) {
			return;
		}

		long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(CACHE_EXPIRATION_DAYS);
		File[] files = iconDiskCacheDir.listFiles();

		if (files == null)
			return;

		for (File file : files) {
			if (file.lastModified() < cutoff) {
				file.delete();
			}
		}
	}

	public class AppInstallReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())
					|| Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
				String packageName = intent.getData() != null ? intent.getData().getSchemeSpecificPart() : null;
				if (packageName != null && Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
					iconMemoryCache.remove(packageName);
					new File(iconDiskCacheDir, packageName).delete();
				}

				handler.removeCallbacks(loadAppsRunnable);
				handler.postDelayed(loadAppsRunnable, DEBOUNCE_DELAY_MS);
			}
		}
	}
}

