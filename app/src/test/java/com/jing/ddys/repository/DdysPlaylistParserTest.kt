package com.jing.ddys.repository

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class DdysPlaylistParserTest {

    @Test
    fun preservesAbsoluteSourceInNodePlaylist() {
        val document = Jsoup.parse(
            """
            <script class="ddys-playlist-data" type="application/json">
            {
              "playlistType": "movie",
              "nodes": [
                { "id": "v1", "access": "vip" },
                { "id": "v3", "access": "public" },
                { "id": "v2", "access": "public" }
              ],
              "seasons": [{
                "title": "第1季",
                "tracks": [{
                  "src": "https://v3.ddys.app/v2/movie/example.mp4",
                  "server": "v3",
                  "title": "正片",
                  "episode": 1,
                  "nodeSources": {
                    "v3": "https://v3.ddys.app/v2/movie/example.mp4",
                    "v2": "https://v2.ddys.app/v2/movie/example.mp4"
                  }
                }]
              }]
            }
            </script>
            """.trimIndent()
        )

        val episode = DdysPlaylistParser.parse(document, "/movie/").single()

        assertEquals("https://v3.ddys.app/v2/movie/example.mp4", episode.src0)
        assertEquals("/movie/|第1季|https://v3.ddys.app/v2/movie/example.mp4", episode.id)
        assertEquals("正片", episode.displayName)
    }

    @Test
    fun absoluteSourceTakesPrecedenceOverServerAndKeepsQuery() {
        assertEquals(
            "https://cdn.example.com/movie.mp4?token=example&expires=123#t=10",
            parseSource(" https://cdn.example.com/movie.mp4?token=example&expires=123#t=10 ", "v3")
        )
        assertEquals(
            "http://cdn.example.com/movie.mp4",
            parseSource("http://cdn.example.com/movie.mp4", "https://v3.ddys.app/")
        )
    }

    @Test
    fun resolvesProtocolRelativeSourceWithoutPrependingServer() {
        assertEquals(
            "https://cdn.example.com/movie.mp4",
            parseSource("//cdn.example.com/movie.mp4", "v3")
        )
    }

    @Test
    fun keepsLegacyRelativeSourcesWithAndWithoutServer() {
        assertEquals("/v2/movie/example.mp4", parseSource("/v2/movie/example.mp4", ""))
        assertEquals(
            "https://v3.ddys.app/v2/movie/example.mp4",
            parseSource("v2/movie/example.mp4", "https://v3.ddys.app/")
        )
    }

    private fun parseSource(src: String, server: String): String {
        val document = Jsoup.parse(
            """
            <script class="ddys-playlist-data" type="application/json">
            { "seasons": [{ "tracks": [{ "src": "$src", "server": "$server" }] }] }
            </script>
            """.trimIndent()
        )
        return DdysPlaylistParser.parse(document, "/movie/").single().src0
    }

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
