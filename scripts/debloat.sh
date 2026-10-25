#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <TV_IP[:PORT]> [--rebirth]"
  echo "  Default   : Installs general-purpose Projectivy Launcher (com.spocky.projengmenu)"
  echo "  --rebirth : Installs ultra-lightweight Rebirth Launcher (tv.rebirth.launcher, 65.6 KB, Italian DVB-T2 preset)"
  exit 1
fi
TV_ADDR="$1"
LAUNCHER_MODE="${2:-projectivy}"
if [[ "$TV_ADDR" != *:* ]]; then
  TV_ADDR="${TV_ADDR}:5555"
fi
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APKS_DIR="$SCRIPT_DIR/../apks"
mkdir -p "$APKS_DIR"

adb connect "$TV_ADDR"

if [[ "$LAUNCHER_MODE" == "--rebirth" ]]; then
  echo "[1/6] Ensuring Rebirth Launcher (tv.rebirth.launcher) is installed and set as default HOME..."
  REBIRTH_APK="$APKS_DIR/RebirthLauncher-1.0.0.apk"
  if [[ ! -f "$REBIRTH_APK" ]]; then
    echo "  -> Downloading RebirthLauncher-1.0.0.apk from GitHub release..."
    curl -fL "https://github.com/fabriziosalmi/jvc-rebirth/releases/download/v1.0.0/RebirthLauncher-1.0.0.apk" -o "$REBIRTH_APK"
  fi
  adb -s "$TV_ADDR" install -r "$REBIRTH_APK"
  if ! adb -s "$TV_ADDR" shell "pm list packages tv.rebirth.launcher" | grep -q "tv.rebirth.launcher"; then
    echo "[!] ERROR: Rebirth Launcher (tv.rebirth.launcher) is not installed. Aborting before disabling stock launcher."
    exit 1
  fi
  adb -s "$TV_ADDR" shell "pm enable tv.rebirth.launcher 2>/dev/null" || true
  adb -s "$TV_ADDR" shell "cmd package set-home-activity tv.rebirth.launcher/.MainActivity" || true
  adb -s "$TV_ADDR" shell "pm disable-user --user 0 com.spocky.projengmenu 2>/dev/null; am force-stop com.spocky.projengmenu 2>/dev/null" || true
  ACTIVE_HOME_COMPONENT="tv.rebirth.launcher/.MainActivity"
else
  echo "[1/6] Ensuring Projectivy Launcher (default) is installed and set as default HOME..."
  if ! adb -s "$TV_ADDR" shell "pm list packages com.spocky.projengmenu" | grep -q "com.spocky.projengmenu"; then
    LAUNCHER_APK="$(find "$APKS_DIR" -maxdepth 1 -name "ProjectivyLauncher*.apk" | head -n 1 || true)"
    if [[ -z "$LAUNCHER_APK" ]]; then
      echo "  -> Downloading Projectivy Launcher APK from official GitHub release..."
      LAUNCHER_URL="$(curl -fsSL https://api.github.com/repos/spocky/miproja1/releases/latest | grep -Eo 'https://[^"]+\.apk' | head -n 1)"
      LAUNCHER_APK="$APKS_DIR/ProjectivyLauncher-latest.apk"
      curl -fL "$LAUNCHER_URL" -o "$LAUNCHER_APK"
    fi
    adb -s "$TV_ADDR" install -r "$LAUNCHER_APK"
  fi
  adb -s "$TV_ADDR" shell "pm enable com.spocky.projengmenu 2>/dev/null" || true
  if ! adb -s "$TV_ADDR" shell "pm list packages com.spocky.projengmenu" | grep -q "com.spocky.projengmenu"; then
    echo "[!] ERROR: Projectivy Launcher (com.spocky.projengmenu) is not installed. Aborting before disabling stock launcher."
    exit 1
  fi
  adb -s "$TV_ADDR" shell "cmd package set-home-activity com.spocky.projengmenu/com.spocky.projengmenu.ui.home.MainActivity" || true
  adb -s "$TV_ADDR" shell "pm disable-user --user 0 tv.rebirth.launcher 2>/dev/null; am force-stop tv.rebirth.launcher 2>/dev/null" || true
  ACTIVE_HOME_COMPONENT="com.spocky.projengmenu/com.spocky.projengmenu.ui.home.MainActivity"
fi

if ! adb -s "$TV_ADDR" shell "pm list packages it.rainet.androidtv" | grep -q "it.rainet.androidtv"; then
  if [[ -f "$APKS_DIR/raiplay/it.rainet.androidtv.apk" ]]; then
    adb -s "$TV_ADDR" install-multiple \
      "$APKS_DIR/raiplay/it.rainet.androidtv.apk" \
      "$APKS_DIR/raiplay/config.armeabi_v7a.apk" \
      "$APKS_DIR/raiplay/config.it.apk" \
      "$APKS_DIR/raiplay/config.xhdpi.apk"
  fi
fi

if ! adb -s "$TV_ADDR" shell "pm list packages com.dazn" | grep -q "com.dazn"; then
  if [[ -f "$APKS_DIR/dazn/com.dazn.apk" ]]; then
    adb -s "$TV_ADDR" install-multiple \
      "$APKS_DIR/dazn/com.dazn.apk" \
      "$APKS_DIR/dazn/config.it.apk" \
      "$APKS_DIR/dazn/config.en.apk" \
      "$APKS_DIR/dazn/config.xhdpi.apk"
  fi
fi

echo "[2/6] Disabling boot-verified bloatware packages..."
# CRITICAL NOTE: Never disable FusedLocation, PacProcessor, OsuLogin, SecureElement,
# Bluetooth, SetupWraith, OneTimeInitializer, PartnerSetup, SetupWizard, Factory, ServicePatch, or BacklightService.
# Disabling any of those causes system_server to crash during boot and triggers Android RescueParty.
adb -s "$TV_ADDR" shell '
for pkg in \
  com.google.android.tvlauncher \
  com.google.android.tvrecommendations \
  com.google.android.leanbacklauncher.partnercustomizer \
  com.google.android.katniss \
  com.vestel.vestelreporter \
  fusion.android.tv.demo \
  com.cltv.fast \
  com.mediatek.tv.freeviewplay \
  com.google.android.videos \
  com.google.android.play.games \
  com.google.android.youtube.tvmusic \
  com.google.android.apps.nbu.smartconnect.tv \
  com.google.android.feedback \
  com.android.printspooler \
  com.google.android.backdrop \
  com.android.dreams.basic \
  com.google.android.syncadapters.calendar \
  com.android.providers.calendar \
  com.android.providers.contacts \
  com.android.providers.userdictionary \
  com.android.deskclock \
  com.android.vending \
  com.google.android.tv.remote.service \
  com.android.settings.intelligence \
  com.vestel.tv.eib \
  com.mstar.android.tv.disclaimercustomization; do
  echo "  -> disable-user $pkg"
  pm disable-user --user 0 "$pkg" 2>/dev/null || true
  am force-stop "$pkg" 2>/dev/null || true
done

pm uninstall -k --user 0 com.google.android.marvin.talkback 2>/dev/null || true
pm uninstall --user 0 com.spotify.tv.android 2>/dev/null || pm disable-user --user 0 com.spotify.tv.android 2>/dev/null || true
pm uninstall --user 0 com.netflix.ninja 2>/dev/null || pm disable-user --user 0 com.netflix.ninja 2>/dev/null || true
'

echo "[3/6] Restricting background execution for streaming apps and Play Store..."
adb -s "$TV_ADDR" shell '
for pkg in \
  com.android.vending \
  com.google.android.tts \
  com.google.android.tv.remote.service \
  com.disney.disneyplus \
  com.amazon.amazonvideo.livingroom \
  com.dazn \
  it.rainet.androidtv \
  com.google.android.youtube.tv; do
  if pm path "$pkg" >/dev/null 2>&1; then
    echo "  -> restrict background $pkg"
    cmd appops set "$pkg" RUN_IN_BACKGROUND ignore 2>/dev/null || true
    cmd appops set "$pkg" RUN_ANY_IN_BACKGROUND ignore 2>/dev/null || true
    cmd appops set "$pkg" START_FOREGROUND ignore 2>/dev/null || true
    am set-standby-bucket "$pkg" rare 2>/dev/null || true
    am set-inactive "$pkg" true 2>/dev/null || true
    am force-stop "$pkg" 2>/dev/null || true
  fi
done
cmd appops set com.android.vending WAKE_LOCK ignore 2>/dev/null || true
'

echo "[4/6] Applying ActivityManager, WebView, VM, GPU, Backlight 50Hz fix, and video filter tuning..."
adb -s "$TV_ADDR" shell '
device_config set_sync_disabled_for_tests persistent 2>/dev/null || true
device_config put activity_manager max_cached_processes 4 2>/dev/null || true
device_config put activity_manager use_compaction true 2>/dev/null || true
device_config put runtime_native_boot disable_lock_profiling true 2>/dev/null || true
settings put global activity_manager_constants max_cached_processes=4,background_settle_time=10000,content_provider_retain_time=5000,use_compaction=true
cmd webviewupdate disable-multiprocess 2>/dev/null || true
setprop debug.hwui.render_dirty_regions true 2>/dev/null || true
for tag in MtkTvInputSource_jni TV_MtkTvConfig MtkTvChannelList MtkTvChannelList_jni MtkTvBroadcast MtkTvBroadcast_jni MtkATVScan_jni MTK_KL; do
  setprop "log.tag.$tag" S 2>/dev/null || true
  setprop "persist.log.tag.$tag" S 2>/dev/null || true
done
logcat -G 64K 2>/dev/null || true
logcat -c 2>/dev/null || true
settings put secure enabled_notification_assistant ""
settings put secure autofill_service ""
settings put secure screensaver_enabled 0
settings put secure accessibility_enabled 0
settings put secure enabled_accessibility_services ""
settings put secure enabled_notification_listeners ""
am force-stop com.google.android.ext.services 2>/dev/null || true
settings put system sound_effects_enabled 0
settings put system haptic_feedback_enabled 0
settings put secure long_press_timeout 300
settings put global network_scoring_ui_enabled 0
settings put global wifi_networks_available_notification_on 0
settings put global package_verifier_user_consent 0
settings put secure send_action_app_error 0
settings put global wifi_sleep_policy 2
settings put global wifi_scan_always_enabled 0
for k in \
  picture_auto_backlight \
  last_live_tv_backlight_mode \
  key_picture_mode_standard \
  key_picture_mode_others_auto_backlight \
  key_picture_mode_energy_saving \
  key_picture_mode_game \
  key_picture_mode_movie \
  key_picture_mode_sport \
  key_picture_mode_vivid \
  key_picture_mode_user \
  key_picture_mode_standard_auto_backlight \
  key_picture_mode_energy_saving_auto_backlight \
  key_picture_mode_game_auto_backlight \
  key_picture_mode_movie_auto_backlight \
  key_picture_mode_sport_auto_backlight \
  key_picture_mode_vivid_auto_backlight \
  key_picture_mode_user_auto_backlight \
  tv_picture_advance_video_dnr \
  tv_picture_advance_video_mpeg_nr \
  tv_picture_advance_video_mjc_effect \
  tv_picture_video_noise_reduction; do
  settings put global "$k" 0
done
settings put global window_animation_scale 0.0
settings put global transition_animation_scale 0.0
settings put global animator_duration_scale 0.0
'

echo "[5/6] Compiling Live TV stack, System WebView, Settings, Launcher, and apps with 100% native AOT (-m speed)..."
adb -s "$TV_ADDR" shell '
for pkg in \
  com.mediatek.wwtv.tvcenter \
  com.mediatek.tvinput \
  com.mediatek.tv.service \
  com.android.providers.tv \
  com.vestel.tv.keycontroller \
  com.vestel.tv.service \
  com.vestel.tv.backlight \
  tv.rebirth.launcher \
  com.spocky.projengmenu \
  com.android.systemui \
  com.android.tv.settings \
  com.google.android.webview \
  it.rainet.androidtv \
  com.dazn \
  com.disney.disneyplus \
  com.amazon.amazonvideo.livingroom \
  com.google.android.youtube.tv; do
  if pm path "$pkg" >/dev/null 2>&1; then
    echo -n "  -> compile -m speed $pkg: "
    cmd package compile -m speed "$pkg" || true
  fi
done
'

echo "[6/6] Trimming caches, running eMMC fstrim, and launching active HOME ($ACTIVE_HOME_COMPONENT)..."
adb -s "$TV_ADDR" shell "pm trim-caches 999999999999 && sm fstrim" || true
adb -s "$TV_ADDR" shell "am start -n $ACTIVE_HOME_COMPONENT"
echo "[+] Done."
