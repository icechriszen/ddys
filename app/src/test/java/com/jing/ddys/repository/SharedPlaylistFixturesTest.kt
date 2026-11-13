package com.jing.ddys.repository

import com.google.gson.JsonParser
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

/** The desktop parser consumes the same fixtures to keep source migrations compatible. */
class SharedPlaylistFixturesTest {
    @Test
    fun matchesSharedDesktopFixtures() {
        val input = checkNotNull(javaClass.classLoader!!.getResourceAsStream("playlists.json"))
        val fixtures = input.bufferedReader().use { JsonParser.parseReader(it).asJsonArray }
        fixtures.forEach { value ->
            val fixture = value.asJsonObject
            val name = fixture["name"].asString
            val doc = Jsoup.parse(fixture["html"].asString)
            val id = fixture["videoId"].asString
            val episodes = LegacyPlaylistParser.parse(doc, id, fixture["season"].asString)
                .ifEmpty { WpsePlaylistParser.parse(doc, id) }
                .ifEmpty { DdysPlaylistParser.parse(doc, id) }
            val expected = fixture["episodes"].asJsonArray
            assertEquals(name, expected.size(), episodes.size)
            episodes.forEachIndexed { index, episode ->
                val fields = expected[index].asJsonObject
                assertEquals(name, fields["id"].asString, episode.id)
                assertEquals(name, fields["name"].asString, episode.name)
                assertEquals(name, fields["seasonName"].asString, episode.seasonName)
                assertEquals(name, fields["src0"].asString, episode.src0)
                assertEquals(name, fields["src1"].asString, episode.src1)
                assertEquals(name, fields["subTitleUrl"].asString, episode.subTitleUrl)
            }
        }
    }
}
