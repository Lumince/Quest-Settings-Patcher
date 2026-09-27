package com.lumi.settingspatcher;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String SETTINGS_PKG = "com.oculus.panelapp.settings";
    private static final String VRSHELL_PKG  = "com.oculus.vrshell";

    private static final Set<Integer> SUPPORTED_SETTINGS_VERSIONS = new HashSet<>(Arrays.asList(
            665903155,                                        // v81
            671701082, 671701119,                             // v203 pro
            672201326,                                        // v204
            673301368, 673301462,                             // v205
            674401129, 674401131,                             // v206
            675101053,                                        // v207 Q2+Q3
            675101304, 675101311, 675101221, 675101195, 675101172 // v207 / 3s / Pro
    ));

    private static final Set<Integer> SUPPORTED_VRSHELL_VERSIONS = new HashSet<>(Arrays.asList(
            949708223,                         // v203 Q2/Q3/Pro
            996826886,                         // v204 Q2/Q3/Pro
            998228807,                         // v205 Q2
            1009732165,                        // v205 Q2/Q3/3s/Pro
            1026370745,                        // v206 Pro
            1026630909,                        // v206 Q2/Q3/3s
            1028758766,                        // v207 Q2/Q3
            1051347662, 1051657337,            // v207-latest Q3/Pro, Q2
            1039989429, 1042715439,            // v207 3s, v207 3s
            1037766809                         // v207 Pro
    ));

    private static final int COLOR_BG       = Color.parseColor("#0F0F1A");
    private static final int COLOR_CARD     = Color.parseColor("#1A1A2E");
    private static final int COLOR_ACCENT   = Color.parseColor("#7B68EE");
    private static final int COLOR_SUCCESS  = Color.parseColor("#4CAF50");
    private static final int COLOR_ERROR    = Color.parseColor("#EF5350");
    private static final int COLOR_WARNING  = Color.parseColor("#FF9800");
    private static final int COLOR_TEXT     = Color.WHITE;
    private static final int COLOR_DIM      = Color.parseColor("#9090B0");
    private static final int COLOR_KILL     = Color.parseColor("#C62828");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));

        // ── Device info ─────────────────────────────────────────────────────────

        LinearLayout deviceCard = card(root);
        sectionLabel(deviceCard, "DEVICE INFO");
        row(deviceCard, "Build Incremental", Build.VERSION.INCREMENTAL);
        row(deviceCard, "Android API", String.valueOf(Build.VERSION.SDK_INT));

        // ── Settings app ────────────────────────────────────────────────────────

        AppInfo settingsInfo = getAppInfo(SETTINGS_PKG, SUPPORTED_SETTINGS_VERSIONS);
        LinearLayout settingsCard = card(root);
        sectionLabel(settingsCard, "SETTINGS APP");
        buildAppCard(settingsCard, SETTINGS_PKG, settingsInfo);

        // ── VrShell ─────────────────────────────────────────────────────────────

        AppInfo vrshellInfo = getAppInfo(VRSHELL_PKG, SUPPORTED_VRSHELL_VERSIONS);
        LinearLayout vrshellCard = card(root);
        sectionLabel(vrshellCard, "VR SHELL");
        buildAppCard(vrshellCard, VRSHELL_PKG, vrshellInfo);

        // ── Contact notice ──────────────────────────────────────────────────────

        if ((settingsInfo.installed && !settingsInfo.supported)
                || (vrshellInfo.installed && !vrshellInfo.supported)) {
            TextView notice = new TextView(this);
            notice.setText("Unsupported version detected.\nContact Lumince for support.");
            notice.setTextColor(COLOR_WARNING);
            notice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            notice.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams noticeLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            noticeLp.setMargins(0, dp(4), 0, 0);
            root.addView(notice, noticeLp);
        }

        setContentView(root);
        requestRoot();
    }

    // ── Card content builder ─────────────────────────────────────────────────

    private void buildAppCard(LinearLayout card, String pkg, AppInfo info) {
        if (!info.installed) {
            TextView tv = new TextView(this);
            tv.setText("Not installed");
            tv.setTextColor(COLOR_DIM);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            card.addView(tv);
            return;
        }

        row(card, "Version Code", String.valueOf(info.versionCode));
        row(card, "Version Name", info.versionName != null ? info.versionName : "—");

        TextView statusTv = new TextView(this);
        statusTv.setText(info.supported ? "✓  Supported" : "✗  Unsupported");
        statusTv.setTextColor(info.supported ? COLOR_SUCCESS : COLOR_ERROR);
        statusTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        statusTv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusLp.setMargins(0, dp(10), 0, dp(10));
        card.addView(statusTv, statusLp);

        Button killBtn = new Button(this);
        killBtn.setText("Kill Process");
        killBtn.setTextColor(COLOR_TEXT);
        killBtn.setAllCaps(false);
        killBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        GradientDrawable killBg = new GradientDrawable();
        killBg.setColor(COLOR_KILL);
        killBg.setCornerRadius(dp(8));
        killBtn.setBackground(killBg);
        killBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
        killBtn.setOnClickListener(v -> killWithRoot(pkg));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(0, dp(2), 0, 0);
        card.addView(killBtn, btnLp);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void requestRoot() {
        new Thread(() -> {
            try {
                Process su = Runtime.getRuntime().exec(new String[]{"su", "-c", "echo ok"});
                su.waitFor();
            } catch (Exception ignored) {}
        }).start();
    }

    private void killWithRoot(String pkg) {
        new Thread(() -> {
            try {
                Process su = Runtime.getRuntime().exec(new String[]{"su", "-c",
                        "am force-stop " + pkg});
                int exit = su.waitFor();
                runOnUiThread(() -> {
                    if (exit == 0) {
                        Toast.makeText(this, "Killed " + pkg, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Kill failed (exit " + exit + ")",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Root error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private AppInfo getAppInfo(String pkg, Set<Integer> supportedVersions) {
        AppInfo info = new AppInfo();
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(pkg, 0);
            info.installed   = true;
            info.versionCode = pi.versionCode;
            info.versionName = pi.versionName;
            info.supported   = supportedVersions.contains(pi.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            info.installed = false;
        }
        return info;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        card.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_CARD);
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        parent.addView(card, lp);
        return card;
    }

    private void sectionLabel(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(COLOR_ACCENT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        parent.addView(tv, lp);
    }

    private void row(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(COLOR_DIM);
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        row.addView(labelTv, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextColor(COLOR_TEXT);
        valueTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        valueTv.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(valueTv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dp(5));
        parent.addView(row, rowLp);
    }

    private int dp(int dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics()));
    }

    private static class AppInfo {
        boolean installed;
        int     versionCode;
        String  versionName;
        boolean supported;
    }
}
