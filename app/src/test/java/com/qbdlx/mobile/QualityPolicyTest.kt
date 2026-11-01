package com.qbdlx.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityPolicyTest {
    @Test fun aSavedChoiceAlwaysWins() {
        assertTrue("Preguntar siempre stays on", initialAskQuality(savedAsk = true, hasSavedQuality = true))
        assertFalse("a fixed quality stays fixed", initialAskQuality(savedAsk = false, hasSavedQuality = false))
        assertTrue(initialAskQuality(savedAsk = true, hasSavedQuality = false))
        assertFalse(initialAskQuality(savedAsk = false, hasSavedQuality = true))
    }

    @Test fun someoneWhoAlreadyPickedAQualityIsNotAskedAfterUpgrading() {
        // Their app never wrote "ask_quality" (it did not exist), but it did save the quality they chose.
        assertFalse(initialAskQuality(savedAsk = null, hasSavedQuality = true))
    }

    @Test fun someoneWhoNeverChoseAnythingIsStillAsked() {
        assertTrue(initialAskQuality(savedAsk = null, hasSavedQuality = false))
    }
}
