package com.qbdlx.mobile.ipod

/**
 * hashAB: the anti-tamper signature of the iPod nano 6G/7G library. A public-domain algorithm
 * (https://github.com/DigiDNA/hashab); this is a Kotlin port of ipodsync's Python port of it
 * (MIT, https://github.com/buldiei/ipodsync). Checked against the 100 reference vectors in HashAbTest.
 *
 * Input: SHA-1 of the protected data (20 bytes), the device's FireWire GUID (8 bytes) and 23 bytes of randomness.
 * Output: 57 bytes. All arrays here hold unsigned byte values (0..255) in an IntArray.
 */
object HashAb {
    const val SHA1_LEN = 20
    const val UUID_LEN = 8
    const val RND_LEN = 23
    const val OUTPUT_LEN = 57

    fun sign(sha1: ByteArray, uuid: ByteArray, rnd: ByteArray): ByteArray {
        require(sha1.size == SHA1_LEN) { "sha1 must be $SHA1_LEN bytes, not ${sha1.size}" }
        require(uuid.size == UUID_LEN) { "uuid must be $UUID_LEN bytes, not ${uuid.size}" }
        require(rnd.size == RND_LEN) { "rnd must be $RND_LEN bytes, not ${rnd.size}" }
        return calc(sha1.unsigned(), uuid.unsigned(), rnd.unsigned()).let { out -> ByteArray(out.size) { out[it].toByte() } }
    }

    private fun ByteArray.unsigned() = IntArray(size) { this[it].toInt() and 0xFF }

    private val INPUT_SBOX by lazy { HashAbTables.get("INPUT_SBOX") }
    private val NIBBLE_SBOX_EVEN by lazy { HashAbTables.get("NIBBLE_SBOX_EVEN") }
    private val NIBBLE_SBOX_ODD by lazy { HashAbTables.get("NIBBLE_SBOX_ODD") }
    private val NIBBLE_SBOX_MAIN by lazy { HashAbTables.get("NIBBLE_SBOX_MAIN") }
    private val FINAL_SBOX by lazy { HashAbTables.get("FINAL_SBOX") }
    private val OUTPUT_SBOX by lazy { HashAbTables.get("OUTPUT_SBOX") }
    private val ROUND_KEYS by lazy { HashAbTables.get("ROUND_KEYS") }
    private val MIXCOL_STATE by lazy { HashAbTables.get("MIXCOL_STATE") }
    private val MIXCOL_MULT by lazy { HashAbTables.get("MIXCOL_MULT") }
    private val FINAL_PERM by lazy { HashAbTables.get("FINAL_PERM") }
    private val WB_STATE_EXTRACT by lazy { HashAbTables.get("WB_STATE_EXTRACT") }
    private val WB_INPUT_A by lazy { HashAbTables.get("WB_INPUT_A") }
    private val WB_INPUT_B by lazy { HashAbTables.get("WB_INPUT_B") }
    private val WB_MIX_A by lazy { HashAbTables.get("WB_MIX_A") }
    private val WB_MIX_B by lazy { HashAbTables.get("WB_MIX_B") }
    private val WB_T_TABLES by lazy { HashAbTables.get("WB_T_TABLES") }
    private val WB_T_MIX by lazy { HashAbTables.get("WB_T_MIX") }
    private val WB_FINAL_SBOX by lazy { HashAbTables.get("WB_FINAL_SBOX") }

    private fun u8(x: Int) = x and 0xFF

    private fun calc(sha1: IntArray, uuid: IntArray, rnd: IntArray): IntArray {
        val p1 = phase1(uuid + sha1 + rnd + IntArray(29) { 0xA5 })
        val roundState = IntArray(8)
        val cipherBlock = IntArray(32) { p1[PERMUTATION_TABLE[it] + 44] }
        for (w in 0 until 8) {
            roundState[w] = cipherBlock[4 * w] or (cipherBlock[4 * w + 1] shl 8) or (cipherBlock[4 * w + 2] shl 16) or (cipherBlock[4 * w + 3] shl 24)
        }
        val target = IntArray(OUTPUT_LEN)
        target[0] = 3
        rnd.copyInto(target, 2)
        for (i in 0 until 23) {
            val x = target[i + 2]
            target[i + 2] = u8(69 * x + 118 * (x and 0x5D) + 17)
        }
        val expanded = generate_key_material(p1)
        val ibuff = generate_initial_buffer(expanded)
        phase2(roundState, ibuff, cipherBlock).copyInto(target, 25)
        finalPermute(target)
        return target
    }

    // ---- phase 1: CBC-MAC-like compression of 5 blocks of 16 bytes -------------------------------------------------
    private fun applySboxPair(table: IntArray, inp: Int, key: Int): Int {
        val high = table[(inp and 0xF0) + (key shr 4)]
        val low = table[256 + u8((key and 0xF) + 16 * inp)]
        return u8((high shl 4) or low)
    }

    private fun mixColumnByte(ms: IntArray, idx0: Int, idx1: Int, idx2: Int, idx3: Int, j: Int): Int {
        val base = j * 1536
        val a = ms[idx0 + j]; val b = ms[idx1 + j]; val c = ms[idx2 + j]; val d = ms[idx3 + j]
        val t1 = MIXCOL_MULT[(d and 0xF) + base + 768 + u8(c * 16)] and 0xF
        val t2 = MIXCOL_MULT[(b and 0xF) + base + 256 + u8(a * 16)]
        val low = MIXCOL_MULT[t1 + base + 1280 + u8(t2 * 16)] and 0xF
        val u1 = MIXCOL_MULT[(b shr 4) + (a and 0xF0) + base]
        val u2 = MIXCOL_MULT[(c and 0xF0) + base + 512 + (d shr 4)] and 0xF
        val high = MIXCOL_MULT[u8(u1 * 16) + base + 1024 + u2]
        return u8(low + 16 * high)
    }

    private fun phase1(input: IntArray): IntArray {
        var keyState = KEY_STATE_INIT.copyOf()
        val out = IntArray(80)
        for (block in 0 until 5) {
            val boff = block * 16
            val firstSbox = if (block != 0) NIBBLE_SBOX_ODD else NIBBLE_SBOX_EVEN
            val cipherState = IntArray(16) { applySboxPair(firstSbox, INPUT_SBOX[input[boff + it]], keyState[it]) }
            var roundOutput = IntArray(16) { applySboxPair(NIBBLE_SBOX_MAIN, cipherState[it], ROUND_KEYS[it]) }
            for (rnd in 1 until 10) {
                val perm = IntArray(16) { 4 * roundOutput[AES_SHIFT_ROWS[it]] + MIXCOL_OFFSETS[it % 4] }
                for (col in 0 until 4) {
                    for (j in 0 until 4) {
                        cipherState[col * 4 + j] = mixColumnByte(MIXCOL_STATE, perm[col * 4], perm[col * 4 + 1], perm[col * 4 + 2], perm[col * 4 + 3], j)
                    }
                }
                roundOutput = IntArray(16) { applySboxPair(NIBBLE_SBOX_MAIN, cipherState[it], ROUND_KEYS[rnd * 16 + it]) }
            }
            val permuted = IntArray(16) { roundOutput[AES_SHIFT_ROWS[it]] }
            for (i in 0 until 16) cipherState[i] = applySboxPair(NIBBLE_SBOX_MAIN, FINAL_SBOX[permuted[i]], ROUND_KEYS[160 + i])
            keyState = cipherState.copyOf()
            for (i in 0 until 16) out[boff + i] = OUTPUT_SBOX[cipherState[i]]
        }
        return out
    }

    // ---- phase 2: white-box AES over two 16-byte blocks -------------------------------------------------------------
    private fun phase2Transform(rs: IntArray, rsOffset: Int, inp: IntArray, tabIn: IntArray, tabMix: IntArray): IntArray {
        val rsBytes = IntArray(16)
        for (w in 0 until 4) for (b in 0 until 4) {
            val byteVal = (rs[rsOffset + w] ushr (b * 8)) and 0xFF
            rsBytes[w * 4 + b] = WB_STATE_EXTRACT[byteVal + (b shl 8) + (w shl 10)]
        }
        val inputTrans = IntArray(16) { tabIn[inp[it] + it * 256] }
        return IntArray(16) { i ->
            val base = i * 512
            val rsB = rsBytes[i]
            val inB = inputTrans[i]
            u8(16 * tabMix[(rsB and 0xF0) + base + (inB shr 4)] + (tabMix[u8(16 * rsB + (inB and 0xF)) + base + 256] and 0xF))
        }
    }

    private fun phase2(roundState: IntArray, ibuff: IntArray, cipherBlock0: IntArray): IntArray {
        val output = IntArray(32)
        val cipherBlock = cipherBlock0.copyOf()
        for (it in 0 until 2) {
            val inp = if (it == 0) ibuff else cipherBlock.copyOf()
            val tabIn = if (it == 0) WB_INPUT_A else WB_INPUT_B
            val tabMix = if (it == 0) WB_MIX_A else WB_MIX_B
            val out2 = phase2Transform(roundState, it * 4, inp, tabIn, tabMix)
            out2.copyInto(cipherBlock, 0)
            var mixState = out2.copyOf()
            for (rnd in 0 until 9) {
                val roundOffset = rnd * 4096
                val perm = IntArray(16) { 4 * (roundOffset + mixState[AES_SHIFT_ROWS[it]] + it * 256) }
                for (col in 0 until 4) {
                    val co = col * 4
                    val idx0 = perm[co + 1]; val idx1 = perm[co + 2]; val idx2 = perm[co + 3]; val idx3 = perm[co]
                    for (bi in 0 until 4) {
                        val block = rnd * 16 + col * 4 + bi
                        val t0 = WB_T_TABLES[idx1 + bi]
                        val t1 = WB_T_TABLES[idx3 + bi]
                        val t2 = WB_T_TABLES[idx0 + bi]
                        val t3 = WB_T_TABLES[idx2 + bi]
                        val hiNibT0 = NIBBLE_HI_TABLE[t0]
                        val hiNibT1 = NIBBLE_HI_TABLE[t1]
                        val loNibT2 = NIBBLE_LO_TABLE[t2]
                        val loNibT3 = NIBBLE_LO_TABLE[t3]
                        val t0x27 = u8(27 * t0)
                        val mixedT1 = u8(128 + (122 xor u8(-27 * t1)))
                        val b = block * 1536
                        val i1 = (b + 0 * 256 + (((((hiNibT1 shr 4) xor 0x09) and 0x0F) shl 4))) or ((loNibT2 xor 0x0E) and 0x0F)
                        val i3 = b + 1 * 256 + ((((mixedT1 and 0x0F) xor 0x0C) shl 4) or ((((5 * t2) and 0x0F) xor 0x06) and 0x0F))
                        val i2 = (b + 2 * 256 + (((((hiNibT0 shr 4) xor 0x09) and 0x0F) shl 4))) or ((loNibT3 xor 0x0E) and 0x0F)
                        val i0 = (b + 3 * 256 + (((((((t0x27 and 0x0F) + 7) and 0x0F) xor 0x1) shl 4)))) or LOW_SUB[t3 and 0x0F]
                        val i4 = (b + 4 * 256 + ((WB_T_MIX[i1] and 0x0F) shl 4)) or (WB_T_MIX[i2] and 0x0F)
                        val i5 = (b + 5 * 256 + ((WB_T_MIX[i3] and 0x0F) shl 4)) or (WB_T_MIX[i0] and 0x0F)
                        cipherBlock[co + bi] = u8(16 * WB_T_MIX[i4] + (WB_T_MIX[i5] and 0xF))
                    }
                }
                if (rnd < 8) mixState = cipherBlock.copyOfRange(0, 16)
            }
            val temp = IntArray(16) { WB_FINAL_SBOX[cipherBlock[AES_SHIFT_ROWS[it]] + it * 256] }
            temp.copyInto(cipherBlock, 0)
            cipherBlock.copyInto(output, it * 16, 0, 16)
        }
        return output
    }

    // ---- final permutation and byte mixing ----------------------------------------------------------------------------
    private fun finalPermute(target: IntArray) {
        val perm = IntArray(55) { FINAL_PERM[4 * it] or (FINAL_PERM[4 * it + 1] shl 8) or (FINAL_PERM[4 * it + 2] shl 16) or (FINAL_PERM[4 * it + 3] shl 24) }
        for (i in 0 until 55) {
            val p = perm[i] + 2
            val tmp = target[p]; target[p] = target[i + 2]; target[i + 2] = tmp
        }
        for (i in 0 until 55) {
            val x = target[i + 2]
            var y = u8(93 xor u8(13 * x))
            if (x % 2 == 1) y = y xor 0x80
            target[i + 2] = y
        }
    }
}
