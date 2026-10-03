package com.jing.ddys.repository

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup

object DiscoverParser {
    fun options(html: String): DiscoverOptions {
        val doc = Jsoup.parse(html)
        fun choices(selector: String): List<DiscoverChoice> = doc.select(selector).mapNotNull { input ->
            val label = input.closest("label")?.text()?.trim().orEmpty()
            val value = input.attr("value")
            if (label.isBlank() || value.isBlank()) null else DiscoverChoice(value, label)
        }.distinctBy { it.value }
        val result = DiscoverOptions(
            choices("[aria-labelledby=discover-categories] input[name='categories[]']"),
            choices("[aria-labelledby=discover-regions] input[name='regions[]']"),
            choices("[aria-labelledby=discover-genres] input[name='tags[]']"),
            choices("form#ddys-discover-form input[name='tags[]']")
        )
        if (result.categories.isEmpty() || result.regions.isEmpty() || result.genres.isEmpty()) {
            throw DiscoverCompatibilityException("发现页筛选选项结构已变化")
        }
        return result
    }

    fun page(html: String, pageUrl: String, requestedPage: Int): DiscoverPage {
        val doc = Jsoup.parse(html, pageUrl)
        val total = doc.selectFirst(".ddys-discover-total")?.text()
            ?.let { Regex("([0-9,]+)\\s*部视频").find(it)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() }
            ?: throw DiscoverCompatibilityException("发现页结果数量结构已变化")
        val cards = doc.select("article.ddys-discover-card").map { article ->
            val link = article.selectFirst("h2 a[href]")
                ?: throw DiscoverCompatibilityException("发现页影片链接缺失")
            val title = link.text().trim()
            val url = link.absUrl("href")
            if (title.isEmpty() || url.isEmpty()) throw DiscoverCompatibilityException("发现页影片信息不完整")
            val poster = article.selectFirst(".ddys-discover-poster img")
            val image = poster?.absUrl("src").orEmpty().ifBlank { poster?.absUrl("data-src").orEmpty() }
            val metadata = article.select(".ddys-discover-meta").map { it.text().trim() }
                .firstOrNull { Regex("^\\d{4}(?:\\s|$)").containsMatchIn(it) }.orEmpty()
            val score = article.selectFirst(".ddys-discover-score")?.text()?.trim().orEmpty()
            VideoCardInfo(image, title, url,
                listOf(metadata, score).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null })
        }.distinctBy { it.url }
        if (total > 0 && cards.isEmpty()) throw DiscoverCompatibilityException("发现页影片列表结构已变化")
        val navigation = doc.selectFirst("nav.ddys-discover-pagination")
            ?: if (total > 0) throw DiscoverCompatibilityException("发现页分页结构已变化") else null
        val pageNumbers = navigation?.text()?.let { Regex("第\\s*(\\d+)\\s*页[／/]共\\s*(\\d+)\\s*页").find(it) }
        if (total > 0 && pageNumbers == null) throw DiscoverCompatibilityException("发现页分页信息缺失")
        if (pageNumbers != null && pageNumbers.groupValues[1].toInt() != requestedPage) {
            throw DiscoverCompatibilityException("发现页返回了错误的页码")
        }
        val hasNext = (pageNumbers?.groupValues?.get(2)?.toIntOrNull() ?: 0) > requestedPage
        if (hasNext && navigation?.select("a[href]")?.none {
                it.absUrl("href").toHttpUrl().queryParameter("pg")?.toIntOrNull() == requestedPage + 1
            } != false) throw DiscoverCompatibilityException("发现页下一页链接缺失")
        return DiscoverPage(cards, requestedPage, hasNext, total)
    }
}

object DiscoverRequest {
    private val categoryNames = mapOf(
        "/category/anime/new-bangumi/" to "本季新番",
        "/category/airing/" to "热映中", "/category/movie/" to "电影",
        "/category/drama/kr-drama/" to "韩剧", "/category/anime/" to "动画",
        "/category/movie/western-movie/" to "欧美电影", "/category/movie/asian-movie/" to "日韩电影",
        "/category/movie/chinese-movie/" to "华语电影", "/category/drama/western-drama/" to "欧美剧",
        "/category/drama/cn-drama/" to "华语剧", "/category/drama/jp-drama/" to "日剧"
    )

    fun url(baseUrl: String, query: HomeQuery, options: DiscoverOptions, page: Int): String {
        require(page >= 1)
        require(DiscoverDraft.from(query.filters).isValid)
        fun resolve(choices: List<DiscoverChoice>, label: String): String =
            choices.firstOrNull { it.label == label }?.value
                ?: throw DiscoverCompatibilityException("网站不再提供筛选条件：$label")
        val builder = baseUrl.toHttpUrl().newBuilder().encodedPath("/discover/").query(null).fragment(null)
        val tags = linkedSetOf<String>()
        when (query.category) {
            "/" -> Unit
            "/tag/douban-top250/" -> tags += resolve(options.tags, "豆瓣电影Top250")
            else -> {
                val name = categoryNames[query.category]
                    ?: throw DiscoverCompatibilityException("此栏目尚不能组合筛选")
                builder.addQueryParameter("categories[]", resolve(options.categories, name))
            }
        }
        query.filters.region?.let { builder.addQueryParameter("regions[]", resolve(options.regions, it)) }
        query.filters.genres.sorted().forEach { tags += resolve(options.genres, it) }
        tags.forEach { builder.addQueryParameter("tags[]", it) }
        builder.addQueryParameter("source", "douban").addQueryParameter("tag_mode", "all")
        query.filters.yearMin?.let { builder.addQueryParameter("year_min", it.toString()) }
        query.filters.yearMax?.let { builder.addQueryParameter("year_max", it.toString()) }
        query.filters.scoreMin?.let { builder.addQueryParameter("min", formatDiscoverScore(it)) }
        query.filters.scoreMax?.let { builder.addQueryParameter("max", formatDiscoverScore(it)) }
        return builder.addQueryParameter("sort", query.filters.sort.parameter)
            .addQueryParameter("pg", page.toString()).build().toString()
    }
}
