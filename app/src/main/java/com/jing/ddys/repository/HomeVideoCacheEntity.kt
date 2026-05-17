package com.jing.ddys.repository

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "home_video_cache",
    primaryKeys = ["category", "url"],
    indices = [
        Index(value = ["category", "page", "position"])
    ]
)
data class HomeVideoCacheEntity(
    val category: String,
    val page: Int,
    val position: Int,
    val url: String,
    val title: String,
    val imageUrl: String,
    val subTitle: String?,
    val cachedAt: Long
) {
    fun toVideoCardInfo(): VideoCardInfo = VideoCardInfo(
        imageUrl = imageUrl,
        title = title,
        url = url,
        subTitle = subTitle
    )

    companion object {
        fun fromVideoCardInfo(
            category: String,
            page: Int,
            position: Int,
            video: VideoCardInfo,
            cachedAt: Long
        ): HomeVideoCacheEntity = HomeVideoCacheEntity(
            category = category,
            page = page,
            position = position,
            url = video.url,
            title = video.title,
            imageUrl = video.imageUrl,
            subTitle = video.subTitle,
            cachedAt = cachedAt
        )
    }
}
