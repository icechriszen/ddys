package com.jing.ddys.repository

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyPlaylistParserTest {

    @Test
    fun parsesPlaylistScriptWithoutReflectiveMapInstantiation() {
        val document = Jsoup.parse(
            """
            <script class="wp-playlist-script" type="application/json">
            {
              "tracks": [
                {
                  "caption": "第一集",
                  "src0": "/video/low.m3u8",
                  "src1": "/video/high.m3u8",
                  "subsrc": "/subtitle.ass"
                }
              ]
            }
            </script>
            """.trimIndent()
        )

        val episodes = LegacyPlaylistParser.parse(document, "/movie/", "1")

        assertEquals(1, episodes.size)
        assertEquals("第一集", episodes.single().name)
        assertEquals("/video/low.m3u8", episodes.single().src0)
        assertEquals("/video/high.m3u8", episodes.single().src1)
        assertEquals("1", episodes.single().seasonName)
    }
}
