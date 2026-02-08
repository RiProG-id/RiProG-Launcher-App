package com.riprog.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SettingsActivity extends Activity {

    private SettingsManager settingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(this);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundResource(R.color.background);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(24), dpToPx(48), dpToPx(24), dpToPx(24));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextSize(32);
        title.setTextColor(getColor(R.color.foreground));
        title.setPadding(0, 0, 0, dpToPx(32));
        root.addView(title);

        addSettingItem(root, "Grid Columns", "Change number of columns in app drawer", v -> {
            String[] options = {"4 Columns", "5 Columns", "6 Columns"};
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Select Columns")
                .setItems(options, (dialog, which) -> {
                    settingsManager.setColumns(which + 4);
                })
                .show();
        });

        addSettingItem(root, "Add Widget", "Choose a widget for home screen", v -> {
            setResult(Activity.RESULT_OK, getIntent().putExtra("action", "pick_widget"));
            finish();
        });

        addSettingItem(root, "Remove Widget", "Remove current home screen widget", v -> {
            setResult(Activity.RESULT_OK, getIntent().putExtra("action", "remove_widget"));
            finish();
        });

        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.foreground_dim));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dividerParams.setMargins(0, dpToPx(24), 0, dpToPx(24));
        root.addView(divider, dividerParams);

        TextView aboutTitle = new TextView(this);
        aboutTitle.setText("About");
        aboutTitle.setTextSize(24);
        aboutTitle.setTextColor(getColor(R.color.foreground));
        aboutTitle.setPadding(0, 0, 0, dpToPx(16));
        root.addView(aboutTitle);

        TextView aboutContent = new TextView(this);
        aboutContent.setText("RiProG Launcher\n\nUltra-lightweight Android launcher — minimal, fast, and distraction-free.\n\n" +
                "TECHNICAL DETAILS\n" +
                "Min SDK: 23 (Android 6.0)\n" +
                "Target SDK: 34 (Android 14)\n" +
                "Language: Java 17\n\n" +
                "DONATE / SUPPORT\n" +
                "Dana / GoPay: 0831-4095-0951\n" +
                "Bank Jago: 503442488516\n" +
                "PayPal: paypal.me/RiProG\n\n" +
                "LINKS\n" +
                "GitHub: RiProG-id/RiProG-Launcher-App\n" +
                "Support: t.me/RiOpSo");
        aboutContent.setTextColor(getColor(R.color.foreground_dim));
        aboutContent.setTextSize(14);
        aboutContent.setPadding(0, 0, 0, dpToPx(32));
        root.addView(aboutContent);

        setContentView(scrollView);
    }

    private void addSettingItem(LinearLayout parent, String title, String summary, View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(0, dpToPx(16), 0, dpToPx(16));
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(listener);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(18);
        titleView.setTextColor(getColor(R.color.foreground));
        item.addView(titleView);

        TextView summaryView = new TextView(this);
        summaryView.setText(summary);
        summaryView.setTextSize(14);
        summaryView.setTextColor(getColor(R.color.foreground_dim));
        item.addView(summaryView);

        parent.addView(item);
    }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
