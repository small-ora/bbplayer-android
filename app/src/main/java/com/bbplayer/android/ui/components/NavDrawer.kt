package com.bbplayer.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/**
 * Opens the navigation drawer from a page header. Set to null (e.g. wide screens with a permanent
 * rail) so [LargeTitle] hides the hamburger automatically.
 */
val LocalOpenMenu = staticCompositionLocalOf<(() -> Unit)?> { null }

/** One primary (tab) destination shown both in the drawer and in the wide-screen rail. */
data class NavTab(val label: String, val icon: ImageVector)

/** One pushed destination shown under the drawer's "更多" section. */
data class NavEntry(val label: String, val icon: ImageVector, val route: String)

/**
 * Salt Player style side menu: a rounded sheet sliding in from the left, opened from the
 * hamburger in the page header (top-left corner). Navigation only — the bottom bar is reserved
 * for music transport controls ([BottomPlayerBar]).
 *
 * [MERGE-HOOK:v5-account] 登录体系落地后，在 header 与「导航」之间插入账号头部
 * （头像/昵称/登录按钮）。不要改动本文件其余结构，避免与 v5 分支产生冲突。
 */
@Composable
fun AppDrawerSheet(
    drawerState: DrawerState,
    tabs: List<NavTab>,
    selectedIndex: Int,
    onSelectTab: (Int) -> Unit,
    secondary: List<NavEntry>,
    onOpenSecondary: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Passing [drawerState] installs the predictive-back handler, so the system back button
    // closes the drawer instead of popping the NavHost underneath.
    ModalDrawerSheet(
        drawerState = drawerState,
        modifier = modifier.width(304.dp),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = SaltTheme.colors.popup,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            DrawerHeader()
            Spacer(Modifier.height(18.dp))
            DrawerSectionLabel("导航")
            tabs.forEachIndexed { index, tab ->
                DrawerItem(
                    label = tab.label,
                    icon = tab.icon,
                    selected = index == selectedIndex,
                    onClick = { onSelectTab(index) },
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(SaltTheme.colors.stroke)
            )
            Spacer(Modifier.height(10.dp))
            DrawerSectionLabel("更多")
            secondary.forEach { entry ->
                DrawerItem(
                    label = entry.label,
                    icon = entry.icon,
                    selected = false,
                    onClick = { onOpenSecondary(entry.route) },
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DrawerHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 22.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SaltTheme.colors.highlight.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = SaltTheme.colors.highlight,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("BBPlayer", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text("B 站音乐 · 纯净播放", color = SaltTheme.colors.subText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        color = SaltTheme.colors.subText.copy(alpha = 0.72f),
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) SaltTheme.colors.highlight.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) SaltTheme.colors.highlight else SaltTheme.colors.subText,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            color = if (selected) SaltTheme.colors.highlight else SaltTheme.colors.text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
