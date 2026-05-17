package com.jing.ddys.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import com.jing.ddys.repository.HomeVideoCacheEntity
import com.jing.ddys.repository.VideoCardInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Dy555DatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: Dy555Database? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun migrationFrom1To2KeepsExistingTablesAndCreatesHomeCache() {
        createVersion1Database()

        database = Room.databaseBuilder(context, Dy555Database::class.java, DB_NAME)
            .addMigrations(Dy555Database.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        val migrated = database!!
        val oldRows = migrated.openHelper.readableDatabase.query(
            SimpleSQLiteQuery("select count(*) from search_history")
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        kotlinx.coroutines.runBlocking {
            migrated.homeVideoCacheDao().replacePage(
                category = "/",
                page = 1,
                videos = listOf(
                    VideoCardInfo(
                        imageUrl = "https://example.com/poster.jpg",
                        title = "Cached",
                        url = "https://example.com/cached",
                        subTitle = "subtitle"
                    )
                ),
                cachedAt = 100L
            )
        }

        assertEquals(1, oldRows)
        assertEquals(
            listOf("Cached"),
            migrated.homeVideoCacheDao().queryCategory("/").loadForTest().map { it.title }
        )
    }

    private fun createVersion1Database() {
        context.deleteDatabase(DB_NAME)
        val dbPath = context.getDatabasePath(DB_NAME)
        dbPath.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbPath, null).use { db ->
            db.execSQL(
                "create table if not exists `search_history` " +
                    "(`keyword` text not null, `searchTime` integer not null, primary key(`keyword`))"
            )
            db.execSQL(
                "create table if not exists `video_history` " +
                    "(`id` text not null, `title` text not null, `pic` text not null, " +
                    "`epId` text, primary key(`id`))"
            )
            db.execSQL(
                "create table if not exists `episode_history` " +
                    "(`id` text not null, `videoId` text not null, `name` text not null, " +
                    "`progress` integer not null, `duration` integer not null, " +
                    "`timestamp` integer not null, primary key(`id`))"
            )
            db.execSQL(
                "create index if not exists `index_episode_history_videoId` " +
                    "on `episode_history` (`videoId`)"
            )
            db.execSQL("insert into search_history(keyword, searchTime) values('abc', 1)")
            db.version = 1
        }
    }

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

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
