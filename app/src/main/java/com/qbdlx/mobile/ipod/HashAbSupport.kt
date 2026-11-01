package com.qbdlx.mobile.ipod

// Helpers used by the generated hashAB code (HashAbGen*.kt). See THIRD_PARTY.md for where the algorithm comes from.

/** Logical shifts on 32-bit values; a count of 32 or more gives 0, as in C-style masked arithmetic. */
internal fun shl(a: Int, n: Int): Int = if (n in 0..31) a shl n else 0
internal fun shr(a: Int, n: Int): Int = if (n in 0..31) a ushr n else 0

internal class InputSeed(val raw: Int, val doubled: Int, val maskedAnd: Int, val maskedOr: Int, val init: Int)

internal fun makeSeed(value: Int): InputSeed {
    val doubled = value * 2 + 0xD1EB870E.toInt()
    return InputSeed(
        raw = value,
        doubled = doubled,
        maskedAnd = doubled and 0xA50FF288.toInt(),
        maskedOr = doubled or 0xA50FF288.toInt(),
        init = 0x44824335 + ((-0x2D7806BC) xor ((-0x170A3C79) + value)),
    )
}

private const val SLOT_MUL = -0x362C9C11
private const val SLOT_INV = 0x362C9C11
private const val COEFF_XOR = 0x49D363EF
private const val SLOT_INNER_MUL = -0x1E6C2B0F
private const val COEFF_AND = 0x6C593822
private const val INNER_CONST_AND = -0x2E079947

internal fun slot_mix_add(slotValue: Int, mixValue: Int, mixMask: Int, offset: Int, tweak: Int): Int {
    val diff = (mixMask - mixValue) * 2 + tweak
    val gate = diff and (slotValue * 0x3CD8561E + 0x5C0F3290)
    return (offset - slotValue) + mixValue * SLOT_MUL + mixMask * SLOT_INV + gate * SLOT_MUL
}

internal fun slot_mix_or(slotValue: Int, term1: Int, term2: Int, offset: Int): Int =
    slotValue + offset + term1 + (term2 or (slotValue * (-0x3CD8561E) + 0xA3F0CD6F.toInt())) * SLOT_MUL

internal fun slot_mix_xor(slotValue: Int, term1: Int, term2: Int, offset: Int, xorConst: Int, innerConst: Int): Int =
    offset - COEFF_XOR * (xorConst xor (innerConst + term1 + slotValue * SLOT_INNER_MUL + term2 * 2))

internal fun slot_mix_and(slotValue: Int, term1Outer: Int, term1Inner: Int, term2: Int, offset: Int, mask: Int): Int =
    (offset + slotValue + term1Outer + COEFF_AND * term2) -
        COEFF_AND * (mask and (INNER_CONST_AND + term1Inner + slotValue * SLOT_INNER_MUL + term2 * 2))

internal fun masked_step(value: Int, addend: Int, bias: Int, mask: Int): Int =
    (value + addend) - ((value * 2 + bias) and mask)

internal fun masked_gate(value: Int, gateBias: Int, mask: Int): Int = (value * 2 + gateBias) and mask
