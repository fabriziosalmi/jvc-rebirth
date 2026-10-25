package tv.rebirth.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.SparseLongArray;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity implements RebirthHomeView.ActionListener {

    private static final String[] CHANNEL_PROJECTION = new String[] { "_id", "display_number" };
    private static final Uri CHANNELS_CONTENT_URI = Uri.parse("content://android.media.tv/channel");
    private static final ComponentName LIVE_TV_COMPONENT = new ComponentName(
        LauncherModel.LIVE_TV_PKG,
        LauncherModel.LIVE_TV_ACTIVITY
    );

    private LauncherModel model;
    private RebirthHomeView homeView;
    private ActivityManager activityManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.ROOT);
    private final Date reusableDate = new Date();
    private final ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
    private final SparseLongArray lcnToChannelIdCache = new SparseLongArray(64);
    private boolean channelsPreloaded = false;
    private long lastClockMinute = -1L;
    private String cachedClockText = "20:15";

    private final Runnable statusRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshHeaderTelemetry();
            handler.postDelayed(this, 10000L);
        }
    };

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
        handler.removeCallbacks(statusRefreshRunnable);
        statusRefreshRunnable.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(statusRefreshRunnable);
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

    private void refreshHeaderTelemetry() {
        if (homeView == null) return;
        long nowMs = System.currentTimeMillis();
        long currentMinute = nowMs / 60000L;
        if (currentMinute != lastClockMinute) {
            lastClockMinute = currentMinute;
            reusableDate.setTime(nowMs);
            cachedClockText = timeFormat.format(reusableDate);
        }
        long freeMb = getAvailableMemoryMb();
        homeView.updateStatus(cachedClockText, freeMb);
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
                tuneToChannelLcn(tile.lcn, tile.title);
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
        tuneToChannelLcn(lcn, "");
    }

    @Override
    public void onLiveTvShortcutKey(final int keyCode) {
        launchLiveTv();
        if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            final boolean isUp = (keyCode == KeyEvent.KEYCODE_CHANNEL_UP);
            sendMtkChannelUpDownBroadcast(isUp);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    sendMtkChannelUpDownBroadcast(isUp);
                }
            }, 350L);
        }
    }

    private void launchLiveTv() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(LIVE_TV_COMPONENT);
            intent.putExtra("bootcomplete", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable t) {
            launchPackage(LauncherModel.LIVE_TV_PKG);
        }
    }

    private void launchPackage(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) {
                intent = pm.getLaunchIntentForPackage(pkg);
            }
            if (intent == null) {
                Intent query = new Intent(Intent.ACTION_MAIN);
                query.setPackage(pkg);
                List<ResolveInfo> list = pm.queryIntentActivities(query, 0);
                if (list != null && !list.isEmpty()) {
                    ResolveInfo ri = list.get(0);
                    intent = new Intent(Intent.ACTION_MAIN);
                    intent.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
                }
            }
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else {
                Toast.makeText(this, "App non installata: " + pkg, Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Errore avvio: " + pkg, Toast.LENGTH_SHORT).show();
        }
    }

    private void tuneToChannelLcn(final int lcn, final String channelTitle) {
        final String numStr = String.valueOf(lcn);
        final String safeName = (channelTitle != null) ? channelTitle : "";
        boolean launchedWithUri = false;
        try {
            long channelId = resolveChannelIdForLcn(lcn);
            if (channelId > 0L) {
                Uri channelUri = Uri.parse(LauncherModel.buildChannelUri(channelId));
                Intent intent = new Intent(Intent.ACTION_VIEW, channelUri);
                intent.setComponent(LIVE_TV_COMPONENT);
                intent.putExtra("bootcomplete", true);
                intent.setData(channelUri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                launchedWithUri = true;
            }
        } catch (Throwable ignored) {
        }

        if (!launchedWithUri) {
            launchLiveTv();
        }

        // Also send MediaTek TurnkeyReceiver$6 broadcast ("com.mediatek.tv.selectchannel")
        // so SVL/TIFChannelManager tunes even when Android tv.db is empty
        sendMtkSelectChannelBroadcast(numStr, safeName);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendMtkSelectChannelBroadcast(numStr, safeName);
            }
        }, 350L);
    }

    private void sendMtkSelectChannelBroadcast(String channelNum, String channelName) {
        try {
            Intent bcast = new Intent(LauncherModel.ACTION_MTK_SELECT_CHANNEL);
            bcast.putExtra("channelNum", channelNum);
            bcast.putExtra("channelName", channelName);
            sendBroadcast(bcast);
        } catch (Throwable ignored) {
        }
    }

    private void sendMtkChannelUpDownBroadcast(boolean up) {
        try {
            Intent bcast = new Intent(LauncherModel.ACTION_MTK_CHANNEL_UPDOWN);
            bcast.putExtra("upOrdown", up);
            sendBroadcast(bcast);
        } catch (Throwable ignored) {
        }
    }

    private long resolveChannelIdForLcn(int lcn) {
        long cached = lcnToChannelIdCache.get(lcn, -1L);
        if (cached > 0L) {
            return cached;
        }
        if (!channelsPreloaded) {
            preloadAllChannelIds();
            cached = lcnToChannelIdCache.get(lcn, -1L);
            if (cached > 0L) {
                return cached;
            }
        }
        return -1L;
    }

    private void preloadAllChannelIds() {
        channelsPreloaded = true;
        Cursor cursor = null;
        try {
            ContentResolver cr = getContentResolver();
            cursor = cr.query(
                CHANNELS_CONTENT_URI,
                CHANNEL_PROJECTION,
                null,
                null,
                "_id ASC"
            );
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String disp = cursor.getString(1);
                    if (disp != null && disp.length() > 0) {
                        try {
                            int num = Integer.parseInt(disp.trim());
                            if (num > 0 && lcnToChannelIdCache.get(num, -1L) < 0L) {
                                lcnToChannelIdCache.put(num, id);
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void switchToHdmiTile(LauncherModel.Tile tile) {
        try {
            Uri uri = Uri.parse(tile.passthroughUri);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setComponent(LIVE_TV_COMPONENT);
            intent.putExtra("deviceName", tile.title);
            intent.putExtra("inputName", tile.targetPackageOrInput);
            intent.putExtra("bootcomplete", true);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            // Also send MediaTek TurnkeyReceiver$4 source broadcast as backup
            Intent srcBcast = new Intent(LauncherModel.ACTION_MTK_SELECT_SOURCE);
            srcBcast.putExtra(LauncherModel.EXTRA_MTK_SOURCE_NAME, tile.title.replace(" ", ""));
            sendBroadcast(srcBcast);
        } catch (Throwable t) {
            launchLiveTv();
        }
    }

    private void showAllInstalledAppsDialog() {
        try {
            final PackageManager pm = getPackageManager();
            final List<String> labels = new ArrayList<String>(32);
            final List<String> pkgs = new ArrayList<String>(32);

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

            // Sort alphabetically by label
            final Integer[] order = new Integer[labels.size()];
            for (int i = 0; i < order.length; i++) order[i] = Integer.valueOf(i);
            java.util.Arrays.sort(order, new Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    return labels.get(a.intValue()).compareToIgnoreCase(labels.get(b.intValue()));
                }
            });

            final String[] sortedLabels = new String[order.length];
            final String[] sortedPkgs = new String[order.length];
            for (int i = 0; i < order.length; i++) {
                int idx = order[i].intValue();
                sortedLabels[i] = labels.get(idx);
                sortedPkgs[i] = pkgs.get(idx);
            }

            new AlertDialog.Builder(this)
                .setTitle("Tutte le App installate (" + sortedLabels.length + ")")
                .setItems(sortedLabels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which >= 0 && which < sortedPkgs.length) {
                            launchPackage(sortedPkgs[which]);
                        }
                    }
                })
                .setNegativeButton("Chiudi", null)
                .show();
        } catch (Throwable t) {
            openSystemSettings();
        }
    }

    private void freeBackgroundRam() {
        long beforeMb = getAvailableMemoryMb();
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                for (String pkg : LauncherModel.BACKGROUND_KILL_TARGETS) {
                    am.killBackgroundProcesses(pkg);
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
