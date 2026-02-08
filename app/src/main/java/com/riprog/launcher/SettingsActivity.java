package com.riprog.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SettingsActivity extends Activity {

    private SettingsManager settingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(this);

        FrameLayout rootContainer = new FrameLayout(this);
        rootContainer.setPadding(dpToPx(16), dpToPx(48), dpToPx(16), dpToPx(32));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundResource(R.drawable.glass_bg);
        scrollView.setVerticalScrollBarEnabled(false);
        rootContainer.addView(scrollView);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(24), dpToPx(32), dpToPx(24), dpToPx(32));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("⚙️ Settings");
        title.setTextSize(32);
        title.setTextColor(getColor(R.color.foreground));
        title.setPadding(0, 0, 0, dpToPx(32));
        root.addView(title);

        addSettingItem(root, "🛸 Freeform Home", "Allow free placement of items without grid alignment. Disable to automatically organize home layout.", v -> {
            boolean current = settingsManager.isFreeformHome();
            settingsManager.setFreeformHome(!current);
            recreate();
        });

        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.foreground_dim));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dividerParams.setMargins(0, dpToPx(24), 0, dpToPx(24));
        root.addView(divider, dividerParams);

        TextView aboutTitle = new TextView(this);
        aboutTitle.setText("ℹ️ About");
        aboutTitle.setTextSize(24);
        aboutTitle.setTextColor(getColor(R.color.foreground));
        aboutTitle.setPadding(0, 0, 0, dpToPx(16));
        root.addView(aboutTitle);

        TextView aboutContent = new TextView(this);
        aboutContent.setText("RiProG Launcher v2.1.0\n\n" +
                "Ultra-lightweight Android launcher — minimal, fast, and distraction-free.\n\n" +
                "FEATURES\n" +
                "• Ultra-Lightweight & Fast\n" +
                "• App Drawer with Search & Quick Index\n" +
                "• Gesture Support (Swipe up/down)\n" +
                "• Widget Support\n" +
                "• Liquid Glass UI Consistency\n\n" +
                "LINKS & SUPPORT\n" +
                "GitHub: https://github.com/RiProG-id/RiProG-Launcher-App\n" +
                "Telegram Channel: https://t.me/RiOpSo\n" +
                "Telegram Group: https://t.me/RiOpSoDisc\n" +
                "Support Me (Telegram): https://t.me/RiOpSo/2848\n\n" +
                "DONATE\n" +
                "PayPal: https://paypal.me/RiProG\n" +
                "Sociabuzz: https://sociabuzz.com/riprog/tribe\n" +
                "Dana / GoPay / ShopeePay: 0831-4095-0951\n" +
                "Bank Jago Syariah: 503442488516\n\n" +
                "FREE DONATION\n" +
                "Safelinku: https://sfl.gl/NTX6\n" +
                "Arahlink: https://arahlink.id/WAUR");
        aboutContent.setTextColor(getColor(R.color.foreground_dim));
        aboutContent.setTextSize(14);
        aboutContent.setPadding(0, 0, 0, dpToPx(32));
        Linkify.addLinks(aboutContent, Linkify.WEB_URLS);
        aboutContent.setMovementMethod(LinkMovementMethod.getInstance());
        root.addView(aboutContent);

        setContentView(rootContainer);
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
