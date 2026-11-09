package com.qbdlx.mobile

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.media.AudioFocusRequest
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink

/**
 * Plays one track after another without a gap, and fades between them when a fade length is set. Everything runs on
 * the main thread. The app decides *what* plays next (the queue) and hands the engine the next track's address ahead
 * of time through [setNext]; the engine only does the audio.
 *
 *  - No fade: the next track is added behind the current one in the same player, so ExoPlayer joins them without a
 *    gap (as far as the files allow: a change of sample rate or bit depth needs the audio output to restart).
 *  - Fade: the next track is prepared in a second player; near the end it starts silent and the two are cross-faded
 *    with equal-power gains ([fadeGains]), then the roles swap.
 */
class PlayerEngine(private val context: Context, private val listener: Listener) {
    interface Listener {
        fun onPlayingChanged(playing: Boolean)
        /** The engine moved on to the prepared next track by itself. */
        fun onAdvanced()
        /** The track ended and no next track was prepared. */
        fun onEnded()
        fun onError(e: Exception)
        /** Bit-perfect output changed state (only while the mode is on). */
        fun onBitPerfect(state: BitPerfectState) {}
    }

    private val main = Handler(Looper.getMainLooper())
    private val output = BitPerfectOutput(context)
    private var restartedItem = false // the track was already re-created once for this song

    /**
     * Bit-perfect USB output (Android 14+). Needs the stream untouched, so there is no fade, and the phone's equalizer
     * apps are not told about the audio session (they would attach effects). Turning it off hands the mixer back.
     */
    var bitPerfect = false
        set(v) {
            if (v == field) return
            field = v
            if (!v) output.clear()
            announceSession(active.audioSessionId, !v)
            clearNext()
            listener.onBitPerfect(if (v) output.probe() else BitPerfectState(BitPerfectKind.OFF))
        }

    private var active: ExoPlayer = build()
    private var idle: ExoPlayer? = null
    private var nextUrl: String? = null
    private var fading = false
    private var fadeLenMs = 0L

    /** Fade length in milliseconds; 0 = none. Changing it drops the prepared next track so the app can prepare it again the right way. */
    var fadeMs = 0
        set(v) { if (v != field) { field = v; clearNext() } }

    /** Repeat the current track forever (done by the player itself, so there is no gap). Also drops the prepared next track. */
    var repeatOne = false
        set(v) { if (v != field) { field = v; active.repeatMode = if (v) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF; clearNext() } }

    val position: Long get() = active.currentPosition
    /** The length of the current track in milliseconds, or -1 while unknown. */
    val duration: Long get() = active.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: -1
    val isPlaying: Boolean get() = active.isPlaying || (fading && idle?.isPlaying == true)
    val hasNext: Boolean get() = nextUrl != null
    /** A fade is running: the prepared track is already playing, so the queue must not be changed under it. */
    val isFading: Boolean get() = fading
    private val crossfading: Boolean get() = fadeMs > 0 && !repeatOne && !bitPerfect

    private val ticker = object : Runnable {
        override fun run() { tick(); main.postDelayed(this, TICK_MS) }
    }

    init { main.postDelayed(ticker, TICK_MS) }

    // Audio focus is handled here, not by ExoPlayer: the second player of a crossfade would take it from the first.
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var resumeOnGain = false
    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnGain) { resumeOnGain = false; active.play(); if (fading) idle?.play() }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    resumeOnGain = isPlaying; active.pause(); if (fading) idle?.pause()
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resumeOnGain = false; active.pause(); if (fading) idle?.pause()
                    if (UsbDirect.prefs(context).enabled) UsbDirect.managerOrNull(context)?.releaseClaim(delayMs = 0)
                    audioManager.abandonAudioFocusRequest(focusRequest)
                }
            }
        }, main)
        .build()

    private fun takeFocus() { audioManager.requestAudioFocus(focusRequest) }
    private fun dropFocus() { resumeOnGain = false; audioManager.abandonAudioFocusRequest(focusRequest) }

    /** The audio session of the player that is playing, for the phone's equalizer. */
    val audioSessionId: Int get() = active.audioSessionId

    /** Tells the phone's equalizer apps (Samsung's, Wavelet...) that this app plays music in [session], so they can act on it. */
    private fun announceSession(session: Int, open: Boolean) {
        context.sendBroadcast(
            Intent(if (open) AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION else AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session).putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC),
        )
    }

    /** Wraps the normal audio output so tracks the USB DAC can take go straight to it (see [UsbDirect]). */
    @androidx.annotation.OptIn(UnstableApi::class)
    // Lazy: `active` below is built while the class initialises, before this declaration would run.
    private val renderers by lazy { object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
            super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)!!.let { normal ->
                // Only wrap when the user turned the driver on; a failure to start it never takes the app down.
                val usb = if (UsbDirect.prefs(context).enabled) UsbDirect.managerOrNull(context) else null
                if (usb == null) normal else org.ostrichbat2k.usbdac.streaming.RoutingAudioSink(usb, normal)
            }
    } }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun build(): ExoPlayer = ExoPlayer.Builder(context, renderers)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
        .setHandleAudioBecomingNoisy(true) // unplugging headphones pauses
        .build()
        .also { p -> p.addListener(Events(p)); watchOutput(p); if (!bitPerfect) announceSession(p.audioSessionId, true) }

    /** Once the player has created its AudioTrack we know the real format: match the USB mixer to it. */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun watchOutput(p: ExoPlayer) {
        p.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
                if (!bitPerfect || p !== active) return
                val r = output.prepare(audioTrackConfig.sampleRate, audioTrackConfig.encoding, audioTrackConfig.channelConfig)
                main.post { listener.onBitPerfect(r.state) }
                if (r.changed && !restartedItem) {
                    restartedItem = true
                    // The track was made under the old mixer: make it again under the new one, at the same spot.
                    main.post { val pos = p.currentPosition; p.stop(); p.prepare(); p.seekTo(pos) }
                }
            }
        })
    }

    private inner class Events(private val player: ExoPlayer) : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { listener.onPlayingChanged(this@PlayerEngine.isPlaying) }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (player !== active || reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
            // Gapless: the player moved on to the item that was added behind the finished one.
            nextUrl = null
            restartedItem = false
            main.post { if (active.currentMediaItemIndex > 0) active.removeMediaItems(0, active.currentMediaItemIndex) }
            listener.onAdvanced()
        }

        override fun onPlaybackStateChanged(state: Int) {
            PlaybackLog.add(context, "estado ${when (state) { Player.STATE_IDLE -> "IDLE"; Player.STATE_BUFFERING -> "BUFFERING"; Player.STATE_READY -> "READY"; else -> "ENDED" }}${if (player !== active) " (siguiente)" else ""}")
            if (player !== active || state != Player.STATE_ENDED || fading) return
            // The track ended. If a faded transition was prepared but never started (too late), switch now.
            if (idle?.playbackState == Player.STATE_READY && nextUrl != null) swapNow() else listener.onEnded()
        }

        override fun onPlayerError(error: PlaybackException) {
            PlaybackLog.add(context, "ERROR ${error.errorCodeName}: ${error.message} / ${error.cause?.javaClass?.simpleName}: ${error.cause?.message}")
            if (player === active) listener.onError(error)
            else { nextUrl = null; idle?.stop(); idle?.clearMediaItems() } // the prepared track failed: the app fetches it normally later
        }
    }

    /** Replaces everything with [url] and plays it from the start. */
    fun start(url: String) {
        PlaybackLog.add(context, "start ${url.substringBefore('?').takeLast(60)}")
        resetAll()
        restartedItem = false
        active.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        active.setMediaItem(MediaItem.fromUri(url))
        active.prepare()
        takeFocus()
        active.play()
    }

    /** The address of the track that follows the current one, to be ready in time. */
    fun setNext(url: String) {
        if (fading) return
        clearNext()
        nextUrl = url
        if (crossfading) {
            val p = idle ?: build().also { idle = it }
            p.volume = 0f
            p.setMediaItem(MediaItem.fromUri(url))
            p.prepare()
            p.playWhenReady = false
        } else {
            active.addMediaItem(MediaItem.fromUri(url))
        }
    }

    fun clearNext() {
        if (fading) return // too late to take back: the fade finishes first
        nextUrl = null
        idle?.stop(); idle?.clearMediaItems()
        while (active.mediaItemCount > active.currentMediaItemIndex + 1) active.removeMediaItem(active.mediaItemCount - 1)
    }

    fun pause() { active.pause(); if (fading) idle?.pause(); dropFocus() }
    fun resume() { takeFocus(); active.play(); if (fading) idle?.play() }
    fun togglePlay() { if (isPlaying) pause() else resume() }

    /** Ignored while a fade is running (it lasts a few seconds at most). */
    fun seekTo(ms: Long) { if (!fading) active.seekTo(ms.coerceAtLeast(0)) }

    fun stop() { resetAll(); dropFocus(); output.clear() }

    fun release() {
        main.removeCallbacks(ticker)
        announceSession(active.audioSessionId, false); idle?.let { announceSession(it.audioSessionId, false) }
        active.release(); idle?.release(); idle = null
        dropFocus(); output.clear()
    }

    private fun resetAll() {
        fading = false
        nextUrl = null
        idle?.let { it.stop(); it.clearMediaItems(); it.volume = 0f }
        active.stop(); active.clearMediaItems(); active.volume = 1f
    }

    private fun tick() {
        val next = idle ?: return
        if (fading) {
            val progress = if (fadeLenMs > 0) next.currentPosition.toDouble() / fadeLenMs else 1.0
            val (out, inn) = fadeGains(progress)
            active.volume = out; next.volume = inn
            if (progress >= 1.0 || next.playbackState == Player.STATE_ENDED) finishFade()
        } else if (crossfading && nextUrl != null && active.isPlaying && next.playbackState == Player.STATE_READY &&
            shouldStartFade(active.currentPosition, active.duration.takeIf { it > 0 } ?: -1, fadeMs)
        ) {
            fadeLenMs = effectiveFadeMs(active.duration, fadeMs)
            next.volume = 0f
            next.play()
            fading = true
        }
    }

    private fun finishFade() {
        fading = false
        swapNow()
    }

    /** The prepared player becomes the current one; the old one is emptied. */
    private fun swapNow() {
        val incoming = idle ?: return
        val old = active
        active = incoming
        idle = old
        old.stop(); old.clearMediaItems(); old.volume = 1f
        active.volume = 1f
        active.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (!active.isPlaying) active.play()
        nextUrl = null
        listener.onAdvanced()
    }

    private companion object { const val TICK_MS = 80L }
}
