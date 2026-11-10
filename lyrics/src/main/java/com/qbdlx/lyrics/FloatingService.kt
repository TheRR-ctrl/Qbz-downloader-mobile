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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = prefs.getInt("float_x", (resources.displayMetrics.widthPixels * 0.07f).toInt()); y = prefs.getInt("float_y", (120 * dp).toInt()) }

        val now = TextView(this).apply { setTextColor(Color.WHITE); textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD); maxLines = 3 }
        val prev = TextView(this).apply { setTextColor(Color.argb(150, 255, 255, 255)); textSize = 12f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; visibility = View.GONE }
        val next = TextView(this).apply { setTextColor(Color.argb(180, 255, 255, 255)); textSize = 14f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        val close = TextView(this).apply { text = "✕"; setTextColor(Color.argb(200, 255, 255, 255)); textSize = 16f; setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt()); }
        var scale = prefs.getFloat("float_scale", 1f)
        fun tool(label: String) = TextView(this).apply { text = label; setTextColor(Color.argb(220, 255, 255, 255)); textSize = 16f; gravity = Gravity.CENTER; setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt()) }
        fun relayout() { runCatching { wm.updateViewLayout(view, params) } }
        lateinit var applyLayout: () -> Unit
        val bigger = tool("A+").apply { setOnClickListener { scale = (scale + 0.15f).coerceAtMost(2.2f); prefs.edit().putFloat("float_scale", scale).apply(); applyLayout() } }
        val smaller = tool("A−").apply { setOnClickListener { scale = (scale - 0.15f).coerceAtLeast(0.6f); prefs.edit().putFloat("float_scale", scale).apply(); applyLayout() } }
        // Dragging this corner handle: left/right changes the window's width, up/down its height (a tall window shows more lines).
        val resize = tool("⤡").apply {
            var startW = 0; var startH = 0; var downX = 0f; var downY = 0f
            setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { startW = params.width.takeIf { it > 0 } ?: prefs.getInt("float_w", 0); startH = view?.height ?: 0; downX = e.rawX; downY = e.rawY }
                    MotionEvent.ACTION_MOVE -> {
                        prefs.edit().putInt("float_w", (startW + (e.rawX - downX).toInt()).coerceIn((160 * dp).toInt(), resources.displayMetrics.widthPixels))
                            .putInt("float_h", (startH + (e.rawY - downY).toInt()).coerceIn((56 * dp).toInt(), resources.displayMetrics.heightPixels)).apply()
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
        val defaultW = (resources.displayMetrics.widthPixels * 0.86f).toInt()
        val tools = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END; addView(close); addView(modeBtn); addView(bigger); addView(smaller); addView(resize) }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; addView(prev); addView(now); addView(next) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(bubble, LinearLayout.LayoutParams((46 * dp).toInt(), (46 * dp).toInt()))
            addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = (10 * dp).toInt() })
            addView(tools)
        }
        // pop in / out like a chat head
        val spring = android.view.animation.DecelerateInterpolator(1.6f) // never above 1: the window clips whatever grows past its edges
        var closing = false
        // dropped on the ✕: the window flies into it while it shrinks and fades (the whole window moves, because a view cannot
        // travel outside its own window); a hardware layer keeps the rounded edges while it is scaled
        // Dropped on the ✕: by then the magnet already holds the window on it, so it only sinks into the ✕ (shrinks, then fades at
        // the very end) and the ✕ closes after it. Fading while it flew made it vanish halfway: it looked cut.
        var target: TextView? = null
        fun dismiss(cx: Float, cy: Float, offX: Int, offY: Int) {
            if (closing) return
            closing = true
            suppressAuto = true // closed by hand: it does not pop up again until the music stops
            val sx = params.x; val sy = params.y
            val ex = (cx - root.width / 2f).toInt() + offX; val ey = (cy - root.height / 2f).toInt() + offY
            root.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            root.animate().cancel()
            root.pivotX = root.width / 2f; root.pivotY = root.height / 2f
            val s0 = root.scaleX
            val t = target; target = null
            android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 260; interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val f = it.animatedValue as Float
                    val go = (f / 0.5f).coerceAtMost(1f)
                    params.x = sx + ((ex - sx) * go).toInt(); params.y = sy + ((ey - sy) * go).toInt()
                    root.scaleX = s0 + (0.15f - s0) * f; root.scaleY = root.scaleX
                    root.alpha = if (f < 0.6f) 1f else 1f - (f - 0.6f) / 0.4f
                    relayout()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(a: android.animation.Animator) {
                    root.visibility = View.INVISIBLE
                    if (t == null) stopSelf()
                    else t.animate().alpha(0f).scaleX(0.3f).scaleY(0.3f).setDuration(160).withEndAction { runCatching { wm.removeView(t) }; stopSelf() }.start()
                } })
                start()
            }
        }
        // like a picture-in-picture window or a chat head: dragging it onto the ✕ at the bottom closes it
        fun targetCenter(): Pair<Float, Float>? { val t = target ?: return null; val c = IntArray(2); t.getLocationOnScreen(c); return (c[0] + t.width / 2f) to (c[1] + t.height / 2f) }
        fun overTarget(x: Float, y: Float): Boolean { val t = target ?: return false; val c = IntArray(2); t.getLocationOnScreen(c); return Math.hypot((x - (c[0] + t.width / 2)).toDouble(), (y - (c[1] + t.height / 2)).toDouble()) < 80 * dp }
        fun showTarget() {
            if (target != null) return
            val t = TextView(this).apply {
                text = "✕"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(215, 20, 20, 20)) } // no outline: a ring around the ✕ read as a hard border
                alpha = 0f; scaleX = 0.4f; scaleY = 0.4f; translationY = 40 * dp
            }
            val lp = WindowManager.LayoutParams((60 * dp).toInt(), (60 * dp).toInt(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
                .apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = (48 * dp).toInt() }
            if (runCatching { wm.addView(t, lp) }.isSuccess) { target = t; t.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(220).setInterpolator(android.view.animation.OvershootInterpolator(1.6f)).start() }
        }
        fun hideTarget() { target?.let { t -> target = null; t.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(140).withEndAction { runCatching { wm.removeView(t) } }.start() } }
        var wasFolded: Boolean? = null
        // the setting buttons fade out 2 s after the last touch; touching the window brings them back
        var toolsShown = true
        val hideTools = Runnable { toolsShown = false; tools.animate().alpha(0f).setDuration(300).withEndAction { if (!toolsShown) tools.visibility = View.INVISIBLE }.start() }
        fun showTools() {
            toolsShown = true; tools.animate().cancel(); tools.visibility = View.VISIBLE; tools.animate().alpha(1f).setDuration(150).start()
            root.removeCallbacks(hideTools); root.postDelayed(hideTools, 2000)
        }
        var snapAnim: android.animation.ValueAnimator? = null
        val bg = CoverBackground(if (bar) 30 * dp else 18 * dp)
        applyLayout = {
            // everything is read from the saved settings, so the Ajustes sliders and the handles on the window act on the same values
            scale = prefs.getFloat("float_scale", 1f); bar = prefs.getBoolean("float_bar", true)
            folded = prefs.getBoolean("float_min", false)
            val hasBubble = folded // the round cover only shows while folded: the open window is just the text
            val full = !folded
            bg.opacity = prefs.getInt("float_alpha", 100) / 100f
            // with little or no background the text carries its own dark halo (like Musixmatch), so it reads over any app
            val halo = if (bg.opacity < 0.7f) Color.argb(((0.7f - bg.opacity) / 0.7f * 230).toInt().coerceIn(90, 230), 0, 0, 0) else Color.TRANSPARENT
            val r = if (bg.opacity < 0.7f) 3.5f * dp else 0f
            for (t in listOf(now, prev, next, close, modeBtn, bigger, smaller)) t.setShadowLayer(r, 0f, 1f * dp, halo)
            val bd = (prefs.getInt("float_bubble_dp", 60) * dp).toInt()
            bubble.layoutParams = LinearLayout.LayoutParams(bd, bd)
            bubble.visibility = if (hasBubble) View.VISIBLE else View.GONE
            texts.visibility = if (full) View.VISIBLE else View.GONE
            if (full) showTools() else { root.removeCallbacks(hideTools); tools.animate().cancel(); tools.visibility = View.GONE }
            modeBtn.text = if (bar) "⤢" else "▭"
            bigger.visibility = if (bar) View.GONE else View.VISIBLE
            smaller.visibility = bigger.visibility
            now.ellipsize = if (bar) android.text.TextUtils.TruncateAt.END else null
            now.textSize = (if (bar) 14f else 15f) * scale; next.textSize = 12f * scale; prev.textSize = 12f * scale
            root.background = if (full) bg else null
            bg.invalidateSelf()
            val pad = if (full) 8 else 0
            val vp = (if (bar) pad else 12) * dp
            root.setPadding(((if (hasBubble) pad else 14) * dp).toInt(), vp.toInt(), ((if (bar) 4 else 12) * dp).toInt(), vp.toInt())
            (texts.layoutParams as LinearLayout.LayoutParams).marginStart = ((if (hasBubble) 10 else 0) * dp).toInt()
            params.width = if (full) prefs.getInt("float_w", defaultW) else WindowManager.LayoutParams.WRAP_CONTENT
            val hSaved = prefs.getInt("float_h", 0)
            params.height = if (full && hSaved > 0) hSaved else WindowManager.LayoutParams.WRAP_CONTENT
            now.maxLines = if (bar) 1 else if (hSaved > 0) 6 else 2
            // opened next to the right edge, the panel must not hang off the screen
            if (full) snapAnim?.cancel() // a bubble still sliding to the edge would drag the opened window off screen
            if (full && params.width > 0) params.x = params.x.coerceIn(0, (resources.displayMetrics.widthPixels - params.width).coerceAtLeast(0))
            relayout()
            if (wasFolded != folded) {
                // folding / unfolding: the window grows or shrinks from the centre of the round button
                if (wasFolded != null) {
                    root.pivotX = (((if (hasBubble) pad else 14) * dp) + bd / 2f); root.pivotY = bd / 2f + vp
                    root.scaleX = if (folded) 0.85f else 0.7f; root.scaleY = root.scaleX; root.alpha = 0.5f
                    root.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260).setInterpolator(spring).start()
                }
                wasFolded = folded
            }
        }
        // a folded bubble rests against the nearest side edge
        fun snapBubble() {
            val sw = resources.displayMetrics.widthPixels; val bd = (prefs.getInt("float_bubble_dp", 60) * dp).toInt()
            val target = if (params.x + bd / 2 < sw / 2) 0 else (sw - bd).coerceAtLeast(0)
            snapAnim?.cancel()
            snapAnim = android.animation.ValueAnimator.ofInt(params.x, target).apply {
                duration = 260; interpolator = android.view.animation.DecelerateInterpolator(1.5f)
                addUpdateListener { params.x = it.animatedValue as Int; relayout() }
                start()
            }
            prefs.edit().putInt("float_x", target).apply()
        }
        close.setOnClickListener { prefs.edit().putBoolean("float_min", true).apply(); applyLayout(); snapBubble() }
        modeBtn.setOnClickListener { prefs.edit().putBoolean("float_bar", !bar).apply(); applyLayout() }

        var wasHidden = false; var wasOver = false
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        // The magnet: near the ✕ the window leaves the finger and slides onto it, a little smaller, as if pulled in. [pull] goes
        // from 0 (under the finger) to 1 (on the ✕); the place is worked out again on every move and every frame of the slide.
        var pull = 0f; var fingerX = 0; var fingerY = 0; var offX = 0; var offY = 0
        var pullAnim: android.animation.ValueAnimator? = null
        fun place() {
            val tc = targetCenter()
            if (tc == null || pull == 0f) { params.x = fingerX; params.y = fingerY }
            else {
                val mx = (tc.first - root.width / 2f).toInt() + offX; val my = (tc.second - root.height / 2f).toInt() + offY
                params.x = fingerX + ((mx - fingerX) * pull).toInt(); params.y = fingerY + ((my - fingerY) * pull).toInt()
            }
            root.pivotX = root.width / 2f; root.pivotY = root.height / 2f
            root.scaleX = 1f - 0.18f * pull; root.scaleY = root.scaleX
            relayout()
        }
        fun pullTo(v: Float) {
            pullAnim?.cancel()
            pullAnim = android.animation.ValueAnimator.ofFloat(pull, v).apply {
                duration = 170; interpolator = android.view.animation.DecelerateInterpolator(1.8f)
                addUpdateListener { pull = it.animatedValue as Float; if (!closing) place() }
                start()
            }
        }
        root.setOnTouchListener { _, e ->
            if (closing) return@setOnTouchListener true
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { wasHidden = !folded && !toolsShown; if (!folded) showTools(); downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y
                    val c = IntArray(2); root.getLocationOnScreen(c); offX = params.x - c[0]; offY = params.y - c[1]; fingerX = params.x; fingerY = params.y }
                MotionEvent.ACTION_MOVE -> { fingerX = (startX + (e.rawX - downX).toInt()).coerceIn(0, (resources.displayMetrics.widthPixels - root.width).coerceAtLeast(0)); fingerY = startY + (e.rawY - downY).toInt(); place()
                    if (Math.abs(e.rawX - downX) > 8 * dp || Math.abs(e.rawY - downY) > 8 * dp) { showTarget(); target?.let { t -> val over = overTarget(e.rawX, e.rawY); if (over != wasOver) { wasOver = over; pullTo(if (over) 1f else 0f); if (over) root.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); t.animate().scaleX(if (over) 1.25f else 1f).scaleY(if (over) 1.25f else 1f).setDuration(120).start() } } } }
                MotionEvent.ACTION_OUTSIDE -> if (!folded && !closing) { prefs.edit().putBoolean("float_min", true).apply(); applyLayout(); snapBubble() } // a tap anywhere else folds the open window
                MotionEvent.ACTION_CANCEL -> { wasOver = false; pullTo(0f); hideTarget() }
                MotionEvent.ACTION_UP -> {
                    val drop = overTarget(e.rawX, e.rawY); val tc = targetCenter(); wasOver = false
                    if (drop && tc != null) { pullAnim?.cancel(); dismiss(tc.first, tc.second, offX, offY); return@setOnTouchListener true }
                    hideTarget(); pullAnim?.cancel(); if (pull != 0f) { pull = 0f; place() }
                    // optionally the window sticks to the nearest side edge when let go
                    if (folded) snapBubble()
                    else if (prefs.getBoolean("float_snap", false)) {
                        val sw = resources.displayMetrics.widthPixels
                        params.x = if (params.x + root.width / 2 < sw / 2) 0 else (sw - root.width).coerceAtLeast(0); relayout()
                    }
                    prefs.edit().putInt("float_x", params.x).putInt("float_y", params.y).apply()
                    // a tap (no drag) anywhere on the window folds it to the round button, and a tap on the button opens it again
                    val tap = Math.abs(e.rawX - downX) < 8 * dp && Math.abs(e.rawY - downY) < 8 * dp
                    if (tap && wasHidden) Unit // the first tap only brings the buttons back
                    else if (tap) { prefs.edit().putBoolean("float_min", !folded).apply(); applyLayout(); if (!folded) Unit else snapBubble() }
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
        val breath = android.animation.ValueAnimator.ofFloat(0.92f, 1f).apply {
            duration = 900; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { bubble.scaleX = it.animatedValue as Float; bubble.scaleY = bubble.scaleX }
        }

        scope.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { breath.cancel() }
        var litNow = 0; var litShown = -1; var sig = ""
        var bubbleCover: android.graphics.Bitmap? = null
        scope.launch {
            while (isActive) {
                // a setting changed in Ajustes: the window follows within a quarter of a second
                val now2 = listOf("float_scale", "float_bar", "float_bubble", "float_min", "float_alpha", "float_bubble_dp", "float_w", "float_h", "float_next").joinToString("|") { prefs.all[it].toString() }
                if (now2 != sig && !closing) { sig = now2; applyLayout() }
                val state = Tracker.state.value
                val wantBreath = folded && state.playing
                if (wantBreath && !breath.isRunning) breath.start() else if (!wantBreath && breath.isRunning) { breath.cancel(); bubble.scaleX = 1f; bubble.scaleY = 1f }
                val color = Tracker.accent.value.takeIf { it != 0 } ?: 0xFF455A64.toInt()
                // the notification's colour, darkened so white text stays readable
                bg.update(Color.argb(255, (Color.red(color) * 0.55f).toInt(), (Color.green(color) * 0.55f).toInt(), (Color.blue(color) * 0.55f).toInt()), Tracker.cover)
                if (bubbleCover !== Tracker.cover) { bubbleCover = Tracker.cover; bubble.setImageBitmap(bubbleCover) }
                val fg = if (bg.light && bg.opacity > 0.55f) Color.rgb(20, 20, 20) else Color.WHITE // over a see-through window the text is white with a shadow
                if (now.currentTextColor != fg) {
                    now.setTextColor(fg); prev.setTextColor(Color.argb(150, Color.red(fg), Color.green(fg), Color.blue(fg))); next.setTextColor(Color.argb(190, Color.red(fg), Color.green(fg), Color.blue(fg)))
                    close.setTextColor(fg); modeBtn.setTextColor(fg); bigger.setTextColor(fg); smaller.setTextColor(fg); resize.setTextColor(fg)
                }
                var before = ""
                val (a, b) = when (state.status) {
                    Status.SYNCED -> {
                        val posNow = Tracker.positionMs()
                        val i = Lrc.indexAt(state.lines, posNow)
                        before = if (i > 0) state.lines[i - 1].text else ""
                        litNow = if (i >= 0 && Tracker.karaoke) Lrc.litChars(state.lines[i].text, state.lines[i].timeMs, state.lines.getOrNull(i + 1)?.timeMs, posNow, state.lines[i].endMs, state.lines[i].marks) else 0
                        (if (i < 0) "♪ …" else state.lines[i].text.ifBlank { "♪" }) to (Tracker.translations.value[i]?.let { "↳ $it" } ?: state.lines.getOrNull(i + 1)?.text.orEmpty())
                    }
                    Status.PLAIN -> "Letra sin sincronizar" to (state.track?.title.orEmpty())
                    Status.LOADING -> "Buscando la letra…" to (state.track?.title.orEmpty())
                    Status.NOT_FOUND -> "No hay letra para esta canción" to (state.track?.title.orEmpty())
                    Status.ERROR -> "No se pudo cargar la letra" to ""
                    Status.NO_ACCESS -> "Falta el acceso a notificaciones" to ""
                    Status.NO_MEDIA -> "♪" to "Pon una canción"
                }
                if (now.text.toString() != a && !folded && now.text.isNotEmpty()) {
                    // a new line rolls up into place: a short rise and fade, inside the window padding so nothing is clipped
                    texts.animate().cancel()
                    texts.translationY = 8 * dp; texts.alpha = 0.25f
                    texts.animate().translationY(0f).alpha(1f).setDuration(260).setInterpolator(android.view.animation.DecelerateInterpolator(1.8f)).start()
                }
                if (now.text.toString() != a || litNow != litShown) {
                    litShown = litNow
                    now.text = if (litNow > 0 && litNow <= a.length) android.text.SpannableString(a).apply {
                        // the unsung rest is dimmer: the lit words keep the normal colour
                        setSpan(android.text.style.ForegroundColorSpan(Color.argb(150, Color.red(fg), Color.green(fg), Color.blue(fg))), litNow, a.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else a
                }
                if (prev.text.toString() != before) prev.text = before
                prev.visibility = if (before.isBlank() || bar) View.GONE else View.VISIBLE
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
        /** True while Q Lyrics is on screen; the bubble never opens by itself over its own app. */
        @Volatile var appForeground = false
        /** Set when the person closes the bubble while music plays, so the auto-open does not bring it straight back. */
        @Volatile var suppressAuto = false
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
            if (running) suppressAuto = true
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
