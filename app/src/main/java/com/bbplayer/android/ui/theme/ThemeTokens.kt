package com.bbplayer.android.ui.theme

/**
 * v9 中性色 token（P0 部分）。
 *
 * 原则：
 * - 浅色：页面底用浅灰 [LightBackground]，卡片用白面 [LightSurface]（比底色亮 → 读出「浮起」），
 *   12% 黑 hairline [LightStroke] 补边界；[LightSubText] 对白面 ≥ 6:1，禁止再乘 alpha。
 * - 纯白（墨水屏）：底色纯白、卡片略灰（不再与底色相同导致卡片消失）。
 * - 深色：沿用 SaltUI 深色背景层次，只把 hairline 提亮到 12% 白。
 *
 * 数值在这里集中定义，`BbTheme` 与 JVM 单测（ThemeTokensTest）共用同一份常量。
 */
object ThemeTokens {
    // ---- 浅色
    val LightBackground: Long = 0xFFF2F3F5
    val LightSurface: Long = 0xFFFFFFFF
    val LightPopup: Long = 0xFFFFFFFF
    val LightText: Long = 0xFF17181A
    val LightSubText: Long = 0xFF5F6368
    val LightStroke: Long = 0x1F000000 // 12% 黑

    // ---- 纯白（墨水屏）
    val InkBackground: Long = 0xFFFFFFFF
    val InkSurface: Long = 0xFFF4F5F6
    val InkPopup: Long = 0xFFFFFFFF
    val InkText: Long = 0xFF000000
    val InkSubText: Long = 0xFF5F6368
    val InkStroke: Long = 0x1F000000

    // ---- 深色
    val DarkStroke: Long = 0x1FFFFFFF // 12% 白
}

/**
 * 播放页弹层调色板（SPEC AC7）。
 *
 * 沉浸播放页上的弹层默认深灰玻璃；纯白墨水屏播放页改用白面，
 * 避免「纯白主题里弹出一个深灰对话框」的割裂。
 */
object PlayerSheetTokens {
    // 沉浸播放页（深色玻璃）
    val DarkContainer: Long = 0xFF262626
    val DarkOn: Long = 0xFFFFFFFF
    val DarkOnSecondary: Long = 0xCCFFFFFF
    val DarkField: Long = 0x14FFFFFF
    val DarkAccent: Long = 0xFF7FB8FF
    val DarkOnAccent: Long = 0xFF101010

    // 纯白墨水屏（浅色面）
    val InkContainer: Long = 0xFFFFFFFF
    val InkOn: Long = 0xFF111111
    val InkOnSecondary: Long = 0x99000000
    val InkField: Long = 0x0F000000
    val InkAccent: Long = 0xFF0470E6
    val InkOnAccent: Long = 0xFFFFFFFF
}
