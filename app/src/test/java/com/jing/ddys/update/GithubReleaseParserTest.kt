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
    fun findsFirstPublishedReleaseWithApkAsset() {
        val result = GithubReleaseParser.parseFirstWithApk(
            """
            [
              {
                "tag_name": "v1.3.2",
                "name": "v1.3.2",
                "body": "",
                "html_url": "https://github.com/icechriszen/ddys/releases/tag/v1.3.2",
                "assets": [
                  {
                    "name": "ddys-v1.3.2.apk.sha256",
                    "browser_download_url": "https://example.com/ddys-v1.3.2.apk.sha256",
                    "size": 82
                  }
                ]
              },
              {
                "tag_name": "v1.3.1",
                "name": "v1.3.1",
                "body": "",
                "html_url": "https://github.com/icechriszen/ddys/releases/tag/v1.3.1",
                "assets": [
                  {
                    "name": "ddys-v1.3.1.apk",
                    "browser_download_url": "https://example.com/ddys-v1.3.1.apk",
                    "size": 123456,
                    "digest": "sha256:abcdef"
                  }
                ]
              }
            ]
            """.trimIndent()
        )

        assertEquals("v1.3.1", result?.tagName)
        assertEquals("ddys-v1.3.1.apk", result?.apkAsset?.name)
    }

    @Test
    fun ignoresDraftAndPrereleaseEntriesWhenFindingApkAsset() {
        val result = GithubReleaseParser.parseFirstWithApk(
            """
            [
              {
                "tag_name": "v1.4.0-beta",
                "name": "v1.4.0-beta",
                "prerelease": true,
                "assets": [
                  {
                    "name": "ddys-v1.4.0-beta.apk",
                    "browser_download_url": "https://example.com/ddys-v1.4.0-beta.apk",
                    "size": 123456
                  }
                ]
              },
              {
                "tag_name": "v1.3.1",
                "name": "v1.3.1",
                "assets": [
                  {
                    "name": "ddys-v1.3.1.apk",
                    "browser_download_url": "https://example.com/ddys-v1.3.1.apk",
                    "size": 123456
                  }
                ]
              }
            ]
            """.trimIndent()
        )

        assertEquals("v1.3.1", result?.tagName)
    }

    @Test
    fun mapsHttpStatusToNoReleaseOrFailure() {
        assertTrue(UpdateRepository.parseResponse(404, "") is UpdateFetchResult.NoRelease)
        assertTrue(UpdateRepository.parseResponse(403, "") is UpdateFetchResult.Failure)
    }

    @Test
    fun mapsStaticManifestToFoundRelease() {
        val result = UpdateRepository.parseResponse(
            200,
            """
            {
              "tag_name": "v1.3.3",
              "name": "v1.3.3",
              "body": "",
              "html_url": "https://github.com/icechriszen/ddys/releases/tag/v1.3.3",
              "assets": [
                {
                  "name": "ddys-v1.3.3.apk",
                  "browser_download_url": "https://github.com/icechriszen/ddys/releases/download/v1.3.3/ddys-v1.3.3.apk",
                  "size": 123456,
                  "digest": "sha256:abcdef"
                }
              ]
            }
            """.trimIndent()
        )

        assertTrue(result is UpdateFetchResult.Found)
        val release = (result as UpdateFetchResult.Found).release
        assertEquals("v1.3.3", release.tagName)
        assertEquals("abcdef", release.apkAsset.sha256)
    }

    @Test
    fun ignoresMalformedAssetsWithoutThrowing() {
        val result = UpdateRepository.parseResponse(
            200,
            """
            {
              "tag_name": "v1.3.4",
              "name": "v1.3.4",
              "assets": {}
            }
            """.trimIndent()
        )

        assertTrue(result is UpdateFetchResult.NoRelease)
    }
}
