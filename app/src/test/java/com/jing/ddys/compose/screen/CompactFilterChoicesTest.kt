package com.jing.ddys.compose.screen

import com.jing.ddys.repository.DiscoverChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class CompactFilterChoicesTest {
    @Test
    fun usesOnlyAvailableChoicesAndKeepsSourceIds() {
        val choices = listOf(DiscoverChoice("41", "英国"), DiscoverChoice("18", "美国"))
        assertEquals(listOf(choices[1], choices[0]),
            compactFilterChoices(choices, listOf("中国大陆", "美国", "日本")))
    }

    @Test
    fun fillsRemainingSlotsInWebsiteOrderWithoutDuplicates() {
        val choices = (1..9).map { DiscoverChoice("$it", "地区$it") }
        assertEquals(listOf(choices[4], choices[2], choices[0], choices[1], choices[3], choices[5]),
            compactFilterChoices(choices, listOf("地区5", "地区3", "地区5")))
    }
}
