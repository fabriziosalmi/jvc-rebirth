#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <TV_IP[:PORT]> (e.g., $0 192.168.1.100:5555)"
  exit 1
fi
TV_ADDR="$1"
if [[ "$TV_ADDR" != *:* ]]; then
  TV_ADDR="${TV_ADDR}:5555"
fi
adb connect "$TV_ADDR"

echo "[*] Restoring stock packages and settings on $TV_ADDR..."
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
  com.google.android.youtube.tv \
  com.google.android.tts \
  com.google.android.tungsten.setupwraith \
  com.google.android.tv.remote.service \
  com.android.settings.intelligence \
  com.vestel.tv.eib \
  com.mstar.android.tv.disclaimercustomization \
  com.vestel.tv.speedup \
  android.autoinstalls.config.vestel.cosmos; do
  pm enable "$pkg" 2>/dev/null || true
done

settings put global window_animation_scale 1.0
settings put global transition_animation_scale 1.0
settings put global animator_duration_scale 1.0
settings put global private_dns_mode opportunistic
cmd package set-home-activity com.google.android.tvlauncher/com.google.android.tvlauncher.MainActivity 2>/dev/null || true
'
echo "[+] Stock restore completed."
