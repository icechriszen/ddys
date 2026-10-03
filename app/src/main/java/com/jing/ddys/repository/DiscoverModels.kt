package com.jing.ddys.repository

import java.math.BigDecimal
import java.util.Calendar

enum class DiscoverSort(val parameter: String) {
    Modified("modified"), Published("published"), Year("year"), Score("score")
}

data class DiscoverFilters(
    val region: String? = null,
    val genres: Set<String> = emptySet(),
    val yearMin: Int? = null,
    val yearMax: Int? = null,
    val scoreMin: Double? = null,
    val scoreMax: Double? = null,
    val sort: DiscoverSort = DiscoverSort.Modified
) {
    val isActive: Boolean get() = this != DiscoverFilters()
}

data class DiscoverChoice(val value: String, val label: String)

data class DiscoverOptions(
    val categories: List<DiscoverChoice>,
    val regions: List<DiscoverChoice>,
    val genres: List<DiscoverChoice>,
    val tags: List<DiscoverChoice>
)

data class DiscoverPage(
    val data: List<VideoCardInfo>,
    val page: Int,
    val hasNext: Boolean,
    val totalCount: Int
)

class DiscoverCompatibilityException(message: String) : RuntimeException(message)

enum class DiscoverFieldError { InvalidYear, YearOrder, InvalidScore, ScoreOrder }

/** Raw form values deliberately remain separate from applied, valid filters. */
data class DiscoverDraft(
    val region: String? = null,
    val genres: Set<String> = emptySet(),
    val yearMin: String = "",
    val yearMax: String = "",
    val scoreMin: String = "",
    val scoreMax: String = "",
    val sort: DiscoverSort = DiscoverSort.Modified,
    val customYear: Boolean = false,
    val customScore: Boolean = false
) {
    val yearError: DiscoverFieldError? get() {
        if (listOf(yearMin, yearMax).any { it.isNotBlank() &&
                    (it.trim().toIntOrNull()?.let { year -> year in 1000..9999 } != true) }) {
            return DiscoverFieldError.InvalidYear
        }
        return if (yearMin.isNotBlank() && yearMax.isNotBlank() &&
            yearMin.trim().toInt() > yearMax.trim().toInt()) DiscoverFieldError.YearOrder else null
    }
    val scoreError: DiscoverFieldError? get() {
        if (listOf(scoreMin, scoreMax).any { it.isNotBlank() &&
                    (!Regex("[0-9]+(?:\\.[0-9])?").matches(it.trim()) ||
                        it.trim().toDoubleOrNull()?.let { score -> score in 0.0..10.0 } != true) }) {
            return DiscoverFieldError.InvalidScore
        }
        return if (scoreMin.isNotBlank() && scoreMax.isNotBlank() &&
            scoreMin.trim().toDouble() > scoreMax.trim().toDouble()) DiscoverFieldError.ScoreOrder else null
    }
    val isValid: Boolean get() = yearError == null && scoreError == null

    fun toFilters(): DiscoverFilters {
        require(isValid)
        return DiscoverFilters(region, genres.toSet(), yearMin.trim().toIntOrNull(),
            yearMax.trim().toIntOrNull(), scoreMin.trim().toDoubleOrNull(),
            scoreMax.trim().toDoubleOrNull(), sort)
    }

    companion object {
        fun from(filters: DiscoverFilters, currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)) = DiscoverDraft(
            region = filters.region, genres = filters.genres.toSet(),
            yearMin = filters.yearMin?.toString().orEmpty(), yearMax = filters.yearMax?.toString().orEmpty(),
            scoreMin = filters.scoreMin?.let(::formatDiscoverScore).orEmpty(),
            scoreMax = filters.scoreMax?.let(::formatDiscoverScore).orEmpty(), sort = filters.sort,
            customYear = (filters.yearMin != null || filters.yearMax != null) &&
                !(filters.yearMax == currentYear && filters.yearMin in listOf(currentYear, currentYear - 4)),
            customScore = filters.scoreMax != null || (filters.scoreMin != null && filters.scoreMin !in listOf(6.0, 7.0, 8.0, 9.0))
        )
    }
}

fun formatDiscoverScore(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

data class HomeQuery(val category: String = "/", val filters: DiscoverFilters = DiscoverFilters())

/** A query-scoped count prevents delayed pages from relabelling a newer list. */
data class DiscoverCount(val query: HomeQuery, val value: Int)
