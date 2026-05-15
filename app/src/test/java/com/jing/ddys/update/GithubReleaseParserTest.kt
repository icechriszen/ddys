package com.jing.ddys.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubReleaseParserTest {

    @Test
    fun parsesLatestReleaseAndFindsApkAsset() {
        val result = GithubReleaseParser.parse(
            """
            {
              "tag_name": "v1.2.9",
              "name": "v1.2.9",
              "body": "修复播放问题",
              "html_url": "https://github.com/icechriszen/ddys/releases/tag/v1.2.9",
              "assets": [
                {
                  "name": "source.zip",
                  "browser_download_url": "https://example.com/source.zip",
                  "size": 1024
                },
                {
                  "name": "ddys-v1.2.9.apk",
                  "browser_download_url": "https://example.com/ddys-v1.2.9.apk",
                  "size": 123456,
                  "digest": "sha256:abcdef"
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("v1.2.9", result?.tagName)
        assertEquals("修复播放问题", result?.body)
        assertEquals("ddys-v1.2.9.apk", result?.apkAsset?.name)
        assertEquals("https://example.com/ddys-v1.2.9.apk", result?.apkAsset?.downloadUrl)
        assertEquals(123456L, result?.apkAsset?.size)
        assertEquals("abcdef", result?.apkAsset?.sha256)
    }

    @Test
    fun ignoresReleaseWithoutApkAsset() {
        val result = GithubReleaseParser.parse(
            """
            {
              "tag_name": "v1.2.9",
              "name": "v1.2.9",
              "body": "",
              "html_url": "https://github.com/icechriszen/ddys/releases/tag/v1.2.9",
              "assets": [
                {
                  "name": "source.zip",
                  "browser_download_url": "https://example.com/source.zip",
                  "size": 1024
                }
              ]
            }
            """.trimIndent()
        )

        assertNull(result)
    }

    @Test
    fun mapsHttpStatusToNoReleaseOrFailure() {
        assertTrue(UpdateRepository.parseResponse(404, "") is UpdateFetchResult.NoRelease)
        assertTrue(UpdateRepository.parseResponse(403, "") is UpdateFetchResult.Failure)
    }
}
