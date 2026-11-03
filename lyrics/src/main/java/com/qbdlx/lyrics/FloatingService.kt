package com.qbdlx.lyrics

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*

/**
 * A small window over any app that shows the line being sung and the next one. Drag it to move it, ↔ resizes it, A+/A− change the text size; the ✕ closes it.
 * Needs Android's "Display over other apps" permission, which the person gives in Settings.
 */
class FloatingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var view: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!android.provider.Settings.canDrawOverlays(this)) { stopSelf(); return }
        running = true
        Tracker.start(this)
        val prefs = getSharedPreferences("lyrics", MODE_PRIVATE)
        val wm = getSystemService(WindowManager::class.java)
        val dp = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            prefs.getInt("float_w", (resources.displayMetrics.widthPixels * 0.86f).toInt()), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = prefs.getInt("float_x", (resources.displayMetrics.widthPixels * 0.07f).toInt()); y = prefs.getInt("float_y", (120 * dp).toInt()) }

        val now = TextView(this).apply { setTextColor(Color.WHITE); textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD); maxLines = 2 }
        val next = TextView(this).apply { setTextColor(Color.argb(180, 255, 255, 255)); textSize = 14f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        val close = TextView(this).apply { text = "✕"; setTextColor(Color.argb(200, 255, 255, 255)); textSize = 16f; setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt()); setOnClickListener { stopSelf() } }
        var scale = prefs.getFloat("float_scale", 1f)
        fun applyScale() { now.textSize = 18f * scale; next.textSize = 14f * scale }
        applyScale()
        fun tool(label: String) = TextView(this).apply { text = label; setTextColor(Color.argb(220, 255, 255, 255)); textSize = 16f; gravity = Gravity.CENTER; setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt()) }
        fun relayout() { runCatching { wm.updateViewLayout(view, params) } }
        val bigger = tool("A+").apply { setOnClickListener { scale = (scale + 0.15f).coerceAtMost(2.2f); prefs.edit().putFloat("float_scale", scale).apply(); applyScale(); relayout() } }
        val smaller = tool("A−").apply { setOnClickListener { scale = (scale - 0.15f).coerceAtLeast(0.6f); prefs.edit().putFloat("float_scale", scale).apply(); applyScale(); relayout() } }
        // Dragging this handle left or right changes the window's width.
        val resize = tool("↔").apply {
            var startW = 0; var downX = 0f
            setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { startW = params.width; downX = e.rawX }
                    MotionEvent.ACTION_MOVE -> {
                        params.width = (startW + (e.rawX - downX).toInt()).coerceIn((160 * dp).toInt(), resources.displayMetrics.widthPixels)
                        relayout()
                    }
                    MotionEvent.ACTION_UP -> prefs.edit().putInt("float_w", params.width).apply()
                }
                true
            }
        }
        val tools = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END; addView(close); addView(bigger); addView(smaller); addView(resize) }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(now); addView(next) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(tools)
        }
        val bg = CoverBackground(18 * dp)
        root.background = bg

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        root.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y }
                MotionEvent.ACTION_MOVE -> { params.x = startX + (e.rawX - downX).toInt(); params.y = startY + (e.rawY - downY).toInt(); runCatching { wm.updateViewLayout(root, params) } }
                MotionEvent.ACTION_UP -> prefs.edit().putInt("float_x", params.x).putInt("float_y", params.y).apply()
            }
            true
        }
        wm.addView(root, params)
        view = root

        var litNow = 0; var litShown = -1
        scope.launch {
            while (isActive) {
                val state = Tracker.state.value
                val color = Tracker.accent.value.takeIf { it != 0 } ?: 0xFF455A64.toInt()
                // the notification's colour, darkened so white text stays readable
                bg.update(Color.argb(255, (Color.red(color) * 0.55f).toInt(), (Color.green(color) * 0.55f).toInt(), (Color.blue(color) * 0.55f).toInt()), Tracker.cover)
                val fg = if (bg.light) Color.rgb(20, 20, 20) else Color.WHITE
                if (now.currentTextColor != fg) {
                    now.setTextColor(fg); next.setTextColor(Color.argb(190, Color.red(fg), Color.green(fg), Color.blue(fg)))
                    close.setTextColor(fg); bigger.setTextColor(fg); smaller.setTextColor(fg); resize.setTextColor(fg)
                }
                val (a, b) = when (state.status) {
                    Status.SYNCED -> {
                        val posNow = Tracker.positionMs()
                        val i = Lrc.indexAt(state.lines, posNow)
                        litNow = if (i >= 0 && Tracker.karaoke) Lrc.litChars(state.lines[i].text, state.lines[i].timeMs, state.lines.getOrNull(i + 1)?.timeMs, posNow) else 0
                        (if (i < 0) "♪ …" else state.lines[i].text.ifBlank { "♪" }) to (state.lines.getOrNull(i + 1)?.text.orEmpty())
                    }
                    Status.PLAIN -> "Letra sin sincronizar" to (state.track?.title.orEmpty())
                    Status.LOADING -> "Buscando la letra…" to (state.track?.title.orEmpty())
                    Status.NOT_FOUND -> "No hay letra para esta canción" to (state.track?.title.orEmpty())
                    Status.ERROR -> "No se pudo cargar la letra" to ""
                    Status.NO_ACCESS -> "Falta el acceso a notificaciones" to ""
                    Status.NO_MEDIA -> "♪" to "Pon una canción"
                }
                if (now.text.toString() != a || litNow != litShown) {
                    litShown = litNow
                    now.text = if (litNow > 0 && litNow <= a.length) android.text.SpannableString(a).apply {
                        // the unsung rest is dimmer: the lit words keep the normal colour
                        setSpan(android.text.style.ForegroundColorSpan(Color.argb(150, Color.red(fg), Color.green(fg), Color.blue(fg))), litNow, a.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else a
                }
                if (next.text.toString() != b) next.text = b
                next.visibility = if (b.isBlank()) View.GONE else View.VISIBLE
                delay(250)
            }
        }
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        view?.let { v -> runCatching { getSystemService(WindowManager::class.java).removeView(v) } }
        super.onDestroy()
    }

    companion object {
        @Volatile var running = false
        fun toggle(context: Context) {
            val i = Intent(context, FloatingService::class.java)
            if (running) context.stopService(i) else context.startService(i)
        }
    }
}

/** Rounded background: the accent colour with the cover faded over it, then a dark veil; [light] says if dark text reads better. */
private class CoverBackground(private val radius: Float) : android.graphics.drawable.Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var base = 0
    private var cover: android.graphics.Bitmap? = null
    var light = false; private set

    fun update(color: Int, art: android.graphics.Bitmap?) {
        if (color == base && art === cover) return
        base = color; cover = art
        // average brightness of what is drawn: cover at 45% over the colour, then the veil (55% black)
        var l = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
        if (art != null) {
            val px = android.graphics.Bitmap.createScaledBitmap(art, 1, 1, true).getPixel(0, 0)
            l = l * 0.55 + (0.299 * Color.red(px) + 0.587 * Color.green(px) + 0.114 * Color.blue(px)) * 0.45
        }
        light = l * 0.45 > 150
        invalidateSelf()
    }

    override fun draw(c: android.graphics.Canvas) {
        val r = android.graphics.RectF(bounds)
        val path = android.graphics.Path().apply { addRoundRect(r, radius, radius, android.graphics.Path.Direction.CW) }
        c.save(); c.clipPath(path)
        paint.color = base; c.drawRect(r, paint)
        cover?.let { b ->
            val s = maxOf(r.width() / b.width, r.height() / b.height)
            val w = b.width * s; val h = b.height * s
            paint.alpha = 115
            c.drawBitmap(b, null, android.graphics.RectF(r.centerX() - w / 2, r.centerY() - h / 2, r.centerX() + w / 2, r.centerY() + h / 2), paint)
            paint.alpha = 255
        }
        paint.color = Color.argb(140, 0, 0, 0); c.drawRect(r, paint)
        c.restore()
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(f: android.graphics.ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}
