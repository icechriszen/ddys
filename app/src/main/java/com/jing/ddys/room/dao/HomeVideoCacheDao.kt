package com.jing.ddys.room.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.jing.ddys.repository.HomeVideoCacheEntity
import com.jing.ddys.repository.HomeVideoCacheWriter
import com.jing.ddys.repository.VideoCardInfo

@Dao
interface HomeVideoCacheDao : HomeVideoCacheWriter {

    @Query(
        """
        select *
        from home_video_cache
        where category = :category
        order by page asc, position asc
        """
    )
    fun queryCategory(category: String): PagingSource<Int, HomeVideoCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HomeVideoCacheEntity>)

    @Query("delete from home_video_cache where category = :category")
    override suspend fun clearCategory(category: String)

    @Query("delete from home_video_cache where category = :category and page = :page")
    suspend fun clearPage(category: String, page: Int)

    @Transaction
    override suspend fun replacePage(
        category: String,
        page: Int,
        videos: List<VideoCardInfo>,
        cachedAt: Long
    ) {
        clearPage(category, page)
        insertAll(
            videos.mapIndexed { index, video ->
                HomeVideoCacheEntity.fromVideoCardInfo(
                    category = category,
                    page = page,
                    position = index,
                    video = video,
                    cachedAt = cachedAt
                )
            }
        )
    }
}
