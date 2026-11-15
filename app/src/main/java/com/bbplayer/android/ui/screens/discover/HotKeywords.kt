package com.bbplayer.android.ui.screens.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.SectionHeader
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

/**
 * B 站热搜词小组件（SPEC D2）：供搜索页空态使用，点击某条热搜词即回调。
 * 失败时静默降级，并保留一个轻量重试入口，不打断搜索主流程。
 */
@Composable
fun HotKeywords(
    onKeywordClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "B 站热搜",
) {
    val graph = LocalAppGraph.current
    var keywords by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        error = null
        graph.discoverRepository.hotKeywords()
            .onSuccess {
                keywords = it.distinct()
                loading = false
            }
            .onFailure {
                error = it.toUserMessage()
                loading = false
            }
    }

    if (keywords.isEmpty() && !loading && error == null) return

    Column(modifier.fillMaxWidth()) {
        SectionHeader(
            title = title,
            action = if (error != null) "重试" else null,
            onAction = if (error != null) {
                { reloadKey++ }
            } else {
                null
            },
        )
        when {
            loading -> Text(
                text = "正在加载热搜…",
                color = SaltTheme.colors.subText,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = SaltTheme.dimens.padding + 4.dp),
            )
            keywords.isEmpty() -> Text(
                text = error ?: "热搜暂时不可用",
                color = SaltTheme.colors.subText,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = SaltTheme.dimens.padding + 4.dp),
            )
            else -> LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = SaltTheme.dimens.padding, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(keywords, key = { keyword -> keyword }) { keyword ->
                    val rank = keywords.indexOf(keyword) + 1
                    SaltPill(
                        text = if (rank <= 3) "$rank  $keyword" else keyword,
                        selected = rank <= 3,
                        onClick = { onKeywordClick(keyword) },
                    )
                }
            }
        }
    }
}
