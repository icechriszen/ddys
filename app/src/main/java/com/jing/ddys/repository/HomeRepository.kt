package com.jing.ddys.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.jing.ddys.room.dao.HomeVideoCacheDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HomeRepository(
    private val cacheDao: HomeVideoCacheDao,
    private val fetchPage: suspend (category: String, page: Int) -> BasePageResult<VideoCardInfo> =
        { category, page -> HttpUtil.queryVideoOfCategory(category, page) }
) {

    @OptIn(ExperimentalPagingApi::class)
    fun pagerForCategory(category: String): Flow<PagingData<VideoCardInfo>> {
        return Pager(
            config = PagingConfig(
                pageSize = 24,
                initialLoadSize = 24,
                prefetchDistance = 8,
                enablePlaceholders = false
            ),
            remoteMediator = HomeRemoteMediator(
                category = category,
                fetchPage = { page -> fetchPage(category, page) },
                cacheWriter = cacheDao
            )
        ) {
            cacheDao.queryCategory(category)
        }.flow.map { pagingData ->
            pagingData.map(HomeVideoCacheEntity::toVideoCardInfo)
        }
    }
}
