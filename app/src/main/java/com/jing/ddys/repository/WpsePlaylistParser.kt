package com.jing.ddys.repository

import com.google.gson.Gson
import org.jsoup.nodes.Document

object WpsePlaylistParser {
    private val gson = Gson()

    fun parse(document: Document, videoId: String): List<VideoEpisode> {
        val playlistJson = document.selectFirst("script.wpse-playlist-data")?.html()
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        val seasons = (gson.fromJson(playlistJson, Map::class.java)["seasons"] as? List<*>)
            ?: return emptyList()
        val showSeasonName = seasons.size > 1
        return seasons.flatMap { season ->
            val seasonInfo = season as? Map<*, *> ?: return@flatMap emptyList()
            val seasonTitle = seasonInfo["title"]?.toString()?.trim().orEmpty()
            val tracks = seasonInfo["tracks"] as? List<*> ?: emptyList<Any?>()
            tracks.mapNotNull { track ->
                val trackInfo = track as? Map<*, *> ?: return@mapNotNull null
                val src = trackInfo["src"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val episode = trackInfo["episode"]?.let(::formatPlaylistNumber).orEmpty()
                val title = trackInfo["title"]?.toString()?.takeIf { it.isNotBlank() }
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

    private fun formatPlaylistNumber(value: Any): String {
        val raw = value.toString()
        return raw.removeSuffix(".0")
    }
}
