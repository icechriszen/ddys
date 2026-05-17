package com.jing.ddys.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalPagingApi::class)
class HomeRemoteMediatorTest {

    @Test
    fun refreshClearsCategoryAndWritesFirstPage() = runBlocking {
        val writer = FakeHomeVideoCacheWriter()
        val mediator = HomeRemoteMediator(
            category = "/",
            fetchPage = { page ->
                BasePageResult(
                    data = listOf(video("first")),
                    page = page,
                    hasNext = true
                )
            },
            cacheWriter = writer,
            clockMillis = { 10L }
        )

        val result = mediator.load(LoadType.REFRESH, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertFalse((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(listOf("/"), writer.clearedCategories)
        assertEquals(1, writer.writes.single().page)
        assertEquals(10L, writer.writes.single().cachedAt)
        assertEquals("first", writer.writes.single().videos.single().title)
    }

    @Test
    fun appendWritesNextPageFromLastCachedItem() = runBlocking {
        val writer = FakeHomeVideoCacheWriter()
        val mediator = HomeRemoteMediator(
            category = "/category/drama/",
            fetchPage = { page ->
                BasePageResult(
                    data = listOf(video("next")),
                    page = page,
                    hasNext = false
                )
            },
            cacheWriter = writer,
            clockMillis = { 20L }
        )

        val result = mediator.load(
            LoadType.APPEND,
            PagingState(
                pages = listOf(
                    androidx.paging.PagingSource.LoadResult.Page(
                        data = listOf(
                            HomeVideoCacheEntity.fromVideoCardInfo(
                                category = "/category/drama/",
                                page = 2,
                                position = 0,
                                video = video("existing"),
                                cachedAt = 1L
                            )
                        ),
                        prevKey = null,
                        nextKey = null
                    )
                ),
                anchorPosition = null,
                config = PagingConfig(pageSize = 24),
                leadingPlaceholderCount = 0
            )
        )

        assertTrue(result is RemoteMediator.MediatorResult.Success)
        assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
        assertEquals(emptyList<String>(), writer.clearedCategories)
        assertEquals(3, writer.writes.single().page)
        assertEquals("next", writer.writes.single().videos.single().title)
    }

    @Test
    fun networkFailureReturnsErrorWithoutClearingCache() = runBlocking {
        val writer = FakeHomeVideoCacheWriter()
        val mediator = HomeRemoteMediator(
            category = "/",
            fetchPage = { throw RuntimeException("network down") },
            cacheWriter = writer,
            clockMillis = { 10L }
        )

        val result = mediator.load(LoadType.REFRESH, emptyState())

        assertTrue(result is RemoteMediator.MediatorResult.Error)
        assertEquals(emptyList<String>(), writer.clearedCategories)
        assertEquals(emptyList<FakeHomeVideoCacheWriter.Write>(), writer.writes)
    }

    private fun emptyState() = PagingState<Int, HomeVideoCacheEntity>(
        pages = emptyList(),
        anchorPosition = null,
        config = PagingConfig(pageSize = 24),
        leadingPlaceholderCount = 0
    )

    private fun video(title: String) = VideoCardInfo(
        imageUrl = "https://example.com/$title.jpg",
        title = title,
        url = "https://example.com/$title",
        subTitle = "sub-$title"
    )

    private class FakeHomeVideoCacheWriter : HomeVideoCacheWriter {
        data class Write(
            val category: String,
            val page: Int,
            val videos: List<VideoCardInfo>,
            val cachedAt: Long
        )

        val clearedCategories = mutableListOf<String>()
        val writes = mutableListOf<Write>()

        override suspend fun clearCategory(category: String) {
            clearedCategories += category
        }

        override suspend fun replacePage(
            category: String,
            page: Int,
            videos: List<VideoCardInfo>,
            cachedAt: Long
        ) {
            writes += Write(category, page, videos, cachedAt)
        }
    }
}
