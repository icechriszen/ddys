package com.jing.ddys.repository

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class DiscoverTest {
    private val html = javaClass.getResource("/discover/page.html")!!.readText()
    private val base = "https://example.test"
    private val options get() = DiscoverParser.options(html)

    @Test fun parsesRealOptionsWithoutMixingOtherTagsIntoGenres() {
        assertEquals("64", options.categories.first { it.label == "电影" }.value)
        assertTrue(options.regions.any { it.label == "中国大陆" })
        assertEquals(11, options.genres.size)
        assertFalse(options.genres.any { it.label == "豆瓣电影Top250" })
        assertTrue(options.tags.any { it.label == "豆瓣电影Top250" })
    }

    @Test fun parsesCardsAndPaginationWithoutFetchingDetails() {
        val page = DiscoverParser.page(html, "$base/discover/", 1)
        assertEquals(126, page.totalCount)
        assertEquals(24, page.data.size)
        assertTrue(page.hasNext)
        assertEquals("FX战士久留美 (更新至01)", page.data.first().title)
        assertEquals("2026 · 日本 · 豆瓣 0.0", page.data.first().subTitle)
    }

    @Test fun matchesObservedWebsiteCombinationsAndSecondPage() {
        fun sample(name: String, page: Int = 1) = DiscoverParser.page(
            javaClass.getResource("/discover/$name.html")!!.readText(), "$base/discover/?pg=$page", page)
        assertEquals(listOf("星际穿越", "挽救计划"), sample("filtered").data.map { it.title })
        assertEquals(listOf("星际穿越"), sample("range").data.map { it.title })
        assertEquals(1, sample("range").totalCount)
        assertFalse(sample("range").hasNext)
        val second = sample("page2", 2)
        assertTrue(second.hasNext)
        assertEquals(24, second.data.size)
        assertTrue(second.data.none { next -> DiscoverParser.page(html, base, 1).data.any { it.url == next.url } })
    }

    @Test fun missingOptionalMetadataAndPosterAreAllowed() {
        val doc = Jsoup.parse(html)
        val card = doc.selectFirst("article")!!
        card.select("img, p").remove()
        val first = DiscoverParser.page(doc.html(), "$base/discover/", 1).data.first()
        assertEquals("", first.imageUrl)
        assertNull(first.subTitle)
    }

    @Test fun emptyResultsAndLastPageAreDistinctFromBrokenMarkup() {
        assertEquals(0, DiscoverParser.page("<p class='ddys-discover-total'>0 部视频</p>", "$base/discover/", 1).data.size)
        val doc = Jsoup.parse(html)
        doc.selectFirst("nav.ddys-discover-pagination")!!.html("<span>第 6 页／共 6 页</span>")
        assertFalse(DiscoverParser.page(doc.html(), "$base/discover/?pg=6", 6).hasNext)
        val broken = Jsoup.parse(html)
        broken.select("article").remove()
        assertThrows(DiscoverCompatibilityException::class.java) { DiscoverParser.page(broken.html(), "$base/discover/", 1) }
        assertThrows(DiscoverCompatibilityException::class.java) { DiscoverParser.page("<html>Maintenance</html>", base, 1) }
        assertThrows(DiscoverCompatibilityException::class.java) { DiscoverParser.page(html, base, 2) }
        assertThrows(DiscoverCompatibilityException::class.java) { DiscoverParser.options("<html>Maintenance</html>") }
    }

    @Test fun buildsEncodedMultiTagRangeAndSortRequestWithWebsitePageParameter() {
        val filters = DiscoverFilters("中国大陆", setOf("科幻", "悬疑"), 2020, 2026, 8.0, 9.5, DiscoverSort.Score)
        val url = DiscoverRequest.url(base, HomeQuery("/category/movie/", filters), options, 3).toHttpUrl()
        assertEquals("/discover/", url.encodedPath)
        assertEquals("64", url.queryParameter("categories[]"))
        assertEquals("中国大陆", url.queryParameter("regions[]"))
        assertEquals(setOf("1006", "1011"), url.queryParameterValues("tags[]").toSet())
        assertEquals("all", url.queryParameter("tag_mode"))
        assertEquals("douban", url.queryParameter("source"))
        assertEquals("8", url.queryParameter("min"))
        assertEquals("9.5", url.queryParameter("max"))
        assertEquals("2020", url.queryParameter("year_min"))
        assertEquals("2026", url.queryParameter("year_max"))
        assertEquals("score", url.queryParameter("sort"))
        assertEquals("3", url.queryParameter("pg"))
    }

    @Test fun resolvesSpecialCategoriesUsingCurrentWebsiteIds() {
        val changed = options.copy(categories = options.categories.map { it.copy(value = "new-${it.value}") })
        assertEquals("new-41", DiscoverRequest.url(base, HomeQuery("/category/airing/"), changed, 1)
            .toHttpUrl().queryParameter("categories[]"))
        val top = DiscoverRequest.url(base, HomeQuery("/tag/douban-top250/", DiscoverFilters(genres = setOf("科幻"))), options, 1).toHttpUrl()
        assertNull(top.queryParameter("categories[]"))
        assertEquals(setOf("8161", "1006"), top.queryParameterValues("tags[]").toSet())
        assertThrows(DiscoverCompatibilityException::class.java) {
            DiscoverRequest.url(base, HomeQuery("/unknown/"), options, 1)
        }
        assertThrows(DiscoverCompatibilityException::class.java) {
            DiscoverRequest.url(base, HomeQuery(filters = DiscoverFilters(genres = setOf("不存在"))), options, 1)
        }
    }

    @Test fun validatesBoundariesOpenRangesAndDecimalPrecision() {
        assertTrue(DiscoverDraft(yearMin = "1000", yearMax = "9999", scoreMin = "0", scoreMax = "10").isValid)
        assertTrue(DiscoverDraft(yearMin = "2020", scoreMax = "8.5").isValid)
        assertNull(DiscoverDraft(yearMin = "2020", scoreMax = "8.5").toFilters().yearMax)
        assertEquals(DiscoverFieldError.InvalidYear, DiscoverDraft(yearMin = "999").yearError)
        assertEquals(DiscoverFieldError.YearOrder, DiscoverDraft(yearMin = "2026", yearMax = "2020").yearError)
        assertEquals(DiscoverFieldError.InvalidScore, DiscoverDraft(scoreMin = "8.55").scoreError)
        assertEquals(DiscoverFieldError.InvalidScore, DiscoverDraft(scoreMin = "NaN").scoreError)
        assertEquals(DiscoverFieldError.InvalidScore, DiscoverDraft(scoreMax = "10.1").scoreError)
        assertEquals(DiscoverFieldError.ScoreOrder, DiscoverDraft(scoreMin = "9", scoreMax = "8").scoreError)
        assertFalse(DiscoverFilters().isActive)
        assertTrue(DiscoverFilters(sort = DiscoverSort.Score).isActive)
    }

    @Test fun cachesOptionsBySourceAndRetriesAfterAccessVerificationFailure() {
        var source = base
        var fail = true
        val requests = mutableListOf<String>()
        val repository = DiscoverRepository({ source }) { url ->
            requests += url
            if (fail) throw SourceAuthRequiredException()
            html
        }
        assertThrows(SourceAuthRequiredException::class.java) { repository.fetchPage(HomeQuery(), 1) }
        fail = false
        assertEquals(126, repository.fetchPage(HomeQuery(), 1).totalCount)
        repository.options()
        assertEquals(2, requests.count { it == "$base/discover/" })
        source = "https://another.test"
        repository.options()
        assertEquals("https://another.test/discover/", requests.last())
    }
}
