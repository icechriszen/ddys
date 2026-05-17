package com.jing.ddys.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeVideoCacheEntityTest {

    @Test
    fun mapsVideoCardInfoWithoutLosingFields() {
        val video = VideoCardInfo(
            imageUrl = "https://example.com/poster.jpg",
            title = "Title",
            url = "https://example.com/detail",
            subTitle = "Updated today"
        )

        val entity = HomeVideoCacheEntity.fromVideoCardInfo(
            category = "/",
            page = 1,
            position = 2,
            video = video,
            cachedAt = 123L
        )
        val mapped = entity.toVideoCardInfo()

        assertEquals("/", entity.category)
        assertEquals(1, entity.page)
        assertEquals(2, entity.position)
        assertEquals(123L, entity.cachedAt)
        assertEquals(video.url, mapped.url)
        assertEquals(video.title, mapped.title)
        assertEquals(video.imageUrl, mapped.imageUrl)
        assertEquals(video.subTitle, mapped.subTitle)
    }
}
