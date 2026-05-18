package com.jing.ddys.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDescriptionTextTest {

    @Test
    fun removesLeadingDescriptionLabel() {
        assertEquals(
            "这是一段剧情简介。",
            VideoDescriptionText.normalize("简介: 这是一段剧情简介。")
        )
        assertEquals(
            "这是一段剧情简介。",
            VideoDescriptionText.normalize("简　　介：这是一段剧情简介。")
        )
    }

    @Test
    fun keepsDescriptionTextWithoutLabel() {
        assertEquals(
            "这是一段剧情简介。",
            VideoDescriptionText.normalize(" 这是一段剧情简介。 ")
        )
    }
}
