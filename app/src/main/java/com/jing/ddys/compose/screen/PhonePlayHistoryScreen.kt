package com.jing.ddys.compose.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.jing.ddys.history.PlayHistoryViewModel
import com.jing.ddys.repository.HttpUtil
import com.jing.ddys.repository.VideoCardInfo
import com.jing.ddys.room.VideoEpisodeHistory
import kotlinx.coroutines.launch

@Composable
fun PhonePlayHistoryScreen(viewModel: PlayHistoryViewModel) {
    val pagingItems = viewModel.pager.collectAsLazyPagingItems()
    val refreshState = pagingItems.loadState.refresh
    if (refreshState is LoadState.Loading) {
        Loading()
        return
    }
    if (refreshState is LoadState.Error) {
        ErrorTip(message = "加载错误:${refreshState.error.message}") {
            pagingItems.refresh()
        }
        return
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var confirmRemoveVideo by remember { mutableStateOf<VideoEpisodeHistory?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.playback_history),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(
                    count = pagingItems.itemCount,
                    key = pagingItems.itemKey { it.videoId to it.epId }
                ) { index ->
                    val video = pagingItems[index] ?: return@items
                    PhoneVideoPosterCard(
                        video = VideoCardInfo(
                            title = video.title,
                            imageUrl = video.pic,
                            subTitle = video.epName,
                            url = ""
                        ),
                        width = 132.dp,
                        onLongClick = { confirmRemoveVideo = video },
                        onClick = {
                            DetailActivity.navigateTo(
                                context,
                                "${HttpUtil.BASE_URL}${video.videoId}"
                            )
                        }
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
        }

        if (pagingItems.itemCount == 0) {
            Text(
                text = stringResource(id = R.string.grid_no_data_tip),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    val removeVideo = confirmRemoveVideo ?: return
    PhoneDeleteDialog(
        text = String.format(stringResource(id = R.string.confirm_delete_template), removeVideo.title),
        onDeleteClick = {
            confirmRemoveVideo = null
            coroutineScope.launch {
                viewModel.deleteHistoryByVideoId(removeVideo.videoId)
                pagingItems.refresh()
            }
        },
        onDeleteAllClick = {
            confirmRemoveVideo = null
            coroutineScope.launch {
                viewModel.deleteAllHistory()
                pagingItems.refresh()
            }
        },
        onCancel = { confirmRemoveVideo = null }
    )
}
