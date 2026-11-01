package com.qbdlx.mobile.ipod

/**
 * The device's FireWire GUID (8 bytes), which the hashAB signature needs. It is stored as 16 hex digits in
 * `iPod_Control/Device/SysInfoExtended` (an XML plist) or `SysInfo` (plain text). Follows ipodsync's `sysinfo.py`.
 */
object IpodSysInfo {
    private val KEYED = Regex("""(?:FireWireGUID|FirewireGuid)\D{0,40}?(?:0x)?([0-9A-Fa-f]{16})""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val BARE = Regex("""0x([0-9A-Fa-f]{16})""")
    private val HEX16 = Regex("""[0-9a-f]{16}""")

    /** The GUID found in the text of a SysInfo or SysInfoExtended file, or null. */
    fun parse(text: String): ByteArray? {
        val hex = (KEYED.find(text) ?: BARE.find(text))?.groupValues?.get(1) ?: return null
        return fromHex(hex)
    }

    /** The GUID typed by the user ("0x" and spaces allowed); null unless it is exactly 16 hex digits. */
    fun fromUserInput(input: String): ByteArray? {
        val s = input.trim().lowercase().removePrefix("0x").replace(" ", "")
        return if (HEX16.matches(s)) fromHex(s) else null
    }

    /** The GUID from a USB serial number such as "0123456789ABCDEF..." (the first 16 hex digits), or null. */
    fun fromUsbSerial(serial: String?): ByteArray? {
        val m = Regex("""([0-9A-Fa-f]{16})""").find(serial.orEmpty()) ?: return null
        return fromHex(m.groupValues[1])
    }

    fun toHex(guid: ByteArray) = guid.joinToString("") { "%02x".format(it) }

    private fun fromHex(h: String) = ByteArray(8) { h.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
}
