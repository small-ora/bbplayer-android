package com.bbplayer.android.ui.screens.login

import com.bbplayer.android.ui.theme.BbType
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbplayer.android.core.contracts.toUserMessage
import com.bbplayer.android.core.model.BiliUser
import com.bbplayer.android.core.model.QrCodeInfo
import com.bbplayer.android.core.model.QrPollState
import com.bbplayer.android.core.safeLaunch
import com.bbplayer.android.di.LocalAppGraph
import com.bbplayer.android.ui.theme.BbTokens
import com.bbplayer.android.ui.components.SaltPill
import com.bbplayer.android.ui.components.SmallTopBar
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Bilibili account login (v5).
 *
 * Two flows: QR scan (primary) and manual cookie paste (power-user fallback). The screen never
 * displays, logs or persists the cookie itself — after a successful login the caller only receives
 * the public [BiliUser] profile, while credentials stay inside the data-layer Keystore store.
 */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: (BiliUser) -> Unit,
) {
    var tab by remember { mutableStateOf(LoginTab.QR) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SmallTopBar(title = "登录 Bilibili", onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SaltTheme.dimens.padding),
        ) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoginTab.entries.forEach { item ->
                    SaltPill(text = item.label, selected = tab == item, onClick = { tab = item })
                }
            }
            Spacer(Modifier.height(18.dp))
            when (tab) {
                LoginTab.QR -> QrLoginSection(onLoggedIn = onLoggedIn)
                LoginTab.COOKIE -> CookieLoginSection(onLoggedIn = onLoggedIn)
            }
            // 登录页是二级页：底部迷你播放器常驻，滚到底时留出安全内边距（AC6）。
            Spacer(Modifier.height(BbTokens.SecondaryContentBottomPadding))
        }
    }
}

private enum class LoginTab(val label: String) { QR("扫码登录"), COOKIE("Cookie 粘贴") }

@Composable
private fun QrLoginSection(onLoggedIn: (BiliUser) -> Unit) {
    val graph = LocalAppGraph.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var attempt by remember { mutableStateOf(0) }
    var info by remember { mutableStateOf<QrCodeInfo?>(null) }
    var bitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pollState by remember { mutableStateOf(QrPollState.WAITING) }
    var pollMessage by remember { mutableStateOf("") }
    var pollError by remember { mutableStateOf<String?>(null) }

    // "Enter the page" (and every manual refresh) generates a fresh QR code.
    LaunchedEffect(attempt) {
        loading = true
        error = null
        info = null
        bitmap = null
        pollState = QrPollState.WAITING
        pollMessage = ""
        pollError = null
        graph.authRepository.generateQrCode()
            .onSuccess { generated ->
                info = generated
                bitmap = runCatching { encodeQr(generated.url) }.getOrNull()
                if (bitmap == null) error = "二维码生成失败，请刷新重试"
            }
            .onFailure { error = it.toUserMessage() }
        loading = false
    }

    // Poll every 2s, automatically expiring after 150s (or the server-provided deadline).
    LaunchedEffect(info?.qrKey, attempt) {
        val qr = info ?: return@LaunchedEffect
        if (pollState == QrPollState.EXPIRED || pollState == QrPollState.SUCCESS) return@LaunchedEffect
        val deadline = qr.expiresAtMs.takeIf { it > System.currentTimeMillis() }
            ?: (System.currentTimeMillis() + QR_TIMEOUT_MS)
        while (isActive) {
            delay(POLL_INTERVAL_MS)
            if (System.currentTimeMillis() >= deadline) {
                pollState = QrPollState.EXPIRED
                pollMessage = "二维码已过期"
                break
            }
            // A transient failure keeps the loop alive; the next tick can still succeed.
            val result = graph.authRepository.pollQrCode(qr.qrKey).getOrElse { failure ->
                pollError = failure.toUserMessage()
                null
            } ?: continue
            pollError = null
            pollMessage = result.message
            when (result.state) {
                QrPollState.WAITING -> pollState = QrPollState.WAITING
                QrPollState.SCANNED -> pollState = QrPollState.SCANNED
                QrPollState.EXPIRED -> {
                    pollState = QrPollState.EXPIRED
                    break
                }
                QrPollState.SUCCESS -> {
                    pollState = QrPollState.SUCCESS
                    val user = result.user
                    if (user != null) {
                        Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                        onLoggedIn(user)
                    } else {
                        pollError = "登录状态异常，请刷新二维码重试"
                    }
                    break
                }
                QrPollState.ERROR -> {
                    pollError = result.message.ifBlank { "登录失败，请重试" }
                    break
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(248.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                loading -> CircularProgressIndicator(
                    color = SaltTheme.colors.highlight,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
                bitmap != null -> Image(
                    bitmap = bitmap!!,
                    contentDescription = "Bilibili 登录二维码",
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Icon(
                    Icons.Rounded.QrCode2,
                    contentDescription = null,
                    tint = Color(0xFF999999),
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        val statusText = when {
            pollError != null -> pollError!!
            pollState == QrPollState.SUCCESS -> "登录成功"
            pollState == QrPollState.EXPIRED -> "二维码已过期"
            pollState == QrPollState.SCANNED -> "已扫码，请在手机上确认登录"
            else -> "打开 Bilibili App 扫描二维码"
        }
        val statusColor = when {
            pollError != null -> ErrorColor
            pollState == QrPollState.SCANNED -> SaltTheme.colors.highlight
            pollState == QrPollState.EXPIRED -> SaltTheme.colors.subText
            else -> SaltTheme.colors.text
        }
        Text(statusText, color = statusColor, style = BbType.Body)
        if (pollMessage.isNotBlank() && pollError == null && pollState != QrPollState.SCANNED) {
            Spacer(Modifier.height(4.dp))
            Text(pollMessage, color = SaltTheme.colors.subText, style = BbType.Caption)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "二维码 150 秒内有效，登录成功后将自动返回",
            color = SaltTheme.colors.subText,
            style = BbType.Micro,
        )
        Spacer(Modifier.height(14.dp))
        when {
            error != null -> {
                Text(error!!, color = ErrorColor, style = BbType.Label)
                Spacer(Modifier.height(10.dp))
                SaltPill(text = "重试", selected = true, onClick = { attempt++ })
            }
            pollState == QrPollState.EXPIRED -> SaltPill(
                text = "刷新二维码",
                selected = true,
                onClick = { attempt++ },
            )
            pollError != null -> SaltPill(text = "重试", selected = true, onClick = { attempt++ })
            !loading -> SaltPill(text = "刷新", onClick = { attempt++ })
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SaltTheme.colors.subBackground)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = null,
                tint = SaltTheme.colors.highlight,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "扫码后请在手机端确认；凭据仅保存在本机 Keystore 加密存储。",
                color = SaltTheme.colors.subText,
                style = BbType.Micro,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun CookieLoginSection(onLoggedIn: (BiliUser) -> Unit) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var cookie by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (loading || cookie.isBlank()) return
        loading = true
        error = null
        scope.safeLaunch(onError = { error = it.toUserMessage() }) {
            // Never log or echo this value; it is handed straight to the encrypted store.
            graph.authRepository.loginWithCookie(cookie.trim())
                .onSuccess { user ->
                    cookie = ""
                    Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                    onLoggedIn(user)
                }
                .onFailure { error = it.toUserMessage() }
            loading = false
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text("粘贴浏览器 Cookie", style = BbType.Section, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "从已登录 bilibili.com 的浏览器开发者工具复制 Cookie 请求头，粘贴到下方。",
            color = SaltTheme.colors.subText,
            style = BbType.Caption,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(156.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SaltTheme.colors.subBackground)
                .padding(12.dp),
        ) {
            if (cookie.isEmpty()) {
                Text(
                    "SESSDATA=...; bili_jct=...; DedeUserID=...",
                    color = SaltTheme.colors.subText,
                    style = BbType.Caption,
                    lineHeight = 17.sp,
                )
            }
            BasicTextField(
                value = cookie,
                onValueChange = { cookie = it },
                textStyle = TextStyle(color = SaltTheme.colors.text, fontSize = BbType.Caption.fontSize, lineHeight = 17.sp),
                cursorBrush = SolidColor(SaltTheme.colors.highlight),
                modifier = Modifier.fillMaxSize(),
            )
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = ErrorColor, style = BbType.Caption)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SaltPill(
                text = if (loading) "登录中…" else "登录",
                selected = cookie.isNotBlank() && !loading,
                onClick = { submit() },
            )
        }
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SaltTheme.colors.highlight.copy(alpha = 0.08f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                "安全提示：Cookie 等同账号凭据，仅保存在本机 Keystore 加密存储，可随时退出清除。请勿分享给他人，也不要粘贴到不可信的页面。",
                color = SaltTheme.colors.subText,
                style = BbType.Micro,
                lineHeight = 17.sp,
            )
        }
    }
}

/** Renders a QR code with zxing (already a declared app dependency) into a Compose bitmap. */
private fun encodeQr(content: String, sizePx: Int = 720): androidx.compose.ui.graphics.ImageBitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        val row = y * sizePx
        for (x in 0 until sizePx) {
            pixels[row + x] = if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private val ErrorColor = Color(0xFFE5484D)

private const val POLL_INTERVAL_MS = 2_000L
private const val QR_TIMEOUT_MS = 150_000L
