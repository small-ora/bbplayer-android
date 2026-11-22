package com.bbplayer.android.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbplayer.android.ui.theme.BbIconSize
import com.bbplayer.android.ui.theme.BbType
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/** 一个主 Tab：发现 / 曲库 / 我的。 */
data class MainTab(val label: String, val route: String, val icon: ImageVector)

/**
 * 底部导航栏（需求 §1）：3 个核心 Tab 常驻屏幕底部，Material 3 NavigationBar。
 *
 * 系统手势条 inset 由 [com.bbplayer.android.ui.AppRoot] 的外层 `navigationBarsPadding()` 统一处理，
 * 这里把 [NavigationBar] 自带 windowInsets 关掉，避免 double-inset（见 SPEC 风险 2）。
 */
@Composable
fun BbBottomNavBar(
    tabs: List<MainTab>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = SaltTheme.colors.background,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = tab.route == selectedRoute,
                onClick = { onSelect(tab.route) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(BbIconSize.Lg),
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        style = BbType.Caption,
                        maxLines = 1,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SaltTheme.colors.highlight,
                    selectedTextColor = SaltTheme.colors.highlight,
                    indicatorColor = SaltTheme.colors.highlight.copy(alpha = 0.14f),
                    unselectedIconColor = SaltTheme.colors.subText,
                    unselectedTextColor = SaltTheme.colors.subText,
                ),
            )
        }
    }
}
