package com.qbdlx.mobile

/**
 * Whether the quality is asked on every download. A saved choice wins. With none saved, someone who already picked a
 * quality in Settings keeps getting what they picked (no prompt), and someone who never chose anything is still asked,
 * as the app always did.
 */
fun initialAskQuality(savedAsk: Boolean?, hasSavedQuality: Boolean): Boolean = savedAsk ?: !hasSavedQuality
