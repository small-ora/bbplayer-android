package com.bbplayer.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = SaltTheme.dimens.padding,
                end = SaltTheme.dimens.padding,
                top = 20.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (subtitle == null) {
            Text(text = title, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = title, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(
                    text = "  $subtitle",
                    color = SaltTheme.colors.subText,
                    fontSize = SaltTheme.textStyles.sub.fontSize,
                )
            }
        }
        if (action != null && onAction != null) {
            // 区块标题右侧操作：视觉文字不变，热区 ≥48dp（SPEC AC8）
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onAction),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = action,
                    color = SaltTheme.colors.subText,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
