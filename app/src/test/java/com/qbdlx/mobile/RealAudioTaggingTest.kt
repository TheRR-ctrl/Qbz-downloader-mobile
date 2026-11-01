package com.qbdlx.mobile

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Tags real audio files so an independent tool (ffprobe, mutagen) can check them. Runs only when a folder
 * is given with -Dqbz.audioDir=...; every *.flac / *.mp3 in it is written to <name>.tagged.<ext>, with
 * cover.jpg from the same folder as the cover when present. Nothing from the folder is ever committed.
 */
class RealAudioTaggingTest {
    @Test fun tagsEveryRealFileInTheFolder() {
        val path = System.getProperty("qbz.audioDir")
        assumeTrue("set -Dqbz.audioDir=<folder> to run this test", !path.isNullOrBlank() && File(path).isDirectory)
        val dir = File(path!!)

        val cover = File(dir, "cover.jpg").takeIf { it.exists() }?.let { Cover.sniff(it.readBytes()) }
        val tags = TrackTags(
            title = "Losing My Religion (Live)", artist = "R.E.M.", album = "Out of Time (Deluxe)", albumArtist = "R.E.M.",
            trackNumber = 3, trackTotal = 11, discNumber = 1, discTotal = 2, date = "1991-03-12", year = "1991",
            genre = "Rock", composer = "Bill Berry", copyright = "(c) 1991 Warner Records", isrc = "USWB19900001",
            barcode = "0093624899927", label = "Warner Records", explicit = false, mediaType = "album",
        )

        val inputs = dir.listFiles { f -> (f.extension == "flac" || f.extension == "mp3") && !f.name.contains(".tagged.") }!!.sortedBy { it.name }
        assertTrue("no .flac/.mp3 files found in $dir", inputs.isNotEmpty())
        for (input in inputs) {
            val out = File(dir, input.nameWithoutExtension + ".tagged." + input.extension)
            val errors = ArrayList<Throwable>()
            TaggingOutputStream(out.outputStream().buffered(), tags, cover, onTagError = { errors += it }).use { sink ->
                // Feed it like a network download: many chunks of an arbitrary size.
                input.inputStream().use { src ->
                    val buf = ByteArray(32 * 1024 + 11)
                    while (true) { val n = src.read(buf); if (n < 0) break; sink.write(buf, 0, n) }
                }
            }
            assertTrue("tagging errors on ${input.name}: $errors", errors.isEmpty())
            assertTrue("${out.name} was not written", out.length() > 0)
        }
    }
}
