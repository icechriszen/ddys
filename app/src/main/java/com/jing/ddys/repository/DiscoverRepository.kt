package com.jing.ddys.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

class DiscoverRepository(
    private val sourceUrl: () -> String = { VideoSourceAuth.siteBaseUrl },
    private val fetchHtml: (String) -> String = HttpUtil::fetchDiscoverHtml
) {
    private val optionCache = mutableMapOf<String, DiscoverOptions>()

    @Synchronized
    fun options(): DiscoverOptions = optionsFor(sourceUrl())

    @Synchronized
    private fun optionsFor(baseUrl: String): DiscoverOptions = optionCache.getOrPut(baseUrl) {
        DiscoverParser.options(fetchHtml("${baseUrl.trimEnd('/')}/discover/"))
    }

    fun fetchPage(query: HomeQuery, page: Int): DiscoverPage {
        val baseUrl = sourceUrl()
        val url = DiscoverRequest.url(baseUrl, query, optionsFor(baseUrl), page)
        return DiscoverParser.page(fetchHtml(url), url, page)
    }

    fun pager(query: HomeQuery, onCount: (Int) -> Unit): Flow<PagingData<VideoCardInfo>> = Pager(
        PagingConfig(pageSize = 24, initialLoadSize = 24, prefetchDistance = 8, enablePlaceholders = false)
    ) {
        BasicPagingSource { page ->
            val result = fetchPage(query, page)
            onCount(result.totalCount)
            BasePageResult(result.data, result.page, result.hasNext)
        }
    }.flow
}
