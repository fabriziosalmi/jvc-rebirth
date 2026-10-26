package tv.rebirth.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

public final class MainActivity extends Activity
        implements RebirthHomeView.ActionListener, Handler.Callback, DialogInterface.OnClickListener {

    private static final int MSG_REFRESH_STATUS = 1;
    private static final int MSG_DELAYED_CHANNEL_BCAST = 2;
    private static final int MSG_DELAYED_UPDOWN_BCAST = 3;

    private static final ComponentName LIVE_TV_COMPONENT = new ComponentName(
        LauncherModel.LIVE_TV_PKG,
        LauncherModel.LIVE_TV_ACTIVITY
    );

    private LauncherModel model;
    private RebirthHomeView homeView;
    private ActivityManager activityManager;
    private final Handler handler = new Handler(Looper.getMainLooper(), this);
    private final TimeZone timeZone = TimeZone.getDefault();
    private final ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();

    // Pre-allocated reusable Intents for zero-allocation Live TV, DVB-T2 tuning, and HDMI switching
    private final Intent liveTvIntent = new Intent(Intent.ACTION_MAIN)
        .setComponent(LIVE_TV_COMPONENT)
        .putExtra("bootcomplete", true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    private final Intent selectChannelBcast = new Intent(LauncherModel.ACTION_MTK_SELECT_CHANNEL);
    private final Intent channelUpDownBcast = new Intent(LauncherModel.ACTION_MTK_CHANNEL_UPDOWN);
    private final Intent selectSourceBcast = new Intent(LauncherModel.ACTION_MTK_SELECT_SOURCE);

    // Zero-IPC launch Intent cache for the 5 streaming apps + Settings + 3 HDMI URIs
    private final String[] cachedPkgKeys = new String[8];
    private final Intent[] cachedPkgIntents = new Intent[8];
    private int cachedPkgCount = 0;
    private final Uri[] cachedHdmiUris = new Uri[3];

    private long lastTzCheckHour = -1L;
    private int cachedTzOffsetMs = 0;
    private String pendingChannelNum = "";
    private String pendingChannelName = "";
    private boolean pendingChannelUp = true;
    private String[] dialogSortedPkgs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window win = getWindow();
        win.setBackgroundDrawable(null);
        win.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        model = new LauncherModel();
        homeView = new RebirthHomeView(this, model, this);
        setContentView(homeView);
        homeView.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (homeView != null) {
            homeView.ensureBitmapsLoaded();
            homeView.requestFocus();
        }
        handler.removeMessages(MSG_REFRESH_STATUS);
        refreshHeaderTelemetry();
        handler.sendEmptyMessageDelayed(MSG_REFRESH_STATUS, 10000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeMessages(MSG_REFRESH_STATUS);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (homeView != null) {
            homeView.releaseBitmaps();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_UI_HIDDEN && homeView != null) {
            homeView.releaseBitmaps();
        }
    }

    @Override
    public void onBackPressed() {
        launchLiveTv();
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_REFRESH_STATUS:
                refreshHeaderTelemetry();
                handler.sendEmptyMessageDelayed(MSG_REFRESH_STATUS, 10000L);
                return true;
            case MSG_DELAYED_CHANNEL_BCAST:
                sendMtkSelectChannelBroadcast(pendingChannelNum, pendingChannelName);
                return true;
            case MSG_DELAYED_UPDOWN_BCAST:
                sendMtkChannelUpDownBroadcast(pendingChannelUp);
                return true;
        }
        return false;
    }

    private void refreshHeaderTelemetry() {
        if (homeView == null) return;
        long nowMs = System.currentTimeMillis();
        long epochHour = nowMs / 3600000L;
        if (epochHour != lastTzCheckHour) {
            lastTzCheckHour = epochHour;
            cachedTzOffsetMs = timeZone.getOffset(nowMs);
        }
        long localMs = nowMs + cachedTzOffsetMs;
        int totalMinutes = (int) ((localMs / 60000L) % 1440L);
        if (totalMinutes < 0) totalMinutes += 1440;
        int hour = totalMinutes / 60;
        int minute = totalMinutes % 60;

        long freeMb = getAvailableMemoryMb();
        homeView.updateStatus(hour, minute, freeMb);
    }

    private long getAvailableMemoryMb() {
        try {
            if (activityManager != null) {
                activityManager.getMemoryInfo(memoryInfo);
                return memoryInfo.availMem / (1024L * 1024L);
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    @Override
    public void onTileClicked(LauncherModel.Tile tile) {
        if (tile == null) return;
        switch (tile.actionType) {
            case LauncherModel.ACTION_LAUNCH_PACKAGE:
                if (LauncherModel.LIVE_TV_PKG.equals(tile.targetPackageOrInput)) {
                    launchLiveTv();
                } else {
                    launchPackage(tile.targetPackageOrInput);
                }
                break;
            case LauncherModel.ACTION_TUNE_LCN:
                tuneToChannel(tile.lcnBadgeText, tile.title);
                break;
            case LauncherModel.ACTION_SWITCH_HDMI:
                switchToHdmiTile(tile);
                break;
            case LauncherModel.ACTION_FREE_RAM:
                freeBackgroundRam();
                break;
            case LauncherModel.ACTION_OPEN_SETTINGS:
                openSystemSettings();
                break;
        }
    }

    @Override
    public void onTileLongClicked(LauncherModel.Tile tile) {
        if (tile == null) return;
        if (tile.actionType == LauncherModel.ACTION_OPEN_SETTINGS) {
            showAllInstalledAppsDialog();
            return;
        }
        if (tile.actionType == LauncherModel.ACTION_LAUNCH_PACKAGE && tile.targetPackageOrInput.length() > 0) {
            try {
                if (activityManager != null) {
                    activityManager.killBackgroundProcesses(tile.targetPackageOrInput);
                }
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + tile.targetPackageOrInput));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Throwable t) {
                Toast.makeText(this, "Info non disponibile per " + tile.title, Toast.LENGTH_SHORT).show();
            }
        } else {
            freeBackgroundRam();
        }
    }

    @Override
    public void onDirectChannelNumber(int lcn) {
        tuneToChannel(String.valueOf(lcn), "");
    }

    @Override
    public void onLiveTvShortcutKey(int keyCode) {
        launchLiveTv();
        if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            pendingChannelUp = (keyCode == KeyEvent.KEYCODE_CHANNEL_UP);
            sendMtkChannelUpDownBroadcast(pendingChannelUp);
            handler.removeMessages(MSG_DELAYED_UPDOWN_BCAST);
            handler.sendEmptyMessageDelayed(MSG_DELAYED_UPDOWN_BCAST, 350L);
        }
    }

    private void launchLiveTv() {
        try {
            startActivity(liveTvIntent);
        } catch (Throwable t) {
            launchPackage(LauncherModel.LIVE_TV_PKG);
        }
    }

    private Intent findCachedLaunchIntent(String pkg) {
        for (int i = 0; i < cachedPkgCount; i++) {
            if (pkg.equals(cachedPkgKeys[i])) {
                return cachedPkgIntents[i];
            }
        }
        return null;
    }

    private void putCachedLaunchIntent(String pkg, Intent intent) {
        if (cachedPkgCount < cachedPkgKeys.length) {
            cachedPkgKeys[cachedPkgCount] = pkg;
            cachedPkgIntents[cachedPkgCount] = intent;
            cachedPkgCount++;
        }
    }

    private void launchPackage(String pkg) {
        try {
            Intent intent = findCachedLaunchIntent(pkg);
            if (intent == null) {
                PackageManager pm = getPackageManager();
                intent = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (intent == null) {
                    intent = pm.getLaunchIntentForPackage(pkg);
                }
                if (intent == null) {
                    String[] cats = new String[] {
                        Intent.CATEGORY_LEANBACK_LAUNCHER,
                        Intent.CATEGORY_LAUNCHER,
                        null
                    };
                    for (int i = 0; i < cats.length && intent == null; i++) {
                        Intent query = new Intent(Intent.ACTION_MAIN);
                        if (cats[i] != null) {
                            query.addCategory(cats[i]);
                        }
                        query.setPackage(pkg);
                        List<ResolveInfo> list = pm.queryIntentActivities(query, 0);
                        if (list != null && !list.isEmpty()) {
                            ResolveInfo ri = list.get(0);
                            intent = new Intent(Intent.ACTION_MAIN);
                            if (cats[i] != null) {
                                intent.addCategory(cats[i]);
                            }
                            intent.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
                        }
                    }
                }
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    putCachedLaunchIntent(pkg, intent);
                }
            }
            if (intent != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "App non installata: " + pkg, Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Errore avvio: " + pkg, Toast.LENGTH_SHORT).show();
        }
    }

    private void tuneToChannel(String channelNumStr, String channelTitle) {
        pendingChannelNum = (channelNumStr != null) ? channelNumStr : "";
        pendingChannelName = (channelTitle != null) ? channelTitle : "";
        launchLiveTv();
        sendMtkSelectChannelBroadcast(pendingChannelNum, pendingChannelName);
        handler.removeMessages(MSG_DELAYED_CHANNEL_BCAST);
        handler.sendEmptyMessageDelayed(MSG_DELAYED_CHANNEL_BCAST, 350L);
    }

    private void sendMtkSelectChannelBroadcast(String channelNum, String channelName) {
        try {
            selectChannelBcast.putExtra("channelNum", channelNum);
            selectChannelBcast.putExtra("channelName", channelName);
            sendBroadcast(selectChannelBcast);
        } catch (Throwable ignored) {
        }
    }

    private void sendMtkChannelUpDownBroadcast(boolean up) {
        try {
            channelUpDownBcast.putExtra("upOrdown", up);
            sendBroadcast(channelUpDownBcast);
        } catch (Throwable ignored) {
        }
    }

    private void switchToHdmiTile(LauncherModel.Tile tile) {
        try {
            int col = tile.col;
            Uri uri = (col >= 0 && col < cachedHdmiUris.length) ? cachedHdmiUris[col] : null;
            if (uri == null) {
                uri = Uri.parse(tile.passthroughUri);
                if (col >= 0 && col < cachedHdmiUris.length) {
                    cachedHdmiUris[col] = uri;
                }
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setComponent(LIVE_TV_COMPONENT);
            intent.putExtra("deviceName", tile.title);
            intent.putExtra("inputName", tile.targetPackageOrInput);
            intent.putExtra("bootcomplete", true);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            selectSourceBcast.putExtra(LauncherModel.EXTRA_MTK_SOURCE_NAME, tile.mtkSourceName);
            sendBroadcast(selectSourceBcast);
        } catch (Throwable t) {
            launchLiveTv();
        }
    }

    private void showAllInstalledAppsDialog() {
        try {
            PackageManager pm = getPackageManager();
            List<String> labels = new ArrayList<String>(32);
            List<String> pkgs = new ArrayList<String>(32);

            String[] categories = new String[] {
                Intent.CATEGORY_LEANBACK_LAUNCHER,
                Intent.CATEGORY_LAUNCHER
            };
            for (int c = 0; c < categories.length; c++) {
                Intent query = new Intent(Intent.ACTION_MAIN);
                query.addCategory(categories[c]);
                List<ResolveInfo> list = pm.queryIntentActivities(query, 0);
                if (list != null) {
                    for (int i = 0; i < list.size(); i++) {
                        ResolveInfo ri = list.get(i);
                        if (ri == null || ri.activityInfo == null) continue;
                        String pkg = ri.activityInfo.packageName;
                        if (pkg == null || getPackageName().equals(pkg) || pkgs.contains(pkg)) continue;
                        CharSequence cs = ri.loadLabel(pm);
                        String label = (cs != null && cs.length() > 0) ? cs.toString() : pkg;
                        pkgs.add(pkg);
                        labels.add(label + "  (" + pkg + ")");
                    }
                }
            }

            int n = labels.size();
            String[] sortedLabels = labels.toArray(new String[n]);
            String[] sortedPkgs = pkgs.toArray(new String[n]);

            // In-place insertion sort (zero Integer boxing, zero Comparator inner class)
            for (int i = 1; i < n; i++) {
                String curLabel = sortedLabels[i];
                String curPkg = sortedPkgs[i];
                int j = i - 1;
                while (j >= 0 && sortedLabels[j].compareToIgnoreCase(curLabel) > 0) {
                    sortedLabels[j + 1] = sortedLabels[j];
                    sortedPkgs[j + 1] = sortedPkgs[j];
                    j--;
                }
                sortedLabels[j + 1] = curLabel;
                sortedPkgs[j + 1] = curPkg;
            }

            this.dialogSortedPkgs = sortedPkgs;
            new AlertDialog.Builder(this)
                .setTitle("Tutte le App installate (" + n + ")")
                .setItems(sortedLabels, this)
                .setNegativeButton("Chiudi", null)
                .show();
        } catch (Throwable t) {
            openSystemSettings();
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (dialogSortedPkgs != null && which >= 0 && which < dialogSortedPkgs.length) {
            launchPackage(dialogSortedPkgs[which]);
        }
    }

    private void freeBackgroundRam() {
        long beforeMb = getAvailableMemoryMb();
        try {
            if (activityManager != null) {
                for (String pkg : LauncherModel.BACKGROUND_KILL_TARGETS) {
                    activityManager.killBackgroundProcesses(pkg);
                }
            }
            System.gc();
        } catch (Throwable ignored) {
        }
        long afterMb = getAvailableMemoryMb();
        refreshHeaderTelemetry();
        long delta = Math.max(0L, afterMb - beforeMb);
        Toast.makeText(
            this,
            "RAM: " + afterMb + " MB liberi (+" + delta + " MB recuperati)",
            Toast.LENGTH_SHORT
        ).show();
    }

    private void openSystemSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable t) {
            launchPackage("com.android.tv.settings");
        }
    }
}
