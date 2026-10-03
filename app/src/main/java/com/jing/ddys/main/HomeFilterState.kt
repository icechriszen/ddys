package com.jing.ddys.main

import com.jing.ddys.repository.DiscoverDraft
import com.jing.ddys.repository.DiscoverFilters
import com.jing.ddys.repository.HomeQuery

/** The draft never participates in the query until Apply is selected. */
data class HomeFilterState(
    val query: HomeQuery = HomeQuery(),
    val draft: DiscoverDraft? = null,
    val generation: Long = 0
) {
    fun open() = copy(draft = DiscoverDraft.from(query.filters))
    fun cancel() = copy(draft = null)
    fun edit(value: DiscoverDraft) = if (draft == null) this else copy(draft = value)
    fun resetDraft() = edit(DiscoverDraft())
    fun apply(): HomeFilterState {
        val value = draft ?: return this
        if (!value.isValid) return this
        return copy(query = query.copy(filters = value.toFilters()), draft = null, generation = generation + 1)
    }
    fun clear() = copy(query = query.copy(filters = DiscoverFilters()), draft = null, generation = generation + 1)
    fun category(path: String) = if (query.category == path) this else
        copy(query = query.copy(category = path), generation = generation + 1)
    fun acceptsCount(requestGeneration: Long) = generation == requestGeneration
}
