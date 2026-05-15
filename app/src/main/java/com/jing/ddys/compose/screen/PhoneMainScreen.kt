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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.jing.ddys.R
import com.jing.ddys.compose.common.ErrorTip
import com.jing.ddys.compose.common.Loading
import com.jing.ddys.detail.DetailActivity
import com.jing.ddys.history.PlayHistoryActivity
import com.jing.ddys.main.MainViewModel
import com.jing.ddys.repository.SourceAuthRequiredException
import com.jing.ddys.search.SearchActivity
import com.jing.ddys.setting.SettingsActivity
import com.jing.ddys.setting.VideoSourceLoginActivity
import com.jing.ddys.update.UpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneMainScreen(viewModel: MainViewModel, updateViewModel: UpdateViewModel) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val updateState by updateViewModel.updateState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(selectedTabIndex) {
        delay(200L)
        viewModel.onCategoryChoose(categoryList[selectedTabIndex].first)
    }
    LaunchedEffect(Unit) {
        updateViewModel.checkDaily()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(id = R.string.app_name)) },
            actions = {
                IconButton(onClick = { SearchActivity.navigateTo(context) }) {
                    Icon(Icons.Default.Search, contentDescription = "search")
                }
                IconButton(onClick = { PlayHistoryActivity.navigateTo(context) }) {
                    Icon(Icons.Default.History, contentDescription = "history")
                }
                if (updateState.hasVisibleUpdate()) {
                    IconButton(onClick = { SettingsActivity.navigateTo(context) }) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = "update")
                    }
                }
                IconButton(onClick = { SettingsActivity.navigateTo(context) }) {
                    Icon(Icons.Default.Settings, contentDescription = "settings")
                }
            }
        )
        ScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 12.dp) {
            categoryList.forEachIndexed { index, item ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(text = item.second, maxLines = 1) }
                )
            }
        }
        PhoneVideoGrid(viewModel = viewModel)
    }
}

@Composable
private fun PhoneVideoGrid(viewModel: MainViewModel) {
    val pagingItems = viewModel.pager.collectAsLazyPagingItems()
    val context = LocalContext.current
    val sourceLoginLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                pagingItems.retry()
            }
        }

    if (pagingItems.loadState.refresh is LoadState.Loading) {
        Loading()
        return
    }
    if (pagingItems.loadState.refresh is LoadState.Error) {
        val error = (pagingItems.loadState.refresh as LoadState.Error).error
        val authRequired = error is SourceAuthRequiredException
        ErrorTip(
            message = "加载失败:${error.message}",
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

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            viewModel.refreshChannel.receiveAsFlow().collectLatest { pagingItems.refresh() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
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
