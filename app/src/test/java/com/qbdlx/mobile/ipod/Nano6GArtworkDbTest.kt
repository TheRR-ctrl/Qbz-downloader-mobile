package com.qbdlx.mobile.ipod

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Uses a copy of the real nano 6G ArtworkDB (diagnostic of the user's iPod) as a fixture; the fixture itself is never written. */
class Nano6GArtworkDbTest {
    private fun fixture(): ByteArray = javaClass.getResourceAsStream("/ipod/ArtworkDB.nano6g")!!.readBytes()

    /** Sizes of the four real .ithmb files of the diagnostic. */
    private val ithmb = mapOf(1073 to 110_361_600L, 1085 to 6_381_056L, 1074 to 2_060_000L, 1089 to 2_771_936L)

    @Test fun readsTheRealFile() {
        val db = Nano6GArtworkDb.parse(fixture())
        assertEquals(412, db.all.size)
        assertEquals(mapOf(1073 to 115200, 1085 to 15488, 1074 to 5000, 1089 to 6728), db.profiles)
        assertEquals(512, db.nextId)
        assertEquals(100, db.all.first().id); assertEquals(511, db.all.last().id)
        assertEquals(958, db.all.sumOf { i -> i.frames.count { it.format == 1073 } })
        assertEquals(412, db.all.sumOf { i -> i.frames.count { it.format == 1085 } })
    }

    @Test fun serialisingWithoutChangesGivesTheSameBytes() {
        val original = fixture()
        assertArrayEquals(original, Nano6GArtworkDb.parse(original).serialize())
    }

    @Test fun realReferencesAreAllValid() {
        assertEquals(emptyList<String>(), Nano6GArtworkDb.parse(fixture()).validate(ithmb))
    }

    @Test fun addsOneImageWithFourFramesAndKeepsEverythingElse() {
        val original = fixture()
        val db = Nano6GArtworkDb.parse(original)
        val before = db.all.map { it.raw.toList() }
        val offsets = mapOf(1085 to 6_381_056L, 1073 to 110_361_600L, 1074 to 2_060_000L, 1089 to 2_771_936L)
        val img = db.add(NewArtwork(songPid = 123456789L, offsets = offsets, references = 2))
        assertEquals(512, img.id); assertEquals(513, db.nextId)
        assertEquals(listOf(1085, 1073, 1074, 1089), img.frames.map { it.format })
        assertEquals(offsets, img.frames.associate { it.format to it.offset })
        val out = db.serialize()
        val again = Nano6GArtworkDb.parse(out)
        assertEquals(413, again.all.size); assertEquals(513, again.nextId)
        assertEquals(before, again.all.take(412).map { it.raw.toList() }) // the 412 old records are byte-identical
        assertEquals(123456789L, again.find(512)!!.songPid)
        // the grown files make every reference valid
        val grown = mapOf(1085 to 6_381_056L + 15488, 1073 to 110_361_600L + 115200, 1074 to 2_060_000L + 5000, 1089 to 2_771_936L + 6728)
        assertEquals(emptyList<String>(), again.validate(grown))
        // and without growing them it is detected
        assertTrue(again.validate(ithmb).isNotEmpty())
        // header: total size and next id patched, the rest of the file identical
        val b = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(out.size, b.getInt(8)); assertEquals(513, b.getInt(28))
        assertArrayEquals(original.copyOfRange(0x73488, original.size), out.copyOfRange(0x73488 + img.raw.size, out.size))
    }

    @Test fun newRecordHasTheLayoutOfRealOnes() {
        val db = Nano6GArtworkDb.parse(fixture())
        val real = db.all.first { it.frames.size == 4 }
        val img = db.add(NewArtwork(1L, mapOf(1085 to 1L * 15488, 1073 to 0L, 1074 to 0L, 1089 to 0L), 1))
        assertEquals(real.raw.size, img.raw.size)
        assertEquals(real.frames.map { it.width to it.height }, img.frames.map { it.width to it.height })
    }

    @Test fun idsStayUniqueWhenAddingSeveral() {
        val db = Nano6GArtworkDb.parse(fixture())
        repeat(5) { db.add(NewArtwork(it.toLong(), mapOf(1085 to 0L, 1073 to 0L, 1074 to 0L, 1089 to 0L), 1)) }
        assertEquals((100..516).toList(), db.all.map { it.id })
        assertEquals(517, db.nextId)
    }

    @Test fun rejectsAnIdCollision() {
        val d = fixture()
        ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).putInt(28, 300) // next id inside the used range
        assertThrows(ArtworkDbException::class.java) { Nano6GArtworkDb.parse(d) }
    }

    @Test fun rejectsDamagedOrUnknownStructures() {
        val d = fixture()
        assertThrows(ArtworkDbException::class.java) { Nano6GArtworkDb.parse(d.copyOf(d.size - 10)) }
        val badTag = d.copyOf(); badTag[0x84] = 'x'.code.toByte()
        assertThrows(ArtworkDbException::class.java) { Nano6GArtworkDb.parse(badTag) }
        val badMhod = d.copyOf(); ByteBuffer.wrap(badMhod).order(ByteOrder.LITTLE_ENDIAN).putInt(0x140 + 152 + 12, 9) // unknown mhod type
        assertThrows(ArtworkDbException::class.java) { Nano6GArtworkDb.parse(badMhod) }
        assertThrows(ArtworkDbException::class.java) { Nano6GArtworkDb.parse(ByteArray(10)) }
    }
}
