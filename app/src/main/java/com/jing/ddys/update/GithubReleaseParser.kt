package com.jing.ddys.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

object GithubReleaseParser {

    private val gson = Gson()

    fun parse(json: String): UpdateRelease? {
        val dto = runCatching { gson.fromJson(json, GithubReleaseDto::class.java) }.getOrNull()
            ?: return null
        val asset = dto.assets.firstOrNull {
            it.name.endsWith(".apk", ignoreCase = true) && it.browserDownloadUrl.isNotBlank()
        } ?: return null
        return UpdateRelease(
            tagName = dto.tagName.takeIf { it.isNotBlank() } ?: return null,
            releaseName = dto.name,
            body = dto.body,
            htmlUrl = dto.htmlUrl,
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
