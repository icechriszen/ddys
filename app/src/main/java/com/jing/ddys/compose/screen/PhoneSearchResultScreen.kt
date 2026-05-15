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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.jing.ddys.R
import com.jing.ddys.compose.common.ErrorTip
import com.jing.ddys.compose.common.Loading
import com.jing.ddys.detail.DetailActivity
import com.jing.ddys.repository.SearchResult
import com.jing.ddys.repository.SourceAuthRequiredException
import com.jing.ddys.search.SearchResultViewModel
import com.jing.ddys.setting.VideoSourceLoginActivity

@Composable
fun PhoneSearchResultScreen(viewModel: SearchResultViewModel) {
    val pagingItems = viewModel.pager.collectAsLazyPagingItems()
    val refreshState = pagingItems.loadState.refresh
    val context = LocalContext.current
    val sourceLoginLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                pagingItems.retry()
            }
        }

    if (refreshState is LoadState.Loading) {
        Loading()
        return
    }
    if (refreshState is LoadState.Error) {
        val authRequired = refreshState.error is SourceAuthRequiredException
        ErrorTip(
            message = "加载错误:${refreshState.error.message}",
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

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(280.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.title_search_result),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey { it.url }
            ) { index ->
                val result = pagingItems[index] ?: return@items
                PhoneSearchResultCard(
                    searchResult = result,
                    onClick = { DetailActivity.navigateTo(context, result.url) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneSearchResultCard(searchResult: SearchResult, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = searchResult.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (searchResult.desc.isNotBlank()) {
                Text(
                    text = searchResult.desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (searchResult.updateTime.isNotBlank()) {
                Text(
                    text = "${searchResult.updateTime}更新",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
