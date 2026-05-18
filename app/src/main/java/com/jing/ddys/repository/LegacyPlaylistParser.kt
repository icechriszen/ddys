package com.jing.ddys.repository

import com.google.gson.JsonParser
import org.jsoup.nodes.Document

object LegacyPlaylistParser {
    fun parse(document: Document, videoId: String, seasonName: String): List<VideoEpisode> {
        val root = document.selectFirst(".wp-playlist-script")?.html()
            ?.takeIf { it.isNotBlank() }
            ?.let { JsonParser.parseString(it).asJsonObject }
            ?: return emptyList()
        val tracks = root.getAsJsonArray("tracks") ?: return emptyList()
        return tracks.mapNotNull { track ->
            val trackInfo = track.asJsonObject
            val src0 = trackInfo.get("src0")?.asString.orEmpty()
            val src1 = trackInfo.get("src1")?.asString.orEmpty()
            val name = trackInfo.get("caption")?.asString?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            VideoEpisode(
                id = src1.ifEmpty { "$videoId|$name" },
                name = name,
                subTitleUrl = trackInfo.get("subsrc")?.asString?.let {
                    VideoSourceAuth.resolveSiteUrl("/subddr${it}")
                } ?: "",
                seasonName = seasonName,
                src0 = src0,
                src1 = src1
            )
        }
    }
}
