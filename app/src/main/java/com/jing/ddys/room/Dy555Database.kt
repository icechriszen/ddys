package com.jing.ddys.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jing.ddys.room.dao.EpisodeHistoryDao
import com.jing.ddys.room.dao.HomeVideoCacheDao
import com.jing.ddys.room.dao.SearchHistoryDao
import com.jing.ddys.room.dao.VideoHistoryDao
import com.jing.ddys.room.entity.EpisodeHistory
import com.jing.ddys.room.entity.SearchHistory
import com.jing.ddys.room.entity.VideoHistory
import com.jing.ddys.repository.HomeVideoCacheEntity

@Database(
    entities = [
        SearchHistory::class,
        VideoHistory::class,
        EpisodeHistory::class,
        HomeVideoCacheEntity::class
    ], version = 2
)
abstract class Dy555Database : RoomDatabase() {

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun videoHistoryDao(): VideoHistoryDao

    abstract fun episodeHistoryDao(): EpisodeHistoryDao

    abstract fun homeVideoCacheDao(): HomeVideoCacheDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    create table if not exists `home_video_cache` (
                        `category` text not null,
                        `page` integer not null,
                        `position` integer not null,
                        `url` text not null,
                        `title` text not null,
                        `imageUrl` text not null,
                        `subTitle` text,
                        `cachedAt` integer not null,
                        primary key(`category`, `url`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    create index if not exists `index_home_video_cache_category_page_position`
                    on `home_video_cache` (`category`, `page`, `position`)
                    """.trimIndent()
                )
            }
        }
    }
}
