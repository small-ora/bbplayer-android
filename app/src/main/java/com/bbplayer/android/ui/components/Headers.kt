package com.bbplayer.android.ui.components

import com.bbplayer.android.ui.theme.BbType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/**
 * 页面一级标题（需求 §6：20sp Bold，辅助说明 12sp 60% 灰）。
 *
 * 侧边抽屉已废弃：不再有汉堡按钮，`actions` 只放页面自己的功能入口
 * （如「我的」右上角的设置齿轮）。
 */
@Composable
fun LargeTitle(title: String, subtitle: String? = null, actions: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = SaltTheme.dimens.padding,
                end = SaltTheme.dimens.padding,
                top = 14.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = SaltTheme.textStyles.largeTitle)
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    color = SaltTheme.colors.subText,
                    style = BbType.Caption,
                )
            }
        }
        actions?.invoke()
    }
}

@Composable
fun SmallTopBar(title: String, onBack: (() -> Unit)? = null, actions: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ArrowBack, "返回", tint = SaltTheme.colors.text, modifier = Modifier.size(24.dp))
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = BbType.Section,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        )
        actions?.invoke()
    }
}

/** Pill search field: Salt Player uses a full-radius grey pill, no M3 container chrome. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "关键词 / BV / av / b23.tv / 链接",
    autoFocus: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SaltTheme.dimens.padding)
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(SaltTheme.colors.subBackground)
            .border(0.5.dp, SaltTheme.colors.stroke, RoundedCornerShape(50))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = SaltTheme.colors.subText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = SaltTheme.colors.text, fontSize = BbType.Body.fontSize),
                cursorBrush = SolidColor(SaltTheme.colors.highlight),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(value) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            onSearch(value)
                            true
                        } else if (event.key == Key.Enter) {
                            true
                        } else {
                            false
                        }
                    },
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, color = SaltTheme.colors.subText, style = BbType.Body)
                    }
                    inner()
                },
            )
        }
        if (value.isNotEmpty()) {
            Box(
                // 行高 46dp 限制，清空键取 40dp（SPEC AC8 显式豁免）；其余可点元素 ≥48dp。
                Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable {
                    onValueChange("")
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "清空", tint = SaltTheme.colors.subText, modifier = Modifier.size(17.dp))
            }
        }
    }
}

/** Small label pill, e.g. quality badge / filter chip. */
@Composable
fun SaltPill(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) SaltTheme.colors.highlight.copy(alpha = 0.14f) else SaltTheme.colors.subBackground)
            .border(
                0.5.dp,
                if (selected) SaltTheme.colors.highlight.copy(alpha = 0.45f) else SaltTheme.colors.stroke,
                RoundedCornerShape(50),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = if (selected) SaltTheme.colors.highlight else SaltTheme.colors.subText,
            style = BbType.Caption,
            maxLines = 1,
        )
    }
}

/**
 * 图标按钮：视觉尺寸由 [size] 决定，**触控热区不小于 48dp**（SPEC AC8）。
 */
@Composable
fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = SaltTheme.colors.text,
    size: Int = 48,
) {
    val touchSize = maxOf(size, 48)
    Box(
        Modifier.size(touchSize.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}
