package com.jing.ddys.room

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jing.ddys.repository.HomeVideoCacheEntity
import com.jing.ddys.repository.VideoCardInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeVideoCacheDaoTest {

    private lateinit var database: Dy555Database

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            Dy555Database::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replacePageStoresCategoryItemsInPageOrder() {
        val dao = database.homeVideoCacheDao()

        kotlinx.coroutines.runBlocking {
            dao.replacePage(
                category = "/",
                page = 1,
                videos = listOf(video("b"), video("a")),
                cachedAt = 100L
            )
            dao.replacePage(
                category = "/category/movie/",
                page = 1,
                videos = listOf(video("other")),
                cachedAt = 200L
            )
        }

        val rows = dao.queryCategory("/").loadForTest()

        assertEquals(listOf("b", "a"), rows.map { it.title })
        assertEquals(listOf(0, 1), rows.map { it.position })
        assertEquals(listOf(100L, 100L), rows.map { it.cachedAt })
    }

    @Test
    fun clearCategoryOnlyRemovesRequestedCategory() {
        val dao = database.homeVideoCacheDao()

        kotlinx.coroutines.runBlocking {
            dao.replacePage("/", 1, listOf(video("home")), 100L)
            dao.replacePage("/category/movie/", 1, listOf(video("movie")), 100L)
            dao.clearCategory("/")
        }

        assertEquals(emptyList<String>(), dao.queryCategory("/").loadForTest().map { it.title })
        assertEquals(
            listOf("movie"),
            dao.queryCategory("/category/movie/").loadForTest().map { it.title }
        )
    }

    private fun video(title: String) = VideoCardInfo(
        imageUrl = "https://example.com/$title.jpg",
        title = title,
        url = "https://example.com/$title",
        subTitle = "sub-$title"
    )

    private fun PagingSource<Int, HomeVideoCacheEntity>.loadForTest(): List<HomeVideoCacheEntity> {
        val result = kotlinx.coroutines.runBlocking {
            load(
                PagingSource.LoadParams.Refresh(
                    key = null,
                    loadSize = 50,
                    placeholdersEnabled = false
                )
            )
        }
        return (result as PagingSource.LoadResult.Page).data
    }
}
