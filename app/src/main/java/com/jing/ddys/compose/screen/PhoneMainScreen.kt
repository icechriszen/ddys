package com.jing.ddys.compose.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.jing.ddys.R
import com.jing.ddys.compose.common.ErrorTip
import com.jing.ddys.compose.common.Loading
import com.jing.ddys.compose.common.OperationModeButton
import com.jing.ddys.detail.DetailActivity
import com.jing.ddys.history.PlayHistoryActivity
import com.jing.ddys.main.MainViewModel
import com.jing.ddys.repository.SourceAuthRequiredException
import com.jing.ddys.search.SearchActivity
import com.jing.ddys.setting.SettingsActivity
import com.jing.ddys.setting.VideoSourceLoginActivity
import com.jing.ddys.update.UpdateViewModel
import com.jing.ddys.watchtogether.WatchTogetherJoinActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneMainScreen(
    viewModel: MainViewModel,
    updateViewModel: UpdateViewModel,
    filterFocusRequester: FocusRequester,
    selectedTabIndex: Int,
    onSelectCategory: (Int) -> Unit
) {
    var showMoreActions by remember { mutableStateOf(false) }
    val updateState by updateViewModel.updateState.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(id = R.string.app_name)) },
            actions = {
                IconButton(onClick = { SearchActivity.navigateTo(context) }) {
                    Icon(Icons.Default.Search, contentDescription = "search")
                }
                OperationModeButton()
                IconButton(onClick = { SettingsActivity.navigateTo(context) }) {
                    Icon(Icons.Default.Settings, contentDescription = "settings")
                }
                Box {
                    IconButton(onClick = { showMoreActions = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions))
                    }
                    DropdownMenu(expanded = showMoreActions, onDismissRequest = { showMoreActions = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.playback_history)) }, onClick = {
                            showMoreActions = false
                            PlayHistoryActivity.navigateTo(context)
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.watch_together_title)) }, onClick = {
                            showMoreActions = false
                            WatchTogetherJoinActivity.navigateTo(context)
                        })
                        if (updateState.hasVisibleUpdate()) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.software_update_title)) }, onClick = {
                                showMoreActions = false
                                SettingsActivity.navigateTo(context)
                            })
                        }
                    }
                }
            }
        )
        ScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 12.dp) {
            categoryList.forEachIndexed { index, item ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { onSelectCategory(index) },
                    text = { Text(text = item.second, maxLines = 1) }
                )
            }
        }
        HomeFilterBar(viewModel, filterFocusRequester)
        val filterState by viewModel.state.collectAsState()
        key(filterState.generation) { PhoneVideoGrid(viewModel = viewModel) }
    }
}

@Composable
private fun PhoneVideoGrid(viewModel: MainViewModel) {
    val pagingItems = viewModel.pager.collectAsLazyPagingItems()
    val filterState by viewModel.state.collectAsState()
    val context = LocalContext.current
    val sourceLoginLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                pagingItems.retry()
            }
        }

    if (pagingItems.loadState.refresh is LoadState.Loading && pagingItems.itemCount == 0) {
        Loading()
        return
    }
    if (pagingItems.loadState.refresh is LoadState.Error && pagingItems.itemCount == 0) {
        val error = (pagingItems.loadState.refresh as LoadState.Error).error
        val authRequired = error is SourceAuthRequiredException
        ErrorTip(
            message = if (filterState.query.filters.isActive) discoverErrorMessage(error) else "加载失败:${error.message}",
            primaryActionText = if (authRequired) stringResource(R.string.video_source_login_title) else null,
            primaryAction = if (authRequired) {
                {
                    sourceLoginLauncher.launch(
                        Intent(context, VideoSourceLoginActivity::class.java)
                    )
                }
            } else {
                null
            }
        ) {
            pagingItems.retry()
        }
        return
    }

    val gridState = rememberLazyGridState(viewModel.phoneGridIndex, viewModel.phoneGridOffset)
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> viewModel.phoneGridIndex = index; viewModel.phoneGridOffset = offset }
    }
    if (pagingItems.itemCount == 0 && pagingItems.loadState.refresh is LoadState.NotLoading && filterState.query.filters.isActive) {
        DiscoverEmptyState(viewModel)
        return
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(132.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey { it.url }
            ) { index ->
                val video = pagingItems[index] ?: return@items
                PhoneVideoPosterCard(
                    video = video,
                    width = 132.dp,
                    onClick = { DetailActivity.navigateTo(context, video.url) }
                )
            }
            if (pagingItems.loadState.append is LoadState.Loading) {
                item {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            if (pagingItems.loadState.append is LoadState.Error) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(16.dp)) {
                        val error = (pagingItems.loadState.append as LoadState.Error).error
                        Text(discoverErrorMessage(error))
                        if (error is SourceAuthRequiredException) {
                            FilterAction(stringResource(R.string.video_source_login_title), {
                                sourceLoginLauncher.launch(Intent(context, VideoSourceLoginActivity::class.java))
                            })
                        }
                        FilterAction(stringResource(R.string.button_retry), { pagingItems.retry() })
                    }
                }
            }
        }

        if (pagingItems.itemCount == 0) {
            Text(
                text = stringResource(id = R.string.grid_no_data_tip),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
