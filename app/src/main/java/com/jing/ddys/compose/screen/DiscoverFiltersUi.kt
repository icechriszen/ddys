package com.jing.ddys.compose.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.IconButton as TvIconButton
import androidx.tv.material3.IconButtonDefaults as TvIconButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.jing.ddys.R
import com.jing.ddys.compose.AppFormFactor
import com.jing.ddys.compose.rememberAppFormFactor
import com.jing.ddys.main.MainViewModel
import com.jing.ddys.repository.*
import com.jing.ddys.setting.VideoSourceLoginActivity
import java.util.Calendar

@Composable
internal fun HomeFilterPanelHost(viewModel: MainViewModel, focusRequester: FocusRequester) {
    val state by viewModel.state.collectAsState()
    val open = state.draft != null
    val tv = rememberAppFormFactor() == AppFormFactor.Tv
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open, tv) {
        if (!open && wasOpen && tv) focusRequester.requestFocus()
        wasOpen = open
    }
    if (open) DiscoverFilterPanel(viewModel, onClosed = {})
}

@Composable
@OptIn(ExperimentalTvMaterial3Api::class)
internal fun HomeFilterButton(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val active = state.query.filters.isActive
    val icon: @Composable () -> Unit = {
        Box(Modifier.size(24.dp)) {
            Icon(Icons.Default.Tune, stringResource(R.string.discover_filter), Modifier.fillMaxSize())
            if (active) Box(Modifier.align(Alignment.TopEnd).size(6.dp)
                .background(FilterPalette.primary, CircleShape))
        }
    }
    if (rememberAppFormFactor() == AppFormFactor.Tv) {
        TvIconButton(onClick = viewModel::openFilters, modifier = modifier,
            border = TvIconButtonDefaults.border(focusedBorder = Border(BorderStroke(2.dp, FilterPalette.focus))),
        ) { CompositionLocalProvider(LocalContentColor provides androidx.tv.material3.LocalContentColor.current) { icon() } }
    } else {
        IconButton(onClick = viewModel::openFilters, modifier = modifier, content = icon)
    }
}

@Composable
internal fun HomeFilterSummary(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val count by viewModel.count.collectAsState()
    if (!state.query.filters.isActive) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(filterSummary(state.query.filters), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            FilterAction(stringResource(R.string.discover_clear), viewModel::clearFilters)
        }
        count?.takeIf { it.query == state.query && state.query.filters.isActive }?.let {
            Text(stringResource(R.string.discover_count, it.value), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun filterSummary(filters: DiscoverFilters, includeDefaultSort: Boolean = false): String {
    if (!filters.isActive) return if (includeDefaultSort) {
        stringResource(R.string.discover_any) + " · " + stringResource(R.string.discover_sort_modified)
    } else stringResource(R.string.discover_unfiltered)
    val pieces = mutableListOf<String>()
    filters.region?.let { pieces += it }
    if (filters.genres.isNotEmpty()) pieces += filters.genres.sorted().joinToString("＋")
    if (filters.yearMin != null || filters.yearMax != null) pieces += stringResource(
        R.string.discover_year_summary, filters.yearMin?.toString() ?: "…", filters.yearMax?.toString() ?: "…")
    if (filters.scoreMin != null && filters.scoreMax == null) {
        pieces += stringResource(R.string.discover_score_min_summary, formatDiscoverScore(filters.scoreMin))
    } else if (filters.scoreMin != null || filters.scoreMax != null) {
        pieces += stringResource(R.string.discover_score_summary, formatDiscoverScore(filters.scoreMin ?: 0.0),
            formatDiscoverScore(filters.scoreMax ?: 10.0))
    }
    if (includeDefaultSort || filters.sort != DiscoverSort.Modified) pieces += stringResource(sortLabel(filters.sort))
    return pieces.joinToString(" · ")
}

private fun sortLabel(sort: DiscoverSort): Int = when (sort) {
    DiscoverSort.Modified -> R.string.discover_sort_modified
    DiscoverSort.Published -> R.string.discover_sort_published
    DiscoverSort.Year -> R.string.discover_sort_year
    DiscoverSort.Score -> R.string.discover_sort_score
}

// Panel colors are intentionally local: cyan means remote focus, purple means selected.
private object FilterPalette {
    val surface = Color(0xFF22222C)
    val control = Color(0xFF30303D)
    val selected = Color(0xFF5C3BB1)
    val primary = Color(0xFF7651D9)
    val text = Color(0xFFF5F2FA)
    val muted = Color(0xFFC3BDCF)
    val divider = Color(0xFF3B3847)
    val focus = Color(0xFF22D3EE)
}

@Composable
internal fun DiscoverFilterPanel(viewModel: MainViewModel, onClosed: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val optionState by viewModel.optionsState.collectAsState()
    val draft = state.draft ?: return
    val tv = rememberAppFormFactor() == AppFormFactor.Tv
    val initialFocus = remember { FocusRequester() }
    val context = LocalContext.current
    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.loadOptions()
    }
    val dismiss = { viewModel.cancelFilters(); onClosed() }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { dismiss() } })
            val panelWidth = minOf(maxWidth, maxOf(440.dp, maxWidth * 0.44f))
            Surface(
                modifier = if (tv) Modifier.align(Alignment.CenterEnd).width(panelWidth).fillMaxHeight()
                    else Modifier.align(Alignment.BottomCenter).widthIn(max = 600.dp).fillMaxWidth().fillMaxHeight(0.96f),
                shape = if (tv) RoundedCornerShape(0.dp)
                    else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = FilterPalette.surface,
                contentColor = FilterPalette.text
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (!tv) Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(36.dp, 4.dp).background(FilterPalette.muted.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.discover_filter), style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.discover_category_context,
                                categoryList.firstOrNull { it.first == state.query.category }?.second.orEmpty()),
                                style = MaterialTheme.typography.bodySmall, color = FilterPalette.muted)
                        }
                        FilterButton(dismiss, Modifier.size(48.dp).focusRequester(initialFocus), quiet = true) {
                            Icon(Icons.Default.Close, stringResource(R.string.discover_close), Modifier.size(22.dp))
                        }
                    }
                    // BoxWithConstraints subcomposes its children; request focus only once
                    // the close button has been composed inside that subtree.
                    LaunchedEffect(tv) { if (tv) initialFocus.requestFocus() }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            optionState.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = FilterPalette.primary)
                            optionState.error != null -> Column(Modifier.align(Alignment.Center).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(discoverErrorMessage(optionState.error!!))
                                if (optionState.error is SourceAuthRequiredException) {
                                    FilterAction(stringResource(R.string.video_source_login_title), {
                                        login.launch(Intent(context, VideoSourceLoginActivity::class.java))
                                    })
                                }
                                FilterAction(stringResource(R.string.button_retry), viewModel::loadOptions)
                            }
                            optionState.options != null -> FilterFields(draft, optionState.options!!, viewModel::editFilters)
                        }
                    }
                    Divider(color = FilterPalette.divider)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (draft.isValid) stringResource(R.string.discover_selected_summary,
                                filterSummary(draft.toFilters(), includeDefaultSort = true))
                            else stringResource(R.string.discover_fix_range),
                            color = if (draft.isValid) FilterPalette.muted else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterAction(stringResource(R.string.discover_reset), viewModel::resetDraft, Modifier.weight(1f))
                            FilterAction(stringResource(R.string.discover_apply), {
                                viewModel.applyFilters()
                                if (viewModel.state.value.draft == null) onClosed()
                            }, Modifier.weight(2f), primary = true,
                                enabled = draft.isValid && optionState.options != null && optionState.error == null && !optionState.loading)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterFields(draft: DiscoverDraft, options: DiscoverOptions, edit: (DiscoverDraft) -> Unit) {
    val year = remember { Calendar.getInstance().get(Calendar.YEAR) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        FilterSection(R.string.discover_region) {
            ExpandableChoices(options.regions, listOfNotNull(draft.region).toSet(),
                listOf("中国大陆", "中国香港", "中国台湾", "美国", "日本", "韩国"),
                onAny = { edit(draft.copy(region = null)) },
                onSelect = { edit(draft.copy(region = it)) })
        }
        FilterSection(R.string.discover_genres, stringResource(R.string.discover_genres_hint)) {
            ExpandableChoices(options.genres, draft.genres,
                listOf("科幻", "悬疑", "犯罪", "喜剧", "动作", "惊悚"),
                onAny = { edit(draft.copy(genres = emptySet())) },
                onSelect = { label -> edit(draft.copy(genres = if (label in draft.genres) draft.genres - label else draft.genres + label)) })
        }
        FilterSection(R.string.discover_year) {
            ChoiceGrid(listOf(
                FilterOption(stringResource(R.string.discover_any), !draft.customYear && draft.yearMin.isBlank() && draft.yearMax.isBlank()) {
                    edit(draft.copy(yearMin = "", yearMax = "", customYear = false))
                },
                FilterOption(stringResource(R.string.discover_this_year), !draft.customYear && draft.yearMin == "$year" && draft.yearMax == "$year") {
                    edit(draft.copy(yearMin = "$year", yearMax = "$year", customYear = false))
                },
                FilterOption(stringResource(R.string.discover_last_five), !draft.customYear && draft.yearMin == "${year - 4}" && draft.yearMax == "$year") {
                    edit(draft.copy(yearMin = "${year - 4}", yearMax = "$year", customYear = false))
                },
                FilterOption(stringResource(R.string.discover_custom), draft.customYear) { edit(draft.copy(customYear = true)) }
            ), columns = 4)
            if (draft.customYear) RangeFields(draft.yearMin, draft.yearMax,
                R.string.discover_year_min, R.string.discover_year_max, KeyboardType.Number, draft.yearError,
                { edit(draft.copy(yearMin = it)) }, { edit(draft.copy(yearMax = it)) })
        }
        FilterSection(R.string.discover_score) {
            ChoiceGrid(listOf(FilterOption(stringResource(R.string.discover_any),
                !draft.customScore && draft.scoreMin.isBlank() && draft.scoreMax.isBlank()) {
                edit(draft.copy(scoreMin = "", scoreMax = "", customScore = false))
            }) + (6..9).map { minimum ->
                FilterOption(stringResource(R.string.discover_score_above, minimum),
                    !draft.customScore && draft.scoreMin == "$minimum" && draft.scoreMax.isBlank()) {
                    edit(draft.copy(scoreMin = "$minimum", scoreMax = "", customScore = false))
                }
            } + FilterOption(stringResource(R.string.discover_custom), draft.customScore) {
                edit(draft.copy(customScore = true))
            }, columns = 3)
            if (draft.customScore) RangeFields(draft.scoreMin, draft.scoreMax,
                R.string.discover_score_min, R.string.discover_score_max, KeyboardType.Decimal, draft.scoreError,
                { edit(draft.copy(scoreMin = it)) }, { edit(draft.copy(scoreMax = it)) })
        }
        SortField(draft.sort) { edit(draft.copy(sort = it)) }
    }
}

@Composable
private fun ExpandableChoices(
    choices: List<DiscoverChoice>, selected: Set<String>, preferred: List<String>,
    onAny: () -> Unit, onSelect: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val compact = compactFilterChoices(choices, preferred)
    val hasMore = compact.size < choices.size
    // Keep this control in the same grid cell when expanding, so D-pad focus does not jump.
    val items = listOf(FilterOption(stringResource(R.string.discover_any), selected.isEmpty(), onClick = onAny)) +
        compact.map { choice -> FilterOption(choice.label, choice.label in selected) { onSelect(choice.label) } } +
        if (hasMore) listOf(FilterOption(stringResource(if (expanded) R.string.discover_less else R.string.discover_more),
            icon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            onClick = { expanded = !expanded })) else emptyList()
    ChoiceGrid(items, columns = 4)
    val extra = choices.filter { it !in compact && (expanded || it.label in selected) }
    if (extra.isNotEmpty()) {
        ChoiceGrid(extra.map { choice ->
            FilterOption(choice.label, choice.label in selected) { onSelect(choice.label) }
        }, columns = 4)
    }
}

private data class FilterOption(
    val label: String,
    val selected: Boolean = false,
    val icon: ImageVector? = null,
    val onClick: () -> Unit
)

@Composable
private fun ChoiceGrid(items: List<FilterOption>, columns: Int) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val count = if (maxWidth < 280.dp) minOf(columns, 3) else columns
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.chunked(count).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { item ->
                        FilterButton(item.onClick, Modifier.weight(1f).semantics { selected = item.selected },
                            selected = item.selected) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (item.selected) Icon(Icons.Default.Check, null, Modifier.size(14.dp))
                                Text(item.label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                item.icon?.let { Icon(it, null, Modifier.size(14.dp)) }
                            }
                        }
                    }
                    repeat(count - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FilterSection(label: Int, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val tv = rememberAppFormFactor() == AppFormFactor.Tv
    if (tv) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(60.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterHeading(label)
                hint?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = FilterPalette.muted) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterHeading(label)
                hint?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = FilterPalette.muted) }
            }
            content()
        }
    }
}

@Composable
private fun SortField(sort: DiscoverSort, onSelect: (DiscoverSort) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val summaryFocus = remember { FocusRequester() }
    val tv = rememberAppFormFactor() == AppFormFactor.Tv
    FilterSection(R.string.discover_sort) {
        FilterButton({ expanded = !expanded }, Modifier.fillMaxWidth().focusRequester(summaryFocus)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(sortLabel(sort)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(20.dp))
            }
        }
        if (expanded) {
            Column(Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DiscoverSort.values().forEach { value ->
                    FilterButton({
                        if (tv) summaryFocus.requestFocus()
                        expanded = false
                        onSelect(value)
                    }, Modifier.fillMaxWidth().semantics { selected = sort == value }, selected = sort == value) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(sortLabel(value)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (sort == value) Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeFields(min: String, max: String, minLabel: Int, maxLabel: Int, type: KeyboardType,
                        error: DiscoverFieldError?, onMin: (String) -> Unit, onMax: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = FilterPalette.focus, unfocusedBorderColor = FilterPalette.divider,
            focusedTextColor = FilterPalette.text, unfocusedTextColor = FilterPalette.text,
            focusedLabelColor = FilterPalette.muted, unfocusedLabelColor = FilterPalette.muted,
            cursorColor = FilterPalette.focus
        )
        OutlinedTextField(min, onMin, Modifier.weight(1f), label = { Text(stringResource(minLabel)) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type), isError = error != null,
            shape = RoundedCornerShape(10.dp), colors = colors)
        OutlinedTextField(max, onMax, Modifier.weight(1f), label = { Text(stringResource(maxLabel)) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type), isError = error != null,
            shape = RoundedCornerShape(10.dp), colors = colors)
    }
    if (error != null) Text(stringResource(when (error) {
        DiscoverFieldError.InvalidYear -> R.string.discover_invalid_year
        DiscoverFieldError.YearOrder -> R.string.discover_year_order
        DiscoverFieldError.InvalidScore -> R.string.discover_invalid_score
        DiscoverFieldError.ScoreOrder -> R.string.discover_score_order
    }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun FilterHeading(label: Int) = Text(stringResource(label), style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.SemiBold)

@Composable
internal fun FilterAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                          enabled: Boolean = true, selected: Boolean = false, primary: Boolean = false) {
    FilterButton(onClick, modifier, enabled, selected, primary) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                         selected: Boolean = false, primary: Boolean = false, quiet: Boolean = false,
                         content: @Composable RowScope.() -> Unit) {
    val tv = rememberAppFormFactor() == AppFormFactor.Tv
    val background = when {
        primary -> FilterPalette.primary
        selected -> FilterPalette.selected
        quiet -> Color.Transparent
        else -> FilterPalette.control
    }
    val shape = RoundedCornerShape(10.dp)
    val buttonModifier = modifier.heightIn(min = if (tv) 40.dp else 48.dp)
    if (tv) {
        TvButton(onClick, buttonModifier, enabled = enabled, scale = ButtonScale.None,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            shape = TvButtonDefaults.shape(shape = shape),
            border = TvButtonDefaults.border(focusedBorder = Border(BorderStroke(2.dp, FilterPalette.focus), shape = shape)),
            colors = TvButtonDefaults.colors(
                containerColor = background, contentColor = FilterPalette.text,
                focusedContainerColor = background, focusedContentColor = FilterPalette.text,
                disabledContainerColor = FilterPalette.control, disabledContentColor = FilterPalette.muted.copy(alpha = 0.45f)
            )) {
                CompositionLocalProvider(LocalContentColor provides
                    if (enabled) FilterPalette.text else FilterPalette.muted.copy(alpha = 0.45f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically, content = content)
                }
            }
    } else {
        Button(onClick, buttonModifier, enabled = enabled, shape = shape,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = background, contentColor = FilterPalette.text,
                disabledContainerColor = FilterPalette.control, disabledContentColor = FilterPalette.muted.copy(alpha = 0.45f)
            ), content = content)
    }
}

@Composable
internal fun discoverErrorMessage(error: Throwable): String = when (error) {
    is DiscoverCompatibilityException -> stringResource(R.string.discover_compatibility_error)
    is SourceAuthRequiredException -> stringResource(R.string.discover_auth_error)
    else -> stringResource(R.string.discover_network_error)
}

@Composable
internal fun DiscoverEmptyState(viewModel: MainViewModel) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.discover_empty))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterAction(stringResource(R.string.discover_modify), viewModel::openFilters)
            FilterAction(stringResource(R.string.discover_clear), viewModel::clearFilters)
        }
    }
}
