package com.qbdlx.mobile

import org.junit.Assert.*
import org.junit.Test

class AiMixTest {
    private val valid = (1..50).toSet()

    @Test fun dropsUnknownAndRepeatedNumbers() {
        val m = AiMix.validate("""{"mixes":[{"name":"Calma","ids":[1,1,2,3,4,5,99,6]}]}""", valid)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), m.single().ns)
    }
    @Test fun dropsShortMixesAndDuplicateNames() {
        val m = AiMix.validate("""{"mixes":[{"name":"A","ids":[1,2]},{"name":"B","ids":[1,2,3,4,5]},{"name":"b","ids":[6,7,8,9,10]}]}""", valid)
        assertEquals(listOf("B"), m.map { it.name })
    }
    @Test fun capsSizeAndCount() {
        val ids = (1..50).joinToString(",")
        val j = (1..8).joinToString(",", "{\"mixes\":[", "]}") { """{"name":"M$it","ids":[$ids]}""" }
        val m = AiMix.validate(j, valid)
        assertEquals(AiMix.MAX_MIXES, m.size); assertEquals(AiMix.MAX_MIX, m[0].ns.size)
    }
    @Test fun badJsonAndFenceAndInvisible() {
        assertTrue(AiMix.validate("no es json", valid).isEmpty())
        val m = AiMix.validate("```json\n{\"mixes\":[{\"name\":\"Ca​lma\",\"ids\":[1,2,3,4,5]}]}\n```", valid)
        assertEquals("Calma", m.single().name)
    }
    @Test fun localMixesNeverEmptyWithEnoughSongs() {
        val songs = (1..12).map { MixSong(it, "t$it", if (it <= 6) "A" else "B", "x") }
        val m = AiMix.localMixes(songs, seed = 1)
        assertTrue(m.isNotEmpty()); assertTrue(m.any { it.name == "Sorpresa" })
    }

    @Test fun ideasAreCleanedDedupedAndCapped() {
        val json = """{"ideas":["Para correr","  para correr ","\"Noche tranquila.\"","","ab","a\u200bB c","Uno","Dos","Tres"]}"""
        val r = AiMix.validateIdeas(json)
        assertEquals(listOf("Para correr", "Noche tranquila", "aB c", "Uno", "Dos"), r)
        assertTrue(AiMix.validateIdeas("not json").isEmpty())
    }

    @Test fun localIdeasUseTopArtists() {
        val songs = (1..6).map { MixSong(it, "t$it", "Queen", "a") } + (7..9).map { MixSong(it, "t$it", "Solo", "a") }
        assertEquals(listOf("Lo mejor de Queen", "Sorpresa con todo mi catálogo"), AiMix.localIdeas(songs))
    }
}
