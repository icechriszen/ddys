package com.jing.ddys.repository

object VideoDescriptionText {
    private val leadingLabel = Regex("^简[\\s　]*介\\s*[:：]\\s*")

    fun normalize(value: String): String {
        return value.trim().replace(leadingLabel, "")
    }
}
