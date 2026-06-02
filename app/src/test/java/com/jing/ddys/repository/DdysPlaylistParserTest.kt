package com.jing.ddys.repository

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class DdysPlaylistParserTest {

    @Test
    fun parsesDdysPlaylistDataWithServerHost() {
        val document = Jsoup.parse(
            """
            <script class="ddys-playlist-data" type="application/json">
            {
              "playlistType": "drama",
              "seasons": [
                {
                  "title": "第1季",
                  "season": 1,
                  "tracks": [
                    {
                      "src": "/v2/west_drama/Spider_Noir/Spider_Noir_S01E01.mp4",
                      "server": "v3",
                      "episode": 1,
                      "cut": "0",
                      "title": "1"
                    }
                  ]
                }
              ]
            }
            </script>
            """.trimIndent()
        )

        val episode = DdysPlaylistParser.parse(document, "/spider-noir/").single()

        assertEquals("/spider-noir/|第1季|/v2/west_drama/Spider_Noir/Spider_Noir_S01E01.mp4", episode.id)
        assertEquals("第1集", episode.name)
        assertEquals("第1集", episode.displayName)
        assertEquals("", episode.seasonName)
        assertEquals(
            "https://v3.ddys.app/v2/west_drama/Spider_Noir/Spider_Noir_S01E01.mp4",
            episode.src0
        )
    }

    @Test
    fun attachesSeasonNameWhenMultipleSeasons() {
        val document = Jsoup.parse(
            """
            <script class="ddys-playlist-data" type="application/json">
            {
              "seasons": [
                {
                  "title": "1",
                  "tracks": [
                    { "src": "/v2/show/s1e1.mp4", "server": "v3", "episode": 1 }
                  ]
                },
                {
                  "title": "2",
                  "tracks": [
                    { "src": "/v2/show/s2e1.mp4", "server": "v3.ddys.app", "title": "开场" }
                  ]
                }
              ]
            }
            </script>
            """.trimIndent()
        )

        val episodes = DdysPlaylistParser.parse(document, "/show/")

        assertEquals("1", episodes[0].seasonName)
        assertEquals("第1季 第1集", episodes[0].displayName)
        assertEquals("2", episodes[1].seasonName)
        assertEquals("第2季 开场", episodes[1].displayName)
        assertEquals("https://v3.ddys.app/v2/show/s2e1.mp4", episodes[1].src0)
    }
}
