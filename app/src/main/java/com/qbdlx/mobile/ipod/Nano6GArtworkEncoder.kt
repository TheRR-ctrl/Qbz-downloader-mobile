package com.qbdlx.mobile.ipod

/**
 * Experimental Nano 6G ITHMB pixel encoder.
 *
 * The device sample confirms four square RGB565 little-endian pixel profiles. This class only
 * converts pixels into tightly packed RGB565_LE frames. It deliberately does NOT append frames
 * to an ITHMB file or edit ArtworkDB: frame alignment/record linkage and transactional update
 * rules must be proven separately before any device write is enabled.
 */
object Nano6GArtworkEncoder {
    data class Profile(val fileName: String, val width: Int, val height: Int) {
        val bytesPerFrame: Int get() = width * height * 2
    }

    val profiles: List<Profile> = listOf(
        Profile("F1073_1.ithmb", 240, 240),
        Profile("F1085_1.ithmb", 88, 88),
        Profile("F1074_1.ithmb", 50, 50),
        Profile("F1089_1.ithmb", 58, 58),
    )

    /**
     * Encode one ARGB pixel array into RGB565 little-endian.
     * Input must already be resized/cropped to exactly the requested profile dimensions.
     */
    fun encodeArgb8888(argb: IntArray, profile: Profile): ByteArray {
        require(profile.width > 0 && profile.height > 0) { "Invalid artwork dimensions" }
        require(argb.size == profile.width * profile.height) {
            "Expected ${profile.width * profile.height} pixels, got ${argb.size}"
        }
        val out = ByteArray(profile.bytesPerFrame)
        var p = 0
        for (color in argb) {
            val r = (color ushr 16) and 0xff
            val g = (color ushr 8) and 0xff
            val b = color and 0xff
            val rgb565 = ((r ushr 3) shl 11) or ((g ushr 2) shl 5) or (b ushr 3)
            out[p++] = (rgb565 and 0xff).toByte()
            out[p++] = ((rgb565 ushr 8) and 0xff).toByte()
        }
        return out
    }

    /** Validates only the expected packed frame length; it cannot validate ArtworkDB offsets or frame placement. */
    fun validateFrame(frame: ByteArray, profile: Profile): Boolean {
        if (frame.size != profile.bytesPerFrame) return false
        // RGB565_LE pure red = 00 F8; green = E0 07; blue = 1F 00.
        return true
    }
}

/** Scales a square-cropped cover to the four profiles. [pixels] is ARGB, [w]×[h]; the frames come out as RGB565 little-endian. */
object Nano6GCoverFrames {
    /** Center-crop to a square, then area-average down to each profile (the covers are at least 300 px; scaling up is plain nearest). */
    fun build(pixels: IntArray, w: Int, h: Int): Map<Int, ByteArray> {
        require(pixels.size == w * h && w > 0 && h > 0)
        val side = minOf(w, h); val x0 = (w - side) / 2; val y0 = (h - side) / 2
        return Nano6GArtworkUpdater.FORMATS.associateWith { f ->
            val p = Nano6GArtworkEncoder.profiles.first { it.fileName == Nano6GArtworkUpdater.fileOf(f) }
            val out = IntArray(p.width * p.height)
            for (y in 0 until p.height) for (x in 0 until p.width) {
                val sx0 = x0 + x * side / p.width; val sx1 = maxOf(sx0 + 1, x0 + (x + 1) * side / p.width)
                val sy0 = y0 + y * side / p.height; val sy1 = maxOf(sy0 + 1, y0 + (y + 1) * side / p.height)
                var r = 0; var g = 0; var b = 0; var n = 0
                for (sy in sy0 until sy1) for (sx in sx0 until sx1) { val c = pixels[sy * w + sx]; r += c shr 16 and 255; g += c shr 8 and 255; b += c and 255; n++ }
                out[y * p.width + x] = (255 shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
            Nano6GArtworkEncoder.encodeArgb8888(out, p)
        }
    }
}
