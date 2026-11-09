package com.qbdlx.mobile

import android.content.Context
import org.ostrichbat2k.usbdac.UsbAudioManager
import org.ostrichbat2k.usbdac.UsbAudioManagerImpl
import org.ostrichbat2k.usbdac.UsbDacPreferences

/**
 * Direct USB DAC output (libusbdac, GPL-3): the app talks to the DAC itself over USB, skipping Android's mixer,
 * which on many phones offers no bit-perfect mode at all. One switch ("usb_direct") turns it on; the library
 * claims the DAC only while it is on, and anything the DAC cannot take plays through the normal path.
 */
class UsbDirectPrefs(context: Context) : UsbDacPreferences {
    private val prefs = context.getSharedPreferences("qbdlx", 0)
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<UsbDacPreferences.Listener>()

    override val enabled: Boolean get() = prefs.getBoolean(KEY, false)
    override val bitPerfectMode: Boolean get() = enabled
    override var volumeDb: Int?
        get() = if (prefs.contains("usb_volume_db")) prefs.getInt("usb_volume_db", 0) else null
        set(v) { prefs.edit().apply { if (v == null) remove("usb_volume_db") else putInt("usb_volume_db", v) }.apply() }
    override val abxTestMode: Boolean get() = false
    override val volumeStepPercent: Int get() = 2
    override val maxVolumePercent: Int get() = prefs.getInt("usb_max_volume", 60).coerceIn(1, 100)
    override val manualReleaseControl: Boolean get() = false

    override fun registerListener(listener: UsbDacPreferences.Listener) { listeners.add(listener) }
    override fun unregisterListener(listener: UsbDacPreferences.Listener) { listeners.remove(listener) }

    fun set(on: Boolean) {
        prefs.edit().putBoolean(KEY, on).apply()
        listeners.forEach { it.onUsbEnabledChanged(); it.onBitPerfectModeChanged() }
    }

    companion object { const val KEY = "usb_direct" }
}

object UsbDirect {
    @Volatile private var prefsRef: UsbDirectPrefs? = null
    @Volatile private var managerRef: UsbAudioManager? = null

    fun prefs(context: Context): UsbDirectPrefs =
        prefsRef ?: synchronized(this) { prefsRef ?: UsbDirectPrefs(context.applicationContext).also { prefsRef = it } }

    fun manager(context: Context): UsbAudioManager =
        managerRef ?: synchronized(this) {
            managerRef ?: UsbAudioManagerImpl(context.applicationContext, prefs(context)).also { it.start(); managerRef = it }
        }
}
