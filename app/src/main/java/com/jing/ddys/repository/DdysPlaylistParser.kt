package com.jing.ddys.repository

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.jsoup.nodes.Document

object DdysPlaylistParser {
    fun parse(document: Document, videoId: String): List<VideoEpisode> {
        val seasons = document.selectFirst("script.ddys-playlist-data")?.html()
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
                val server = trackInfo.get("server")?.asString?.takeIf { it.isNotBlank() }
                val episode = trackInfo.get("episode")?.let(::formatPlaylistNumber).orEmpty()
                val rawTitle = trackInfo.get("title")?.asString?.trim().orEmpty()
                val name = formatTrackName(rawTitle, episode, src)
                VideoEpisode(
                    id = "$videoId|$seasonTitle|$src",
                    name = name,
                    subTitleUrl = trackInfo.get("subsrc")?.asString?.takeIf { it.isNotBlank() }
                        ?.let(VideoSourceAuth::resolveSiteUrl)
                        .orEmpty(),
                    seasonName = seasonTitle.takeIf { showSeasonName }.orEmpty(),
                    src0 = resolveTrackUrl(src, server)
                )
            }
        }
    }

    private fun formatTrackName(title: String, episode: String, src: String): String {
        return when {
            title.isNotBlank() && title.toIntOrNull() != null -> "第${title}集"
            title.isNotBlank() -> title
            episode.isNotBlank() -> "第${episode}集"
            else -> src.substringAfterLast('/').substringBeforeLast('.')
        }
    }

    private fun resolveTrackUrl(src: String, server: String?): String {
        val normalizedServer = server?.trim()?.trimEnd('/')
            ?.removePrefix("https://")
            ?.removePrefix("http://")
        return if (normalizedServer.isNullOrBlank()) {
            src
        } else if (normalizedServer.contains('.')) {
            "https://$normalizedServer/" + src.trimStart('/')
        } else {
            "https://$normalizedServer.ddys.app/" + src.trimStart('/')
        }
    }

    private fun formatPlaylistNumber(value: JsonElement): String {
        val raw = value.asString
        return raw.removeSuffix(".0")
    }
}
