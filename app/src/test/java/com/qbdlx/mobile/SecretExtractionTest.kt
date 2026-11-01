package com.qbdlx.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Tests the pure App Secret reconstruction from the web player bundle. The second test runs only
 * when a real bundle path is supplied via -Dqbz.bundle=..., and no real secret is ever committed.
 */
class SecretExtractionTest {

    /** Builds a minimal bundle whose Berlin seed decodes to [secret] by the documented method:
     *  secret = base64decode( (seed + info + extras) without the last 44 chars ). */
    private fun fixture(secret: String, appId: String): String {
        val b = java.util.Base64.getEncoder().encodeToString(secret.toByteArray())
        val concat = b + "A".repeat(44)                       // 44 trailing chars are dropped
        val seed = concat.substring(0, b.length)              // == b
        val rest = concat.substring(b.length)                 // 44 'A'
        val info = rest.substring(0, 22)
        val extras = rest.substring(22)
        return """
            var x;x.initialSeed("$seed",window.utimezone.berlin);
            window.__tz={name:"Europe/Berlin",info:"$info",extras:"$extras"};
            var cfg={production:{api:{appId:"$appId",appSecret:"deadbeefdeadbeefdeadbeefdeadbeef"}}};
        """.trimIndent()
    }

    @Test
    fun reconstructsSecretAndAppIdFromSyntheticBundle() {
        val secret = "a".repeat(32) // valid 32-hex ("a" is a hex digit)
        val bundle = fixture(secret, "123456789")

        assertEquals("123456789", QobuzApi.extractAppId(bundle))

        val candidates = QobuzApi.extractSecretCandidates(bundle)
        val berlin = candidates.firstOrNull { it.timezone == "berlin" }
        assertNotNull("expected a berlin candidate", berlin)
        assertEquals(secret, berlin!!.secret)
        // The decoy inline appSecret must never be returned as a candidate.
        assertTrue(candidates.none { it.secret == "deadbeefdeadbeefdeadbeefdeadbeef" })
    }

    @Test
    fun matchesTheLiveBundleFingerprintsWhenProvided() {
        val path = System.getProperty("qbz.bundle")
        assumeTrue("set -Dqbz.bundle=<bundle.js> to run this test", path != null && File(path).exists())

        val bundle = File(path!!).readText()
        val byTz = QobuzApi.extractSecretCandidates(bundle).associate { it.timezone to QobuzApi.fingerprintOf(it.secret) }
        // Fingerprints are short SHA-256 prefixes, not secrets; computed from the live bundle used in analysis.
        assertEquals("50325705", byTz["berlin"])
        assertEquals("feeb1056", byTz["london"])
        assertEquals("dbc67824", byTz["abidjan"])
        // The first candidate must be Berlin (desktop parity).
        assertEquals("berlin", QobuzApi.extractSecretCandidates(bundle).first().timezone)
    }
}
