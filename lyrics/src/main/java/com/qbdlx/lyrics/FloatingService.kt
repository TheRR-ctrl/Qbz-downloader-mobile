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
 * A small window over any app. Like Shazam's: a round cover button with the line being sung in a bar beside it (tap the button to fold it to just the button);
 * the ▭/⤢ handle switches to the bigger panel with the next line too. Drag it to move it, ↔ resizes it, A+/A− change the text size; the ✕ closes it.
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
        if (openedByLeaving) prefs.edit().putBoolean("float_min", true).apply() // leaving the app shows just the bubble, like a chat head
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
        val close = TextView(this).apply { text = "✕"; setTextColor(Color.argb(200, 255, 255, 255)); textSize = 16f; setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt()); }
        var scale = prefs.getFloat("float_scale", 1f)
        fun tool(label: String) = TextView(this).apply { text = label; setTextColor(Color.argb(220, 255, 255, 255)); textSize = 16f; gravity = Gravity.CENTER; setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt()) }
        fun relayout() { runCatching { wm.updateViewLayout(view, params) } }
        lateinit var applyLayout: () -> Unit
        val bigger = tool("A+").apply { setOnClickListener { scale = (scale + 0.15f).coerceAtMost(2.2f); prefs.edit().putFloat("float_scale", scale).apply(); applyLayout() } }
        val smaller = tool("A−").apply { setOnClickListener { scale = (scale - 0.15f).coerceAtLeast(0.6f); prefs.edit().putFloat("float_scale", scale).apply(); applyLayout() } }
        // Dragging this corner handle: left/right changes the window's width, up/down the size of the text.
        val resize = tool("⤡").apply {
            var startW = 0; var downX = 0f; var downY = 0f; var startScale = 1f
            setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { startW = params.width.takeIf { it > 0 } ?: prefs.getInt("float_w", 0); downX = e.rawX; downY = e.rawY; startScale = scale }
                    MotionEvent.ACTION_MOVE -> {
                        prefs.edit().putInt("float_w", (startW + (e.rawX - downX).toInt()).coerceIn((160 * dp).toInt(), resources.displayMetrics.widthPixels))
                            .putFloat("float_scale", (startScale + (e.rawY - downY) / (240 * dp)).coerceIn(0.6f, 2.2f)).apply()
                        applyLayout()
                    }
                }
                true
            }
        }
        var bar = prefs.getBoolean("float_bar", true)
        var folded = prefs.getBoolean("float_min", false)
        val bubble = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(255, 40, 40, 40)) }
            clipToOutline = true; outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
        val modeBtn = tool("▭")
        val fold = tool("–")
        val defaultW = (resources.displayMetrics.widthPixels * 0.86f).toInt()
        val tools = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END; addView(close); addView(fold); addView(modeBtn); addView(bigger); addView(smaller); addView(resize) }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; addView(now); addView(next) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(bubble, LinearLayout.LayoutParams((46 * dp).toInt(), (46 * dp).toInt()))
            addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = (10 * dp).toInt() })
            addView(tools)
        }
        // pop in / out like a chat head
        val spring = android.view.animation.OvershootInterpolator(1.6f)
        close.setOnClickListener { root.animate().scaleX(0.3f).scaleY(0.3f).alpha(0f).setDuration(160).withEndAction { stopSelf() }.start() }
        var wasFolded: Boolean? = null
        val bg = CoverBackground(if (bar) 30 * dp else 18 * dp)
        applyLayout = {
            // everything is read from the saved settings, so the Ajustes sliders and the handles on the window act on the same values
            scale = prefs.getFloat("float_scale", 1f); bar = prefs.getBoolean("float_bar", true)
            folded = prefs.getBoolean("float_min", false)
            val hasBubble = prefs.getBoolean("float_bubble", true) || folded // a folded window is always the bubble
            val full = !folded
            bg.opacity = prefs.getInt("float_alpha", 100) / 100f
            val bd = (prefs.getInt("float_bubble_dp", 60) * dp).toInt()
            bubble.layoutParams = LinearLayout.LayoutParams(bd, bd)
            bubble.visibility = if (hasBubble) View.VISIBLE else View.GONE
            texts.visibility = if (full) View.VISIBLE else View.GONE
            tools.visibility = if (full) View.VISIBLE else View.GONE
            modeBtn.text = if (bar) "⤢" else "▭"
            bigger.visibility = if (bar) View.GONE else View.VISIBLE
            smaller.visibility = bigger.visibility
            now.maxLines = if (bar) 1 else 2
            now.ellipsize = if (bar) android.text.TextUtils.TruncateAt.END else null
            now.textSize = (if (bar) 15f else 18f) * scale; next.textSize = 14f * scale
            root.background = if (full) bg else null
            bg.invalidateSelf()
            val pad = if (full) 8 else 0
            val vp = (if (bar) pad else 12) * dp
            root.setPadding(((if (hasBubble) pad else 14) * dp).toInt(), vp.toInt(), ((if (bar) 4 else 12) * dp).toInt(), vp.toInt())
            (texts.layoutParams as LinearLayout.LayoutParams).marginStart = ((if (hasBubble) 10 else 0) * dp).toInt()
            params.width = if (full) prefs.getInt("float_w", defaultW) else WindowManager.LayoutParams.WRAP_CONTENT
            // opened next to the right edge, the panel must not hang off the screen
            if (full && params.width > 0) params.x = params.x.coerceIn(0, (resources.displayMetrics.widthPixels - params.width).coerceAtLeast(0))
            relayout()
            if (wasFolded != folded) {
                // folding / unfolding: the window grows or shrinks from the centre of the round button
                if (wasFolded != null) {
                    root.pivotX = (((if (hasBubble) pad else 14) * dp) + bd / 2f); root.pivotY = bd / 2f + vp
                    root.scaleX = if (folded) 1.25f else 0.7f; root.scaleY = root.scaleX; root.alpha = 0.5f
                    root.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260).setInterpolator(spring).start()
                }
                wasFolded = folded
            }
        }
        // a folded bubble rests against the nearest side edge
        fun snapBubble() {
            val sw = resources.displayMetrics.widthPixels; val bd = (prefs.getInt("float_bubble_dp", 60) * dp).toInt()
            val target = if (params.x + bd / 2 < sw / 2) 0 else (sw - bd).coerceAtLeast(0)
            android.animation.ValueAnimator.ofInt(params.x, target).apply {
                duration = 260; interpolator = android.view.animation.DecelerateInterpolator(1.5f)
                addUpdateListener { params.x = it.animatedValue as Int; relayout() }
                start()
            }
            prefs.edit().putInt("float_x", target).apply()
        }
        fold.setOnClickListener { prefs.edit().putBoolean("float_min", true).apply(); applyLayout(); snapBubble() }
        modeBtn.setOnClickListener { prefs.edit().putBoolean("float_bar", !bar).apply(); applyLayout() }

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        root.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y }
                MotionEvent.ACTION_MOVE -> { params.x = startX + (e.rawX - downX).toInt(); params.y = startY + (e.rawY - downY).toInt(); runCatching { wm.updateViewLayout(root, params) } }
                MotionEvent.ACTION_UP -> {
                    // optionally the window sticks to the nearest side edge when let go
                    if (folded) snapBubble()
                    else if (prefs.getBoolean("float_snap", false)) {
                        val sw = resources.displayMetrics.widthPixels
                        params.x = if (params.x + root.width / 2 < sw / 2) 0 else (sw - root.width).coerceAtLeast(0); relayout()
                    }
                    prefs.edit().putInt("float_x", params.x).putInt("float_y", params.y).apply()
                    // a tap (no drag) anywhere on the window folds it to the round button, and a tap on the button opens it again
                    val tap = Math.abs(e.rawX - downX) < 8 * dp && Math.abs(e.rawY - downY) < 8 * dp
                    if (tap) { prefs.edit().putBoolean("float_min", !folded).apply(); applyLayout(); if (!folded) Unit else snapBubble() }
                }
            }
            true
        }
        wm.addView(root, params)
        view = root
        applyLayout()
        root.pivotX = 0f; root.pivotY = 0f; root.scaleX = 0.3f; root.scaleY = 0.3f; root.alpha = 0f
        root.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(280).setInterpolator(spring).start()
        // while the song plays, the folded bubble breathes slowly
        val breath = android.animation.ValueAnimator.ofFloat(1f, 1.08f).apply {
            duration = 900; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { bubble.scaleX = it.animatedValue as Float; bubble.scaleY = bubble.scaleX }
        }

        scope.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { breath.cancel() }
        var litNow = 0; var litShown = -1; var sig = ""
        var bubbleCover: android.graphics.Bitmap? = null
        scope.launch {
            while (isActive) {
                // a setting changed in Ajustes: the window follows within a quarter of a second
                val now2 = listOf("float_scale", "float_bar", "float_bubble", "float_min", "float_alpha", "float_bubble_dp", "float_w", "float_next").joinToString("|") { prefs.all[it].toString() }
                if (now2 != sig) { sig = now2; applyLayout() }
                val state = Tracker.state.value
                val wantBreath = folded && state.playing
                if (wantBreath && !breath.isRunning) breath.start() else if (!wantBreath && breath.isRunning) { breath.cancel(); bubble.scaleX = 1f; bubble.scaleY = 1f }
                val color = Tracker.accent.value.takeIf { it != 0 } ?: 0xFF455A64.toInt()
                // the notification's colour, darkened so white text stays readable
                bg.update(Color.argb(255, (Color.red(color) * 0.55f).toInt(), (Color.green(color) * 0.55f).toInt(), (Color.blue(color) * 0.55f).toInt()), Tracker.cover)
                if (bubbleCover !== Tracker.cover) { bubbleCover = Tracker.cover; bubble.setImageBitmap(bubbleCover) }
                val fg = if (bg.light) Color.rgb(20, 20, 20) else Color.WHITE
                if (now.currentTextColor != fg) {
                    now.setTextColor(fg); next.setTextColor(Color.argb(190, Color.red(fg), Color.green(fg), Color.blue(fg)))
                    close.setTextColor(fg); fold.setTextColor(fg); modeBtn.setTextColor(fg); bigger.setTextColor(fg); smaller.setTextColor(fg); resize.setTextColor(fg)
                }
                val (a, b) = when (state.status) {
                    Status.SYNCED -> {
                        val posNow = Tracker.positionMs()
                        val i = Lrc.indexAt(state.lines, posNow)
                        litNow = if (i >= 0 && Tracker.karaoke) Lrc.litChars(state.lines[i].text, state.lines[i].timeMs, state.lines.getOrNull(i + 1)?.timeMs, posNow, state.lines[i].endMs) else 0
                        (if (i < 0) "♪ …" else state.lines[i].text.ifBlank { "♪" }) to (Tracker.translations.value[i]?.let { "↳ $it" } ?: state.lines.getOrNull(i + 1)?.text.orEmpty())
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
                next.visibility = if (b.isBlank() || bar || !prefs.getBoolean("float_next", true)) View.GONE else View.VISIBLE
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
        /** True when the bubble was opened by leaving the app (it closes by itself when the app comes back); false when the person opened it. */
        @Volatile var openedByLeaving = false
        /** Shows the bubble if it is not already showing; false when Android's "display over other apps" permission is missing. */
        fun show(context: Context, byLeaving: Boolean): Boolean {
            if (!android.provider.Settings.canDrawOverlays(context)) return false
            if (!running) { openedByLeaving = byLeaving; context.startService(Intent(context, FloatingService::class.java)) }
            return true
        }
        /** Closes a bubble that opening the app made redundant; one the person opened themselves stays. */
        fun hideIfAuto(context: Context) {
            if (running && openedByLeaving) { openedByLeaving = false; context.stopService(Intent(context, FloatingService::class.java)) }
        }
        fun toggle(context: Context) {
            val i = Intent(context, FloatingService::class.java)
            openedByLeaving = false
            if (running) context.stopService(i) else context.startService(i)
        }
    }
}

/** Rounded background: the accent colour with the cover faded over it, then a dark veil; [light] says if dark text reads better. */
private class CoverBackground(private val radius: Float) : android.graphics.drawable.Drawable() {
    /** 0..1: how opaque the window is (the text stays fully visible). */
    var opacity = 1f
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
        val k = opacity.coerceIn(0f, 1f)
        paint.color = base; paint.alpha = (255 * k).toInt(); c.drawRect(r, paint)
        cover?.let { b ->
            val s = maxOf(r.width() / b.width, r.height() / b.height)
            val w = b.width * s; val h = b.height * s
            paint.alpha = (115 * k).toInt()
            c.drawBitmap(b, null, android.graphics.RectF(r.centerX() - w / 2, r.centerY() - h / 2, r.centerX() + w / 2, r.centerY() + h / 2), paint)
            paint.alpha = 255
        }
        paint.color = Color.argb((140 * k).toInt(), 0, 0, 0); c.drawRect(r, paint)
        c.restore()
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(f: android.graphics.ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}
