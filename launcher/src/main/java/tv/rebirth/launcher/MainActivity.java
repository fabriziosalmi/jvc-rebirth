package tv.rebirth.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
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
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity implements RebirthHomeView.ActionListener {

    private static final String[] CHANNEL_PROJECTION = new String[] { "_id", "display_number" };
    private static final Uri CHANNELS_CONTENT_URI = Uri.parse("content://android.media.tv/channel");

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
        launchPackage(LauncherModel.LIVE_TV_PKG);
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
                launchPackage(tile.targetPackageOrInput);
                break;
            case LauncherModel.ACTION_TUNE_LCN:
                tuneToChannelLcn(tile.lcn);
                break;
            case LauncherModel.ACTION_SWITCH_HDMI:
                switchToHdmiUri(tile.passthroughUri);
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
        if (tile.actionType == LauncherModel.ACTION_LAUNCH_PACKAGE && tile.targetPackageOrInput.length() > 0) {
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    am.killBackgroundProcesses(tile.targetPackageOrInput);
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
        tuneToChannelLcn(lcn);
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

    private void tuneToChannelLcn(int lcn) {
        try {
            long channelId = resolveChannelIdForLcn(lcn);
            if (channelId > 0L) {
                Uri channelUri = Uri.parse(LauncherModel.buildChannelUri(channelId));
                Intent intent = new Intent(Intent.ACTION_VIEW, channelUri);
                intent.setPackage(LauncherModel.LIVE_TV_PKG);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            }
        } catch (Throwable ignored) {
        }
        launchPackage(LauncherModel.LIVE_TV_PKG);
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

    private void switchToHdmiUri(String passthroughUri) {
        try {
            Uri uri = Uri.parse(passthroughUri);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable t) {
            launchPackage(LauncherModel.LIVE_TV_PKG);
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
