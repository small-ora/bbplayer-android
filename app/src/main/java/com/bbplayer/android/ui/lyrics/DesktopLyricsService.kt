package com.bbplayer.android.ui.lyrics

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.bbplayer.android.BbPlayerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Floating desktop lyrics (TYPE_APPLICATION_OVERLAY), the Salt Player style overlay:
 * current line + translation, drag to move, tap for a small control row
 * (font size / colour / lock / close).  Lyrics come from [NowPlayingLyrics].
 */
class DesktopLyricsService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var root: LinearLayout? = null
    private var textView: TextView? = null
    private var translationView: TextView? = null
    private var controls: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var fontSize = 18f
    private var textColor = 0xFFFFC107.toInt()
    private var locked = false
    private var dragStartY = 0f
    private var dragStartTouchY = 0f
    private var moved = false

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val appGraph get() = (application as BbPlayerApp).graph

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!canDrawOverlays()) {
            Toast.makeText(this, "请先授予「显示在其他应用上层」权限", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        fontSize = prefs.getFloat(KEY_FONT_SIZE, 18f)
        textColor = prefs.getInt(KEY_COLOR, 0xFFFFC107.toInt())
        locked = prefs.getBoolean(KEY_LOCKED, false)
        if (root == null) {
            showOverlay()
            observe()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- overlay

    private fun showOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0x99000000.toInt())
            }
        }
        textView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
            setTextColor(textColor)
            setShadowLayer(3f, 0f, 1f, Color.BLACK)
            gravity = Gravity.CENTER
            maxLines = 2
            text = "桌面歌词"
        }
        translationView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize * 0.72f)
            setTextColor(0xCCFFFFFF.toInt())
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        controls = buildControls()
        container.addView(textView)
        container.addView(translationView)
        container.addView(controls)
        controls?.visibility = View.GONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = prefs.getInt(KEY_Y, dp(120))
        }
        layoutParams = params
        container.setOnTouchListener { _, event -> onTouch(event) }
        container.setOnClickListener { toggleControls() }
        runCatching { windowManager.addView(container, params) }
        root = container
        applyLocked()
    }

    private fun buildControls(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        fun add(label: String, onClick: () -> Unit) {
            row.addView(TextView(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(dp(10), dp(4), dp(10), dp(4))
                setOnClickListener { onClick() }
            })
        }
        add("A-") { changeFontSize(-2f) }
        add("A+") { changeFontSize(+2f) }
        add("颜色") { cycleColor() }
        add("锁定") { toggleLock() }
        add("关闭") { requestStop() }
        return row
    }

    private fun onTouch(event: MotionEvent): Boolean {
        val params = layoutParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartY = params.y.toFloat()
                dragStartTouchY = event.rawY
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - dragStartTouchY
                if (kotlin.math.abs(dy) > dp(6) && !locked) {
                    moved = true
                    params.y = (dragStartY + dy).toInt().coerceAtLeast(0)
                    root?.let { runCatching { windowManager.updateViewLayout(it, params) } }
                }
            }
            MotionEvent.ACTION_UP -> if (moved) {
                prefs.edit().putInt(KEY_Y, params.y).apply()
            }
        }
        return false
    }

    private fun toggleControls() {
        controls?.visibility = if (controls?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun changeFontSize(delta: Float) {
        fontSize = (fontSize + delta).coerceIn(10f, 40f)
        textView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
        translationView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize * 0.72f)
        prefs.edit().putFloat(KEY_FONT_SIZE, fontSize).apply()
        scope.launch { runCatching { appGraph.preferencesRepository.setDesktopLyricsTextSize(fontSize) } }
    }

    private fun cycleColor() {
        val colors = longArrayOf(0xFFFFC107, 0xFFFFFFFF, 0xFF7FB8FF, 0xFFFF9EB8, 0xFF5CCBA5)
        val next = colors[(colors.indexOf(textColor.toLong()).coerceAtLeast(-1) + 1) % colors.size]
        textColor = next.toInt()
        textView?.setTextColor(textColor)
        prefs.edit().putInt(KEY_COLOR, textColor).apply()
        scope.launch { runCatching { appGraph.preferencesRepository.setDesktopLyricsColor(next) } }
    }

    private fun toggleLock() {
        locked = !locked
        prefs.edit().putBoolean(KEY_LOCKED, locked).apply()
        applyLocked()
        Toast.makeText(this, if (locked) "桌面歌词已锁定" else "桌面歌词已解锁", Toast.LENGTH_SHORT).show()
    }

    private fun applyLocked() {
        val params = layoutParams ?: return
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        root?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        // Locked: dragging is ignored (tap still works so the user can unlock from the panel).
        controls?.visibility = if (locked) View.GONE else controls?.visibility ?: View.GONE
    }

    private fun requestStop() {
        scope.launch { runCatching { appGraph.preferencesRepository.setDesktopLyricsEnabled(false) } }
        stopSelf()
    }

    private fun observe() {
        scope.launch {
            NowPlayingLyrics.inAppLyricsVisible.collectLatest { visible ->
                root?.visibility = if (visible) View.GONE else View.VISIBLE
            }
        }
        scope.launch {
            NowPlayingLyrics.snapshot.collectLatest { snapshot ->
                textView?.text = snapshot.text.ifBlank { "桌面歌词" }
                val translation = snapshot.translation
                translationView?.apply {
                    text = translation.orEmpty()
                    visibility = if (translation.isNullOrBlank()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun canDrawOverlays(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFS = "desktop_lyrics"
        private const val KEY_Y = "y"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_COLOR = "color"
        private const val KEY_LOCKED = "locked"
        const val ACTION_STOP = "com.bbplayer.android.action.STOP_DESKTOP_LYRICS"

        fun start(context: Context) {
            runCatching { context.startService(Intent(context, DesktopLyricsService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DesktopLyricsService::class.java))
        }
    }
}
