package com.jing.ddys.compose.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.Text as TvText
import androidx.tv.material3.ButtonScale
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
internal fun HomeFilterBar(viewModel: MainViewModel, focusRequester: FocusRequester) {
    val state by viewModel.state.collectAsState()
    val count by viewModel.count.collectAsState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterAction(stringResource(R.string.discover_filter), viewModel::openFilters,
                Modifier.focusRequester(focusRequester))
            Text(filterSummary(state.query.filters), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            if (state.query.filters.isActive) {
                FilterAction(stringResource(R.string.discover_clear), viewModel::clearFilters)
            }
        }
        count?.takeIf { it.query == state.query && state.query.filters.isActive }?.let {
            Text(stringResource(R.string.discover_count, it.value), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun filterSummary(filters: DiscoverFilters): String {
    if (!filters.isActive) return stringResource(R.string.discover_unfiltered)
    val pieces = mutableListOf<String>()
    filters.region?.let { pieces += it }
    if (filters.genres.isNotEmpty()) pieces += filters.genres.sorted().joinToString("＋")
    if (filters.yearMin != null || filters.yearMax != null) pieces += stringResource(
        R.string.discover_year_summary, filters.yearMin?.toString() ?: "…", filters.yearMax?.toString() ?: "…")
    if (filters.scoreMin != null || filters.scoreMax != null) pieces += stringResource(
        R.string.discover_score_summary, formatDiscoverScore(filters.scoreMin ?: 0.0),
        formatDiscoverScore(filters.scoreMax ?: 10.0))
    if (filters.sort != DiscoverSort.Modified) pieces += stringResource(sortLabel(filters.sort))
    return pieces.joinToString(" · ")
}

private fun sortLabel(sort: DiscoverSort): Int = when (sort) {
    DiscoverSort.Modified -> R.string.discover_sort_modified
    DiscoverSort.Published -> R.string.discover_sort_published
    DiscoverSort.Year -> R.string.discover_sort_year
    DiscoverSort.Score -> R.string.discover_sort_score
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
        Box(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { dismiss() } })
            Surface(
                modifier = if (tv) Modifier.align(Alignment.CenterEnd).width(440.dp).fillMaxHeight()
                    else Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.92f),
                shape = if (tv) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.extraLarge,
                tonalElevation = 8.dp
            ) {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.discover_filter_title), Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge)
                        FilterAction(stringResource(R.string.discover_close), dismiss,
                            Modifier.focusRequester(initialFocus))
                    }
                    Text(stringResource(R.string.discover_category_context,
                        categoryList.firstOrNull { it.first == state.query.category }?.second.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            optionState.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                            optionState.error != null -> Column(Modifier.align(Alignment.Center),
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterAction(stringResource(R.string.discover_reset), viewModel::resetDraft)
                        Spacer(Modifier.weight(1f))
                        FilterAction(stringResource(R.string.discover_apply), {
                            viewModel.applyFilters()
                            if (viewModel.state.value.draft == null) onClosed()
                        }, enabled = draft.isValid && optionState.options != null &&
                            optionState.error == null && !optionState.loading)
                    }
                }
            }
        }
        LaunchedEffect(tv) { if (tv) initialFocus.requestFocus() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterFields(draft: DiscoverDraft, options: DiscoverOptions, edit: (DiscoverDraft) -> Unit) {
    val year = remember { Calendar.getInstance().get(Calendar.YEAR) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterHeading(R.string.discover_region)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChoice(stringResource(R.string.discover_any), draft.region == null) { edit(draft.copy(region = null)) }
            options.regions.forEach { choice ->
                FilterChoice(choice.label, draft.region == choice.label) { edit(draft.copy(region = choice.label)) }
            }
        }
        FilterHeading(R.string.discover_genres)
        Text(stringResource(R.string.discover_genres_hint), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChoice(stringResource(R.string.discover_any), draft.genres.isEmpty()) { edit(draft.copy(genres = emptySet())) }
            options.genres.forEach { choice ->
                FilterChoice(choice.label, choice.label in draft.genres) {
                    edit(draft.copy(genres = if (choice.label in draft.genres) draft.genres - choice.label else draft.genres + choice.label))
                }
            }
        }
        FilterHeading(R.string.discover_year)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChoice(stringResource(R.string.discover_any), !draft.customYear && draft.yearMin.isEmpty() && draft.yearMax.isEmpty()) {
                edit(draft.copy(yearMin = "", yearMax = "", customYear = false))
            }
            FilterChoice(stringResource(R.string.discover_this_year), !draft.customYear && draft.yearMin == "$year" && draft.yearMax == "$year") {
                edit(draft.copy(yearMin = "$year", yearMax = "$year", customYear = false))
            }
            FilterChoice(stringResource(R.string.discover_last_five), !draft.customYear && draft.yearMin == "${year - 4}" && draft.yearMax == "$year") {
                edit(draft.copy(yearMin = "${year - 4}", yearMax = "$year", customYear = false))
            }
            FilterChoice(stringResource(R.string.discover_custom), draft.customYear) { edit(draft.copy(customYear = true)) }
        }
        if (draft.customYear) {
            RangeFields(draft.yearMin, draft.yearMax, R.string.discover_year_min, R.string.discover_year_max, KeyboardType.Number,
                draft.yearError, { edit(draft.copy(yearMin = it)) }, { edit(draft.copy(yearMax = it)) })
        }
        FilterHeading(R.string.discover_score)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChoice(stringResource(R.string.discover_any), !draft.customScore && draft.scoreMin.isEmpty() && draft.scoreMax.isEmpty()) {
                edit(draft.copy(scoreMin = "", scoreMax = "", customScore = false))
            }
            (6..9).forEach { minimum ->
                FilterChoice(stringResource(R.string.discover_score_above, minimum), !draft.customScore && draft.scoreMin == "$minimum" && draft.scoreMax.isEmpty()) {
                    edit(draft.copy(scoreMin = "$minimum", scoreMax = "", customScore = false))
                }
            }
            FilterChoice(stringResource(R.string.discover_custom), draft.customScore) { edit(draft.copy(customScore = true)) }
        }
        if (draft.customScore) {
            RangeFields(draft.scoreMin, draft.scoreMax, R.string.discover_score_min, R.string.discover_score_max, KeyboardType.Decimal,
                draft.scoreError, { edit(draft.copy(scoreMin = it)) }, { edit(draft.copy(scoreMax = it)) })
        }
        FilterHeading(R.string.discover_sort)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DiscoverSort.values().forEach { sort ->
                FilterChoice(stringResource(sortLabel(sort)), draft.sort == sort) { edit(draft.copy(sort = sort)) }
            }
        }
    }
}

@Composable
private fun RangeFields(min: String, max: String, minLabel: Int, maxLabel: Int, type: KeyboardType,
                        error: DiscoverFieldError?, onMin: (String) -> Unit, onMax: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(min, onMin, Modifier.weight(1f), label = { Text(stringResource(minLabel)) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type), isError = error != null)
        OutlinedTextField(max, onMax, Modifier.weight(1f), label = { Text(stringResource(maxLabel)) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type), isError = error != null)
    }
    if (error != null) Text(stringResource(when (error) {
        DiscoverFieldError.InvalidYear -> R.string.discover_invalid_year
        DiscoverFieldError.YearOrder -> R.string.discover_year_order
        DiscoverFieldError.InvalidScore -> R.string.discover_invalid_score
        DiscoverFieldError.ScoreOrder -> R.string.discover_score_order
    }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun FilterHeading(label: Int) = Text(stringResource(label), style = MaterialTheme.typography.titleMedium)

@Composable
private fun FilterChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterAction(if (selected) "✓ $label" else label, onClick,
        Modifier.semantics { this.selected = selected }, selected = selected)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun FilterAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                          enabled: Boolean = true, selected: Boolean = false) {
    if (rememberAppFormFactor() == AppFormFactor.Tv) {
        TvButton(onClick, modifier, enabled = enabled, scale = ButtonScale.None,
            border = TvButtonDefaults.border(focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary))),
            colors = TvButtonDefaults.colors(
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )) { TvText(label) }
    } else {
        OutlinedButton(onClick, modifier, enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            )) { Text(label) }
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
