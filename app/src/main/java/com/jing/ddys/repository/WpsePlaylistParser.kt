package com.jing.ddys.repository

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.jsoup.nodes.Document

object WpsePlaylistParser {
    fun parse(document: Document, videoId: String): List<VideoEpisode> {
        val seasons = document.selectFirst("script.wpse-playlist-data")?.html()
            ?.takeIf { it.isNotBlank() }
            ?.let { JsonParser.parseString(it).asJsonObject.getAsJsonArray("seasons") }
            ?: return emptyList()
        val showSeasonName = seasons.size() > 1
        return seasons.flatMap { season ->
            val seasonInfo = season.asJsonObject
            val seasonTitle = seasonInfo.get("title")?.asString?.trim().orEmpty()
            val tracks = seasonInfo.getAsJsonArray("tracks") ?: return@flatMap emptyList()
            tracks.mapNotNull { track ->
                val trackInfo = track.asJsonObject
                val src = trackInfo.get("src")?.asString?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val episode = trackInfo.get("episode")?.let(::formatPlaylistNumber).orEmpty()
                val title = trackInfo.get("title")?.asString?.takeIf { it.isNotBlank() }
                val name = title ?: episode.takeIf { it.isNotEmpty() }?.let { "第${it}集" }
                    ?: src.substringAfterLast('/').substringBeforeLast('.')
                VideoEpisode(
                    id = src.ifEmpty { "$videoId|$seasonTitle|$name" },
                    name = name,
                    subTitleUrl = "",
                    seasonName = seasonTitle.takeIf { showSeasonName }.orEmpty(),
                    src0 = src
                )
            }
        }
    }

    private fun formatPlaylistNumber(value: JsonElement): String {
        val raw = value.asString
        return raw.removeSuffix(".0")
    }
}
