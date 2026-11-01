package com.qbdlx.mobile.ipod

/**
 * The lookup tables of the hashAB algorithm (public domain, from https://github.com/DigiDNA/hashab), packed in
 * `hashab_tables.bin` by ipodsync (MIT, https://github.com/buldiei/ipodsync). Each table is an array of unsigned bytes.
 */
internal object HashAbTables {
    private val index: Map<String, Pair<Int, Int>> = mapOf(
        "FINAL_PERM" to Pair(0, 220),
        "FINAL_SBOX" to Pair(220, 256),
        "GEN190_SBOX" to Pair(476, 2048),
        "GEN190_STATE_TABLE" to Pair(2524, 2048),
        "IBUFF_KEY_TABLE" to Pair(4572, 256),
        "IBUFF_MIX_SEED_A" to Pair(4828, 256),
        "IBUFF_MIX_SEED_B" to Pair(5084, 256),
        "INPUT_SBOX" to Pair(5340, 256),
        "MIXCOL_MULT" to Pair(5596, 6144),
        "MIXCOL_STATE" to Pair(11740, 4096),
        "NIBBLE_SBOX_EVEN" to Pair(15836, 512),
        "NIBBLE_SBOX_MAIN" to Pair(16348, 512),
        "NIBBLE_SBOX_ODD" to Pair(16860, 512),
        "OUTPUT_SBOX" to Pair(17372, 256),
        "ROUND_KEYS" to Pair(17628, 176),
        "WB_FINAL_SBOX" to Pair(17804, 4096),
        "WB_INPUT_A" to Pair(21900, 4096),
        "WB_INPUT_B" to Pair(25996, 4096),
        "WB_MIX_A" to Pair(30092, 8192),
        "WB_MIX_B" to Pair(38284, 8192),
        "WB_STATE_EXTRACT" to Pair(46476, 4096),
        "WB_T_MIX" to Pair(50572, 245760),
        "WB_T_TABLES" to Pair(296332, 147456),
    )

    private val blob: ByteArray by lazy {
        (HashAbTables::class.java.classLoader!!.getResourceAsStream("hashab_tables.bin")
            ?: error("hashab_tables.bin is missing from the app")).use { it.readBytes() }
    }

    fun get(name: String): IntArray {
        val (offset, length) = index[name] ?: error("unknown hashAB table $name")
        return IntArray(length) { blob[offset + it].toInt() and 0xFF }
    }
}
