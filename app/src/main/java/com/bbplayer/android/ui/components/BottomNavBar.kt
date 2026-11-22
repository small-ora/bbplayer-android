package com.bbplayer.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.bbplayer.android.ui.theme.BbIconSize
import com.bbplayer.android.ui.theme.BbRadius
import com.bbplayer.android.ui.theme.BbTokens
import com.moriafly.salt.ui.SaltTheme

/** 一个主 Tab：发现 / 曲库 / 我的。 */
data class MainTab(val label: String, val route: String, val icon: ImageVector)

/**
 * 底部 dock 的导航段（v9.1 P1-3）：icon-only、无 M3 pill、无 label。
 *
 * - 选中：图标用 `text` 实色（100%）；未选中：`subText` 45%（导航图标非正文，不违反 P0 文本规则）
 * - 触控热区 48dp，图标视觉尺寸 24dp（[BbIconSize] 单一事实源）
 * - dock 的圆角/底色/阴影/inset 由 `AppRoot` 的 dock 容器统一提供
 */
@Composable
fun BbDockNav(
    tabs: List<MainTab>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BbTokens.DockNavHeight),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == selectedRoute
            Box(
                modifier = Modifier
                    .size(BbIconSize.Touch)
                    .clip(BbRadius.FullShape)
                    .clickable { onSelect(tab.route) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) {
                        SaltTheme.colors.text
                    } else {
                        SaltTheme.colors.subText.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.size(BbIconSize.Lg),
                )
            }
        }
    }
}
