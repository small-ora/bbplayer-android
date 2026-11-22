package com.bbplayer.android.ui.components

import com.bbplayer.android.ui.theme.BbType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text

@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    icon: ImageVector = Icons.Rounded.LibraryMusic,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = SaltTheme.colors.subText.copy(alpha = 0.6f), modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text(text = title, textAlign = TextAlign.Center, color = SaltTheme.colors.subText)
        subtitle?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                color = SaltTheme.colors.subText,
                style = BbType.Caption,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            SaltTextButton(text = action, onClick = onAction)
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.CloudOff, null, tint = SaltTheme.colors.subText, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text(text = message, color = SaltTheme.colors.subText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        SaltTextButton(text = "重试", onClick = onRetry)
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, text: String = "加载中…") {
    Box(modifier = modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = SaltTheme.colors.highlight,
                strokeWidth = 2.dp,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(text = text, color = SaltTheme.colors.subText, style = BbType.Caption)
        }
    }
}

@Composable
fun SaltTextButton(text: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .androidxClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = SaltTheme.colors.highlight, style = BbType.Body)
    }
}

private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
