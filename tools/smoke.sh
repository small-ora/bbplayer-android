#!/usr/bin/env bash
# 真机冒烟：安装 → 启动 → 逐屏截图 → 抓 FATAL。用法: .ai/scripts/smoke.sh [outdir]
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT=${1:-.ai/evidence/smoke}
PKG=com.bbplayer.android.debug
ACT=$PKG/com.bbplayer.android.MainActivity
mkdir -p "$OUT"
UI="python3 .ai/scripts/ui.py"

step() { echo "== $*"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "   screenshot $OUT/$1.png"; }

step "clear logcat"; adb logcat -c
step "install"; adb install -r app/build/outputs/apk/debug/app-debug.apk 2>&1 | tail -1
step "launch"; adb shell am force-stop "$PKG"; adb shell am start -n "$ACT" >/dev/null
sleep 4; shot 01-home

step "search tab"; $UI tap desc "搜索" || exit 2; sleep 1.5; shot 02-search

step "type query"; $UI tap text "搜索音乐 / BV / 链接" 2>/dev/null || true
adb shell input text "BV1auGwzMEs1"; sleep 1
$UI key 66; sleep 2.5; shot 03-search-results

step "tap first result"; $UI tap contains "モア" || $UI tap contains "ジャンプ" || true
sleep 2.5; shot 04-player

step "player lyrics"; $UI tap desc "歌词" || true; sleep 2; shot 05-lyrics
step "back"; $UI tap desc "收起" || true; sleep 1

step "library tab"; $UI tap desc "音乐库" || true; sleep 1.5; shot 06-library
step "profile tab"; $UI tap desc "我的" || true; sleep 1.5; shot 07-profile
step "settings"; $UI tap text "设置" || true; sleep 1.5; shot 08-settings

step "fatal check"; adb logcat -d | grep -E 'FATAL EXCEPTION|AndroidRuntime: Process' | head -20 | tee "$OUT/fatal.txt" || true
echo "== done: $OUT"
