package com.jing.ddys.main

import com.jing.ddys.repository.*
import org.junit.Assert.*
import org.junit.Test

class HomeFilterStateTest {
    @Test fun editingAndCancellingNeverChangeTheAppliedQuery() {
        val start = HomeFilterState().category("/category/movie/")
        val editing = start.open().edit(DiscoverDraft(region = "美国", scoreMin = "8"))
        assertEquals(start.query, editing.query)
        assertEquals(start.generation, editing.generation)
        assertEquals(start, editing.cancel())
    }

    @Test fun applyingResetsPagingAndCategorySwitchKeepsConditions() {
        val applied = HomeFilterState().open().edit(DiscoverDraft(region = "美国", genres = setOf("科幻"))).apply()
        assertNull(applied.draft)
        assertEquals(1L, applied.generation)
        val switched = applied.category("/category/drama/western-drama/")
        assertEquals(applied.query.filters, switched.query.filters)
        assertEquals(2L, switched.generation)
        assertTrue(switched.open().apply().generation > switched.generation)
    }

    @Test fun resetIsADraftUntilAppliedAndClearKeepsCategory() {
        val applied = HomeFilterState().category("/category/movie/").open()
            .edit(DiscoverDraft(scoreMin = "8", sort = DiscoverSort.Score)).apply()
        val reset = applied.open().resetDraft()
        assertEquals(applied.query, reset.query)
        assertFalse(reset.apply().query.filters.isActive)
        assertEquals("/category/movie/", applied.clear().query.category)
        assertEquals(DiscoverFilters(), applied.clear().query.filters)
    }

    @Test fun invalidRangesCannotBecomeQueries() {
        val invalid = HomeFilterState().open().edit(DiscoverDraft(yearMin = "2026", yearMax = "2020"))
        assertEquals(invalid, invalid.apply())
    }

    @Test fun delayedCountIsRejectedEvenWhenSwitchingBackToSameQuery() {
        val first = HomeFilterState().open().edit(DiscoverDraft(scoreMin = "8")).apply()
        val returned = first.category("/category/movie/").category("/")
        assertEquals(first.query, returned.query)
        assertFalse(returned.acceptsCount(first.generation))
        assertTrue(returned.acceptsCount(returned.generation))
    }

    @Test fun coldSessionHasNoPersistedFilterOrDraft() {
        assertEquals(HomeQuery(), HomeFilterState().query)
        assertNull(HomeFilterState().draft)
    }
}
