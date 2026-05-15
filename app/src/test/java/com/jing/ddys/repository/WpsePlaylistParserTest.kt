package com.jing.ddys.repository

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class WpsePlaylistParserTest {

    @Test
    fun attachesSeasonNameToEveryEpisode() {
        val document = Jsoup.parse(
            """
            <script class="wpse-playlist-data" type="application/json">
            {
              "seasons": [
                {
                  "title": "1",
                  "tracks": [
                    { "src": "/videos/s1e1.m3u8", "episode": 1 }
                  ]
                },
                {
                  "title": "2",
                  "tracks": [
                    { "src": "/videos/s2e1.m3u8", "episode": 1, "title": "第一集" }
                  ]
                }
              ]
            }
            </script>
            """.trimIndent()
        )

        val episodes = WpsePlaylistParser.parse(document, "/drama/")

        assertEquals("1", episodes[0].seasonName)
        assertEquals("第1集", episodes[0].name)
        assertEquals("第1季 第1集", episodes[0].displayName)
        assertEquals("2", episodes[1].seasonName)
        assertEquals("第一集", episodes[1].name)
        assertEquals("第2季 第一集", episodes[1].displayName)
    }
}
