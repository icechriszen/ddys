package com.jing.ddys.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface HomeVideoCacheWriter {
    suspend fun clearCategory(category: String)

    suspend fun replacePage(
        category: String,
        page: Int,
        videos: List<VideoCardInfo>,
        cachedAt: Long
    )
}

@OptIn(ExperimentalPagingApi::class)
class HomeRemoteMediator(
    private val category: String,
    private val fetchPage: suspend (page: Int) -> BasePageResult<VideoCardInfo>,
    private val cacheWriter: HomeVideoCacheWriter,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) : RemoteMediator<Int, HomeVideoCacheEntity>() {

    override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, HomeVideoCacheEntity>
    ): MediatorResult {
        val page = when (loadType) {
            LoadType.REFRESH -> 1
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val lastItem = state.pages.lastOrNull { it.data.isNotEmpty() }?.data?.lastOrNull()
                lastItem?.page?.plus(1)
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        }

        return try {
            val result = withContext(Dispatchers.IO) {
                fetchPage(page)
            }
            if (loadType == LoadType.REFRESH) {
                cacheWriter.clearCategory(category)
            }
            cacheWriter.replacePage(
                category = category,
                page = page,
                videos = result.data,
                cachedAt = clockMillis()
            )
            MediatorResult.Success(endOfPaginationReached = !result.hasNext)
        } catch (ex: Exception) {
            MediatorResult.Error(ex)
        }
    }
}
