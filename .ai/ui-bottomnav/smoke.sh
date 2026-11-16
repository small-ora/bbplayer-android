#!/usr/bin/env bash
# 设备冒烟：安装 debug APK → 逐页截图（证据），供人工验收。
# 前置：adb 已连接 1080x1920 @480dpi 设备；APK 已构建。
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 2
ADB=${ADB:-adb}
PKG=com.bbplayer.android.debug
ACT=$PKG/com.bbplayer.android.MainActivity
OUT=.ai/ui-bottomnav/evidence
mkdir -p "$OUT"

shot() { "$ADB" exec-out screencap -p > "$OUT/$1.png" && echo "shot: $1"; }
dump() { "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && "$ADB" shell cat /sdcard/ui.xml > "$OUT/$1.xml"; }
tapxy() { "$ADB" shell input tap "$1" "$2"; sleep "${3:-1.2}"; }
key() { "$ADB" shell input keyevent "$1"; sleep "${2:-1.0}"; }

echo "== install =="
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk 2>&1 | tail -2
"$ADB" shell am force-stop "$PKG"
"$ADB" shell am start -n "$ACT" >/dev/null 2>&1
sleep 4

echo "== 1. 发现页 =="
dump 01-discover
shot 01-discover

echo "== 2. 曲库页 =="
tapxy 540 1800   # 底部导航第二个 tab（1080 宽三等分）
dump 02-library
shot 02-library

echo "== 3. 我的页 =="
tapxy 900 1800
dump 03-profile
shot 03-profile

echo "== 4. 进入搜索并播放一首 =="
tapxy 540 150   # 顶部 SearchBar
sleep 1
"$ADB" shell input text "周杰伦"
key 66 2        # enter
sleep 3
dump 04-search
shot 04-search
tapxy 540 500   # 第一条结果（播放并进入播放页）
sleep 4
dump 05-player
shot 05-player

echo "== 5. 返回根页（迷你播放器贴底） =="
key 4 2
dump 06-discover-mini
shot 06-discover-mini

echo "== 6. 二级页（设置）：底栏隐藏、迷你播放器下沉 =="
tapxy 900 1800  # 我的
sleep 1
tapxy 1010 130  # 右上设置齿轮
sleep 1.5
dump 07-settings
shot 07-settings
key 4 1.2

echo "== 7. 曲库更多菜单 =="
tapxy 540 1800
sleep 1
dump 08-library
shot 08-library
"$ADB" shell am force-stop "$PKG"
echo "done"
