package com.jing.ddys.compose.screen

import com.jing.ddys.repository.DiscoverChoice

/** Common choices lead, but every choice and its ID still comes from the active source. */
internal fun compactFilterChoices(
    choices: List<DiscoverChoice>,
    preferredLabels: List<String>
): List<DiscoverChoice> {
    val byLabel = choices.associateBy { it.label }
    return (preferredLabels.mapNotNull(byLabel::get) + choices)
        .distinctBy { it.label }
        .take(6)
}
