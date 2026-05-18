package com.jing.ddys.update

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object GithubReleaseParser {

    fun parse(json: String): UpdateRelease? {
        val element = runCatching { JsonParser.parseString(json) }.getOrNull()
            ?: return null
        return element.asObjectOrNull()?.toUpdateReleaseOrNull()
    }

    fun parseFirstWithApk(json: String): UpdateRelease? {
        val element = runCatching { JsonParser.parseString(json) }.getOrNull()
            ?: return null
        val releases = element.asArrayOrNull()
            ?: return element.asObjectOrNull()?.toUpdateReleaseOrNull()

        return releases.asSequence()
            .mapNotNull { it.asObjectOrNull() }
            .filter { !it.boolean("draft") && !it.boolean("prerelease") }
            .mapNotNull { it.toUpdateReleaseOrNull() }
            .firstOrNull()
    }

    private fun JsonObject.toUpdateReleaseOrNull(): UpdateRelease? {
        val asset = array("assets")
            ?.asSequence()
            ?.mapNotNull { it.asObjectOrNull() }
            ?.firstOrNull {
                it.string("name").endsWith(".apk", ignoreCase = true) &&
                    it.string("browser_download_url").isNotBlank()
            }
            ?: return null
        val tagName = string("tag_name").takeIf { it.isNotBlank() } ?: return null
        return UpdateRelease(
            tagName = tagName,
            releaseName = string("name"),
            body = string("body"),
            htmlUrl = string("html_url"),
            apkAsset = UpdateAsset(
                name = asset.string("name"),
                downloadUrl = asset.string("browser_download_url"),
                size = asset.long("size"),
                sha256 = asset.stringOrNull("digest")
                    ?.removePrefix("sha256:")
                    ?.takeIf { it.isNotBlank() }
            )
        )
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? =
        takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonElement.asArrayOrNull(): JsonArray? =
        takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.array(name: String): JsonArray? =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.string(name: String): String =
        stringOrNull(name).orEmpty()

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.boolean(name: String): Boolean =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: false

    private fun JsonObject.long(name: String): Long =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: 0L
}
