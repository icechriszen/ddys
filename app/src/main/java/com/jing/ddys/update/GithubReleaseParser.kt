package com.jing.ddys.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

object GithubReleaseParser {

    private val gson = Gson()

    fun parse(json: String): UpdateRelease? {
        val dto = runCatching { gson.fromJson(json, GithubReleaseDto::class.java) }.getOrNull()
            ?: return null
        return dto.toUpdateReleaseOrNull()
    }

    fun parseFirstWithApk(json: String): UpdateRelease? {
        val releases = runCatching {
            gson.fromJson(json, Array<GithubReleaseDto>::class.java).toList()
        }.getOrNull() ?: return parse(json)

        return releases.asSequence()
            .filter { !it.draft && !it.prerelease }
            .mapNotNull { it.toUpdateReleaseOrNull() }
            .firstOrNull()
    }

    private fun GithubReleaseDto.toUpdateReleaseOrNull(): UpdateRelease? {
        val asset = assets.firstOrNull {
            it.name.endsWith(".apk", ignoreCase = true) && it.browserDownloadUrl.isNotBlank()
        } ?: return null
        return UpdateRelease(
            tagName = tagName.takeIf { it.isNotBlank() } ?: return null,
            releaseName = name,
            body = body,
            htmlUrl = htmlUrl,
            apkAsset = UpdateAsset(
                name = asset.name,
                downloadUrl = asset.browserDownloadUrl,
                size = asset.size,
                sha256 = asset.digest
                    ?.removePrefix("sha256:")
                    ?.takeIf { it.isNotBlank() }
            )
        )
    }

    private data class GithubReleaseDto(
        @SerializedName("tag_name")
        val tagName: String = "",
        val name: String = "",
        val body: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @SerializedName("html_url")
        val htmlUrl: String = "",
        val assets: List<GithubAssetDto> = emptyList()
    )

    private data class GithubAssetDto(
        val name: String = "",
        @SerializedName("browser_download_url")
        val browserDownloadUrl: String = "",
        val size: Long = 0,
        val digest: String? = null
    )
}
