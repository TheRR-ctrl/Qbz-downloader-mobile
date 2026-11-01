package com.qbdlx.mobile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** An in-memory folder tree standing in for Android's DocumentsContract. */
private class FakeTreeOps : TreeOps {
    class Node(val id: String, val name: String, val isDir: Boolean, val parent: String?, val data: ByteArrayOutputStream = ByteArrayOutputStream())

    val nodes = LinkedHashMap<String, Node>().apply { put("root", Node("root", "", true, null)) }
    val log = CopyOnWriteArrayList<String>()
    @Volatile var slowMs = 0L
    @Volatile var failCreateFile: IOException? = null
    private var next = 1

    fun count(prefix: String) = log.count { it.startsWith(prefix) }

    @Synchronized private fun requireParent(parent: String) { if (parent !in nodes) throw FileNotFoundException("no such document $parent") }

    override fun listChildren(parentDocId: String): List<TreeChild> = synchronized(this) {
        requireParent(parentDocId)
        nodes.values.filter { it.parent == parentDocId }.map { TreeChild(it.id, it.name, it.isDir) }
    }

    private fun add(parent: String, name: String, dir: Boolean): String {
        if (slowMs > 0) Thread.sleep(slowMs) // widen the window in which two workers could both decide to create it
        synchronized(this) {
            requireParent(parent)
            val id = "d${next++}"
            nodes[id] = Node(id, name, dir, parent)
            log += "create ${if (dir) "dir" else "file"} $name"
            return id
        }
    }

    override fun createDir(parentDocId: String, name: String) = add(parentDocId, name, true)
    override fun createFile(parentDocId: String, mime: String, name: String): String {
        failCreateFile?.let { log += "refused $name"; throw it }
        return add(parentDocId, name, false)
    }

    @Synchronized override fun delete(docId: String) {
        if (nodes.remove(docId) == null) throw FileNotFoundException("no such document $docId")
        // a deleted folder takes everything inside with it
        var removed = true
        while (removed) removed = nodes.values.filter { it.parent != null && it.parent !in nodes }.map { nodes.remove(it.id) }.isNotEmpty()
        log += "delete $docId"
    }

    @Synchronized override fun openOutput(docId: String): OutputStream = (nodes[docId] ?: throw FileNotFoundException(docId)).data

    @Synchronized override fun openInput(docId: String): java.io.InputStream =
        java.io.ByteArrayInputStream((nodes[docId] ?: throw FileNotFoundException(docId)).data.toByteArray())

    @Synchronized override fun rename(docId: String, name: String): String {
        val n = nodes[docId] ?: throw FileNotFoundException(docId)
        nodes[docId] = Node(n.id, name, n.isDir, n.parent, n.data)
        log += "rename $name"
        return docId
    }

    @Synchronized fun idOf(name: String) = nodes.values.first { it.name == name }.id
}

class TreeWriterTest {
    private val flac = "audio/flac"

    @Test fun artistAndAlbumFoldersAreCreatedOnceAndReused() {
        val ops = FakeTreeOps()
        val w = TreeWriter(ops, "root")
        w.createFile(listOf("Artist", "Album"), flac, "01 - A.flac")
        w.createFile(listOf("Artist", "Album"), flac, "02 - B.flac")
        w.createFile(listOf("Artist", "Other"), flac, "01 - C.flac")
        assertEquals(1, ops.count("create dir Artist"))
        assertEquals(1, ops.count("create dir Album"))
        assertEquals(1, ops.count("create dir Other"))
        assertEquals(3, ops.count("create file"))
    }

    @Test fun anExistingFolderIsUsedNotDuplicated() {
        val ops = FakeTreeOps()
        val artist = ops.createDir("root", "Artist")
        ops.createDir(artist, "Album")
        ops.log.clear()
        val w = TreeWriter(ops, "root") // a fresh writer knows nothing: it must find them
        w.createFile(listOf("Artist", "Album"), flac, "01 - A.flac")
        assertEquals("no folder may be created when it already exists", 0, ops.count("create dir"))
    }

    @Test fun tenDownloadsOfOneAlbumStartingTogetherMakeOneArtistAndOneAlbumFolder() {
        val ops = FakeTreeOps().also { it.slowMs = 25 }
        val w = TreeWriter(ops, "root")
        val pool = Executors.newFixedThreadPool(10)
        val go = CountDownLatch(1)
        val ids = (1..10).map { n -> pool.submit<String> { go.await(); w.createFile(listOf("Artist", "Album"), flac, "%02d - Song.flac".format(n)) } }
        go.countDown()
        ids.forEach { it.get(20, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals("one Artist folder, not ten", 1, ops.count("create dir Artist"))
        assertEquals("one Album folder, not 'Album (1)', 'Album (2)'…", 1, ops.count("create dir Album"))
        assertEquals(10, ops.count("create file"))
    }

    @Test fun ifTheRememberedFolderWasDeletedItIsRecreatedOnce() {
        val ops = FakeTreeOps()
        val w = TreeWriter(ops, "root")
        w.createFile(listOf("Artist", "Album"), flac, "01 - A.flac")
        ops.delete(ops.idOf("Album")) // the user cleaned up while the app was open

        val id = w.createFile(listOf("Artist", "Album"), flac, "02 - B.flac")

        assertEquals("the Album folder was created again", 2, ops.count("create dir Album"))
        assertEquals("under the same artist", 1, ops.count("create dir Artist"))
        assertEquals("02 - B.flac", ops.nodes.getValue(id).name)
        assertEquals("Album", ops.nodes.getValue(ops.nodes.getValue(id).parent!!).name)
    }

    @Test fun aFolderThatKeepsRefusingFailsAfterExactlyOneRetry() {
        val ops = FakeTreeOps().also { it.failCreateFile = IOException("denied") }
        val w = TreeWriter(ops, "root")
        try {
            w.createFile(listOf("Artist", "Album"), flac, "01 - A.flac")
            fail("must not succeed")
        } catch (e: IOException) {
            assertEquals("denied", e.message)
        }
        assertEquals("one try plus one retry, never a loop", 2, ops.count("refused"))
    }

    @Test fun existsLooksWithoutEverCreatingAFolder() {
        val ops = FakeTreeOps()
        val w = TreeWriter(ops, "root")
        assertFalse(w.exists(listOf("Artist", "Album"), "01 - A.flac"))
        assertEquals("checking must not create folders", 0, ops.count("create"))

        w.createFile(listOf("Artist", "Album"), flac, "01 - A.flac")
        assertTrue(w.exists(listOf("Artist", "Album"), "01 - A.flac"))
        assertFalse("another name", w.exists(listOf("Artist", "Album"), "02 - B.flac"))
        assertFalse("another album", w.exists(listOf("Artist", "Elsewhere"), "01 - A.flac"))
        assertFalse("another artist", w.exists(listOf("Nobody", "Album"), "01 - A.flac"))
    }

    @Test fun aFolderWithTheSameNameIsNotAFile() {
        val ops = FakeTreeOps()
        val artist = ops.createDir("root", "Artist")
        val album = ops.createDir(artist, "Album")
        ops.createDir(album, "01 - A.flac")
        assertFalse(TreeWriter(ops, "root").exists(listOf("Artist", "Album"), "01 - A.flac"))
    }

    @Test fun probeCreatesAndRemovesATinyFileAndLeavesNothingBehind() {
        val ops = FakeTreeOps()
        val before = ops.nodes.size
        TreeWriter(ops, "root").probe()
        assertEquals(before, ops.nodes.size)
        assertEquals(1, ops.count("create file")); assertEquals(1, ops.count("delete"))
    }

    @Test fun probeFailsWhenTheFolderRefusesFiles() {
        val ops = FakeTreeOps().also { it.failCreateFile = IOException("read-only") }
        try { TreeWriter(ops, "root").probe(); fail("a read-only folder must be rejected") } catch (e: IOException) { assertEquals("read-only", e.message) }
    }

    @Test fun deleteNeverThrowsEvenForAFileThatIsAlreadyGone() {
        TreeWriter(FakeTreeOps(), "root").delete("nope")
    }

    @Test fun writingGoesToTheCreatedFile() {
        val ops = FakeTreeOps()
        val w = TreeWriter(ops, "root")
        val id = w.createFile(listOf("A", "B"), flac, "01 - X.flac")
        w.open(id).use { it.write(byteArrayOf(1, 2, 3)) }
        assertArrayEquals(byteArrayOf(1, 2, 3), ops.nodes.getValue(id).data.toByteArray())
    }
}

class FolderSafetyNetTest {
    private class FailingSink(private val failure: Throwable? = null) : OutputStream() {
        val bytes = ByteArrayOutputStream()
        override fun write(b: Int) { failure?.let { throw it }; bytes.write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) { failure?.let { throw it }; bytes.write(b, off, len) }
        override fun flush() { failure?.let { throw it } }
        override fun close() { failure?.let { throw it } }
    }

    @Test fun aFailingDestinationIsReportedAsAFolderProblemNotANetworkOne() {
        for (op in listOf<(OutputStream) -> Unit>({ it.write(1) }, { it.write(ByteArray(4), 0, 4) }, { it.flush() }, { it.close() })) {
            try {
                op(GuardedOutputStream(FailingSink(IOException("No space left on device"))))
                fail("must throw")
            } catch (e: FolderWriteException) {
                assertTrue(e.message!!.contains("No space left"))
            }
        }
    }

    @Test fun aGoodDestinationPassesBytesThroughUntouched() {
        val sink = FailingSink()
        GuardedOutputStream(sink).use { it.write(byteArrayOf(9, 8, 7)); it.write(6) }
        assertArrayEquals(byteArrayOf(9, 8, 7, 6), sink.bytes.toByteArray())
    }

    @Test fun anAlreadyWrappedFailureIsNotWrappedTwice() {
        val original = FolderWriteException(IOException("x"))
        try { GuardedOutputStream(FailingSink(original)).write(1); fail() } catch (e: FolderWriteException) { assertSame(original, e) }
    }

    @Test fun aProgrammingErrorIsNotDisguisedAsAFolderProblem() {
        try { GuardedOutputStream(FailingSink(IllegalStateException("bug"))).write(1); fail() } catch (e: IllegalStateException) { assertEquals("bug", e.message) }
    }

    @Test fun theChosenFolderIsUsedWhenItWorks() {
        var fallbackRan = false
        val where = writeWithFallback(tree = { "folder" }, fallback = { fallbackRan = true; "music" }, onTreeFailure = { fail("no failure expected") })
        assertEquals("folder", where); assertFalse(fallbackRan)
    }

    @Test fun noChosenFolderMeansTheDefaultOne() {
        assertEquals("music", writeWithFallback<String>(tree = null, fallback = { "music" }, onTreeFailure = { fail("not a failure") }))
    }

    @Test fun aFolderFailureFallsBackToTheDefaultAndIsReported() {
        val reported = ArrayList<Throwable>()
        val boom = FolderWriteException(IOException("card removed"))
        val where = writeWithFallback(tree = { throw boom }, fallback = { "music" }, onTreeFailure = { reported += it })
        assertEquals("music", where)
        assertEquals(listOf<Throwable>(boom), reported)
    }

    @Test fun aNetworkErrorIsNotTheFoldersFaultAndIsNotSwallowed() {
        var fallbackRan = false
        try {
            writeWithFallback(tree = { throw IOException("Connection reset") }, fallback = { fallbackRan = true; "music" }, onTreeFailure = { fail("not the folder") })
            fail("the network error must reach the queue's retry logic")
        } catch (e: IOException) {
            assertEquals("Connection reset", e.message)
        }
        assertFalse("the default folder must not mask a network problem", fallbackRan)
    }

    @Test fun theWarningShowsOnceStaysDismissedAndComesBackAfterARecovery() {
        val s = FolderStatus()
        assertFalse(s.visible)
        s.report(false); assertTrue(s.visible)
        s.report(false); assertTrue("still one warning", s.visible)
        s.dismiss(); assertFalse(s.visible)
        s.report(false); assertFalse("every failing track must not bring it back", s.visible)
        s.report(true); assertFalse(s.problem)
        s.report(false); assertTrue("a new problem after a recovery shows again", s.visible)
    }
}

class FileNamesAndPathsTest {
    private fun track(number: Int, albumNumber: Int = 0, title: String = "Song", version: String? = null) =
        Track(1L, title, version, number, 1, "A", 200, true, "16/44", false, albumTrackNumber = albumNumber)

    @Test fun theNameUsesTheAlbumTrackNumberNotThePlaylistPosition() {
        assertEquals("03 - Song.flac", trackFileName(track(number = 41, albumNumber = 3), "flac"))
        assertEquals("the same track gets the same name wherever it was downloaded from",
            trackFileName(track(number = 3, albumNumber = 3), "flac"), trackFileName(track(number = 41, albumNumber = 3), "flac"))
    }

    @Test fun withoutAnAlbumNumberItFallsBackToTheNumberItHas() {
        assertEquals("07 - Song.mp3", trackFileName(track(number = 7), "mp3"))
    }

    @Test fun numbersArePaddedToTwoDigitsAndGrowBeyond() {
        assertEquals("01 - Song.flac", trackFileName(track(1, 1), "flac"))
        assertEquals("100 - Song.flac", trackFileName(track(1, 100), "flac"))
    }

    @Test fun theVersionIsPartOfTheNameAndUnsafeCharactersAreReplaced() {
        assertEquals("02 - AC_DC_ What_ (Live).flac", trackFileName(track(2, 2, title = "AC/DC: What?", version = "Live"), "flac"))
    }

    @Test fun digitsAreAlwaysAsciiWhateverThePhoneLanguage() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale("ar", "EG")) // formats digits as ٠١٢٣ with the default locale
            assertEquals("05 - Song.flac", trackFileName(track(5, 5), "flac"))
        } finally { Locale.setDefault(saved) }
    }

    @Test fun nameCleaningMatchesWhatFatAndSdCardsAccept() {
        assertEquals("AC_DC", cleanName("AC/DC"))
        assertEquals("What_", cleanName("What?"))
        assertEquals("A B", cleanName("  A    B  "))
        assertEquals("Album", cleanName("Album..."))
        assertEquals("_", cleanName(""))
        assertEquals("_", cleanName("..."))
        assertNotEquals("a:b", cleanName("a:b"))
    }

    @Test fun documentIdsAreReadAsVolumeAndPath() {
        val phone = TreePaths.locate("primary:Music/Qobuz")
        assertTrue(phone.isPrimary); assertEquals("primary", phone.volume); assertEquals("Music/Qobuz", phone.path)
        val sd = TreePaths.locate("1A2B-3C4D:Música/Mi carpeta/")
        assertFalse(sd.isPrimary); assertEquals("1A2B-3C4D", sd.volume); assertEquals("Música/Mi carpeta", sd.path)
        assertEquals("", TreePaths.locate("primary:").path)
        assertEquals("", TreePaths.locate("1A2B-3C4D").path)
    }

    @Test fun theScannerPathIsBuiltForThePhoneAndForACard() {
        assertEquals("/storage/emulated/0/Music/Qobuz/A/B/01 - X.flac", TreePaths.fsPath("primary:Music/Qobuz/A/B/01 - X.flac"))
        assertEquals("/storage/1A2B-3C4D/Music/X.flac", TreePaths.fsPath("1A2B-3C4D:Music/X.flac"))
        assertEquals("/storage/emulated/0", TreePaths.fsPath("primary:"))
        assertEquals("/storage/emulated/10/Music", TreePaths.fsPath("primary:Music", primaryRoot = "/storage/emulated/10/"))
    }
}

/** What replacing a lower-quality file needs from the folder tree. */
class TreeUpgradeSupportTest {
    private fun tree(): Pair<FakeTreeOps, TreeWriter> = FakeTreeOps().let { it to TreeWriter(it, "root") }

    @Test fun findReturnsTheFileAndNothingElseAndCreatesNothing() {
        val (ops, w) = tree()
        val id = w.createFile(listOf("Artist", "Album"), "audio/flac", "01 - A.flac")
        ops.log.clear()
        assertEquals(id, w.find(listOf("Artist", "Album"), "01 - A.flac"))
        assertNull(w.find(listOf("Artist", "Album"), "01 - A.mp3"))
        assertNull(w.find(listOf("Artist", "Nope"), "01 - A.flac"))
        assertNull("a folder named like the file is not the file", w.find(listOf("Artist"), "Album"))
        assertEquals(0, ops.count("create"))
    }

    @Test fun readHeadReturnsTheFirstBytesOrNullWhenUnreadable() {
        val (ops, w) = tree()
        val id = w.createFile(listOf("A", "B"), "audio/flac", "x.flac")
        ops.openOutput(id).write(ByteArray(100) { it.toByte() })
        val head = w.readHead(id, 42)!!
        assertEquals(42, head.size); assertEquals(41, head[41].toInt())
        assertEquals(100, w.readHead(id, 500)!!.size)
        assertNull(w.readHead("no-such-doc", 42))
    }

    @Test fun renameKeepsTheContentAndFailureIsReportedNotThrown() {
        val (ops, w) = tree()
        val id = w.createFile(listOf("A", "B"), "audio/flac", "01 - A (1).flac")
        ops.openOutput(id).write(byteArrayOf(1, 2, 3))
        val renamed = w.rename(id, "01 - A.flac")!!
        assertEquals(renamed, w.find(listOf("A", "B"), "01 - A.flac"))
        assertNull(w.find(listOf("A", "B"), "01 - A (1).flac"))
        assertEquals(3, w.readHead(renamed, 10)!!.size)
        assertNull(w.rename("no-such-doc", "x.flac"))
    }

    @Test fun deleteSaysWhetherItWorked() {
        val (_, w) = tree()
        val id = w.createFile(listOf("A", "B"), "audio/flac", "x.flac")
        assertTrue(w.delete(id))
        assertFalse("already gone", w.delete(id))
        assertNull(w.find(listOf("A", "B"), "x.flac"))
    }
}
