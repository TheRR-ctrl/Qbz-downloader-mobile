package com.qbdlx.mobile.ipod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IpodSysInfoTest {
    @Test fun readsAPlainSysInfoFile() {
        val text = "BoardHwName: iPod M43\nModelNumStr: xA899\nFirewireGuid: 0x000A27001234ABCD\nvisibleBuildID: 1.0\n"
        assertEquals("000a27001234abcd", IpodSysInfo.toHex(IpodSysInfo.parse(text)!!))
    }

    @Test fun readsAnXmlPlist() {
        val plist = """<plist><dict><key>FireWireGUID</key>
            <string>000A27001234ABCD</string><key>Other</key><string>1</string></dict></plist>"""
        assertEquals("000a27001234abcd", IpodSysInfo.toHex(IpodSysInfo.parse(plist)!!))
    }

    @Test fun aBareHexValueIsEnough() {
        assertEquals("000a270024bd17a1", IpodSysInfo.toHex(IpodSysInfo.parse("serial 0x000A270024BD17A1")!!))
    }

    @Test fun nothingUsefulGivesNull() {
        assertNull(IpodSysInfo.parse("")); assertNull(IpodSysInfo.parse("FirewireGuid: 0x1234")); assertNull(IpodSysInfo.parse("no guid here"))
    }

    @Test fun userInputMustBeExactlySixteenHexDigits() {
        assertEquals("000a270024bd17a1", IpodSysInfo.toHex(IpodSysInfo.fromUserInput(" 0x000A270024BD17A1 ")!!))
        assertEquals("000a270024bd17a1", IpodSysInfo.toHex(IpodSysInfo.fromUserInput("000a2700 24bd17a1")!!))
        assertNull(IpodSysInfo.fromUserInput("000a270024bd17a")); assertNull(IpodSysInfo.fromUserInput("000a270024bd17a1ff"))
        assertNull(IpodSysInfo.fromUserInput("zzzzzzzzzzzzzzzz")); assertNull(IpodSysInfo.fromUserInput(""))
    }

    @Test fun theGuidIsTakenFromAUsbSerial() {
        assertEquals("0123456789abcdef", IpodSysInfo.toHex(IpodSysInfo.fromUsbSerial("0123456789ABCDEF0123456789ABCDEF01234567")!!))
        assertNull(IpodSysInfo.fromUsbSerial(null)); assertNull(IpodSysInfo.fromUsbSerial("short"))
    }

    @Test fun theGuidIsEightBytes() { assertEquals(8, IpodSysInfo.parse("0x000A270024BD17A1")!!.size) }
}
