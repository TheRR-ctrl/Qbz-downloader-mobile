package com.qbdlx.mobile

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews

/** The home-screen widget: cover, title and previous / play-pause / next. Redrawn by [PlaybackService] whenever the song or the state changes. */
class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val c = QbdlxApp.playback
        runCatching { render(context, c?.nowPlaying, c?.isPlaying == true, null) }
    }

    /** The widget was resized: draw it again with the layout that fits the new size. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle) {
        val c = QbdlxApp.playback
        runCatching { render(context, c?.nowPlaying, c?.isPlaying == true, last.first, last.second) }
    }

    companion object {
        /** The last cover bitmaps drawn, so a resize can redraw without waiting for the next song change. */
        private var last: Pair<Bitmap?, Bitmap?> = null to null

        /** The layout for a widget of this size (dp): the biggest cover, text and buttons that fit both ways. */
        internal fun layoutFor(widthDp: Int, heightDp: Int): Int = when {
            // one cell tall: cover, text and the three buttons side by side, nothing wasted underneath
            heightDp < 120 -> if (widthDp >= 300) R.layout.widget_now_playing_rw else R.layout.widget_now_playing_r
            widthDp >= 400 && heightDp >= 192 -> R.layout.widget_now_playing_xl
            widthDp >= 310 && heightDp >= 140 -> R.layout.widget_now_playing_l
            widthDp >= 245 && heightDp >= 104 -> R.layout.widget_now_playing_m
            else -> R.layout.widget_now_playing_s
        }

        /** Redraws every widget on the home screen; [cover] is a small bitmap of the album cover, or null; [background] is a larger one for the faded backdrop. */
        fun render(context: Context, playing: Pair<Album, Track>?, isPlaying: Boolean, cover: Bitmap?, background: Bitmap? = cover) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
            if (ids.isEmpty()) return
            if (cover != null) last = cover to background
            for (id in ids) {
                val o = manager.getAppWidgetOptions(id)
                val views = RemoteViews(context.packageName, layoutFor(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250), o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 100)))
                try {
                    fill(context, views, playing, isPlaying, cover, background)
                    manager.updateAppWidget(id, views)
                } catch (e: Exception) {
                    val plain = RemoteViews(context.packageName, R.layout.widget_now_playing_r)
                    fill(context, plain, playing, isPlaying, null, null)
                    manager.updateAppWidget(id, plain)
                }
            }
        }

        private fun fill(context: Context, views: RemoteViews, playing: Pair<Album, Track>?, isPlaying: Boolean, cover: Bitmap?, background: Bitmap?) {
        views.setTextViewText(R.id.widget_title, playing?.second?.displayTitle ?: "QBDLX")
        views.setTextViewText(R.id.widget_artist, playing?.first?.artist ?: "")
        // a text that does not fit scrolls (a marquee only runs on a selected view)
        views.setImageViewResource(R.id.widget_play, if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        if (cover != null) views.setImageViewBitmap(R.id.widget_cover, cover) else if (playing == null) views.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher)
        if (cover != null) { views.setImageViewBitmap(R.id.widget_bg, background ?: cover); views.setViewVisibility(R.id.widget_bg, android.view.View.VISIBLE) }
        else if (playing == null) views.setViewVisibility(R.id.widget_bg, android.view.View.GONE)
        views.setOnClickPendingIntent(R.id.widget_prev, PlaybackService.actionIntent(context, PlaybackService.ACTION_PREVIOUS))
        views.setOnClickPendingIntent(R.id.widget_play, PlaybackService.actionIntent(context, PlaybackService.ACTION_TOGGLE))
        views.setOnClickPendingIntent(R.id.widget_next, PlaybackService.actionIntent(context, PlaybackService.ACTION_NEXT))
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }
    }
}
