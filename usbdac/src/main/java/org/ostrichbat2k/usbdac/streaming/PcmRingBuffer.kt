/*
 * Copyright (c) 2026 libusbdac Project
 * PcmRingBuffer.kt is part of libusbdac.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.ostrichbat2k.usbdac.streaming

import java.nio.ByteBuffer

/**
 * Byte ring buffer between the decoder (the player's playback thread, via [write]) and the USB
 * stream's real-time pull (the streaming thread, via [readOrZeroPad]).
 *
 * Sin candado (un productor y un consumidor): antes los dos compartían un `synchronized`, y medido en
 * el teléfono, al cambiar de app el hilo de envío llegó a esperar ~1 s. Si Android frena al hilo del
 * decodificador (prioridad normal) mientras tiene el candado, el hilo de audio (prioridad urgente)
 * se queda parado detrás: inversión de prioridad. Ahora cada lado avanza su propio contador total y
 * nadie espera a nadie. [clear] solo mueve el contador de lectura (con CAS); si coincide con una
 * lectura en curso, esa lectura se descarta y sale silencio.
 */
class PcmRingBuffer(capacityBytes: Int) {
    private val buffer = ByteArray(capacityBytes)
    private val cap = capacityBytes.toLong()
    /** Bytes escritos en total; solo lo avanza el productor. */
    private val writeTotal = java.util.concurrent.atomic.AtomicLong(0L)
    /** Bytes leídos (o descartados por [clear]) en total. */
    private val readTotal = java.util.concurrent.atomic.AtomicLong(0L)

    fun availableToRead(): Int {
        val r = readTotal.get()
        return (writeTotal.get() - r).coerceIn(0L, cap).toInt()
    }

    fun availableToWrite(): Int = (cap - availableToRead()).toInt()

    /** Writes as much of [src] (from its current position to its limit) as there's room for. */
    fun write(src: ByteBuffer): Int {
        val w = writeTotal.get()
        val free = cap - (w - readTotal.get()).coerceIn(0L, cap)
        val toWrite = minOf(src.remaining().toLong(), free).toInt()
        var written = 0
        var pos = (w % cap).toInt()
        while (written < toWrite) {
            val chunk = minOf(toWrite - written, buffer.size - pos)
            src.get(buffer, pos, chunk)
            pos = (pos + chunk) % buffer.size
            written += chunk
        }
        writeTotal.set(w + written)
        return written
    }

    /**
     * Fills the first [length] bytes of [dst] with buffered data, zero-padding (silence) if there
     * isn't enough yet. Returns how many bytes were genuinely read from the buffer.
     */
    fun readOrZeroPad(dst: ByteArray, length: Int = dst.size): Int {
        val r = readTotal.get()
        val avail = (writeTotal.get() - r).coerceIn(0L, cap)
        val toRead = minOf(length.toLong(), avail).toInt()
        var read = 0
        var pos = (r % cap).toInt()
        while (read < toRead) {
            val chunk = minOf(toRead - read, buffer.size - pos)
            System.arraycopy(buffer, pos, dst, read, chunk)
            pos = (pos + chunk) % buffer.size
            read += chunk
        }
        if (!readTotal.compareAndSet(r, r + read)) {
            // Un clear() llegó mientras se copiaba: lo leído ya no vale.
            dst.fill(0, 0, length)
            return 0
        }
        if (read < length) dst.fill(0, read, length)
        return read
    }

    fun clear() {
        while (true) {
            val r = readTotal.get()
            if (readTotal.compareAndSet(r, maxOf(r, writeTotal.get()))) return
        }
    }
}
