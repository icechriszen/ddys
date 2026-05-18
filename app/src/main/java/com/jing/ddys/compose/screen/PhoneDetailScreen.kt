package com.jing.ddys.compose.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.jing.ddys.R
import com.jing.ddys.compose.common.ErrorTip
import com.jing.ddys.compose.common.Loading
import com.jing.ddys.detail.DetailActivity
import com.jing.ddys.detail.DetailViewModel
import com.jing.ddys.ext.secondsToDuration
import com.jing.ddys.playback.VideoPlaybackActivity
import com.jing.ddys.repository.Resource
import com.jing.ddys.repository.SourceAuthRequiredException
import com.jing.ddys.repository.VideoCardInfo
import com.jing.ddys.repository.VideoDetailInfo
import com.jing.ddys.repository.VideoEpisode
import com.jing.ddys.room.entity.VideoHistory
import com.jing.ddys.setting.VideoSourceLoginActivity

@Composable
fun PhoneDetailScreen(viewModel: DetailViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val sourceLoginLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                viewModel.queryDetail()
            }
        }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.fetchHistory()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val detailResource = viewModel.detailFlow.collectAsState().value) {
        Resource.Loading -> Loading()
        is Resource.Error -> {
            val authRequired = detailResource.exception is SourceAuthRequiredException
            ErrorTip(
                message = detailResource.message,
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
                viewModel.queryDetail()
            }
        }

        is Resource.Success -> PhoneDetailContent(viewModel = viewModel, videoDetail = detailResource.data)
    }
}

@Composable
private fun PhoneDetailContent(viewModel: DetailViewModel, videoDetail: VideoDetailInfo) {
    val context = LocalContext.current
    var showDescription by remember { mutableStateOf(false) }
    val latestProgress by viewModel.latestProgress.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = videoDetail.coverUrl,
                    contentDescription = videoDetail.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 120.dp, height = 172.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = videoDetail.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (latestProgress is Resource.Success) {
                        val history = (latestProgress as Resource.Success).data
                        Text(
                            text = "上次播放到${history.name} ${(history.progress / 1000).secondsToDuration()}/${(history.duration / 1000).secondsToDuration()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    videoDetail.infoRows.take(5).forEach {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (videoDetail.description.isNotBlank()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.video_description), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = videoDetail.description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = { showDescription = true }) {
                            Text(text = stringResource(R.string.video_description))
                        }
                    }
                }
            }
        }

        if (videoDetail.seasons.isNotEmpty()) {
            item {
                PhoneHorizontalButtons(title = "选季") {
                    items(videoDetail.seasons, key = { it.seasonName }) { season ->
                        OutlinedButton(
                            enabled = !season.currentSeason,
                            onClick = {
                                season.seasonUrl?.let { DetailActivity.navigateTo(context, it) }
                            }
                        ) {
                            Text(text = VideoEpisode.formatSeasonName(season.seasonName))
                        }
                    }
                }
            }
        }

        if (videoDetail.episodes.isNotEmpty()) {
            videoDetail.episodes.toEpisodeGroups().forEach { episodeGroup ->
                item {
                    PhoneHorizontalButtons(title = episodeGroup.title) {
                        items(episodeGroup.episodes, key = { it.value.id }) { indexedEpisode ->
                            val episode = indexedEpisode.value
                            Button(onClick = {
                                viewModel.saveHistory(
                                    VideoHistory(
                                        id = videoDetail.id,
                                        title = videoDetail.title,
                                        pic = videoDetail.coverUrl
                                    )
                                )
                                VideoPlaybackActivity.navigateTo(context, videoDetail, indexedEpisode.index)
                            }) {
                                Text(text = episode.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        if (videoDetail.relatedVideo.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "相关视频", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(videoDetail.relatedVideo, key = { it.url }) { video: VideoCardInfo ->
                            PhoneVideoPosterCard(
                                video = video,
                                width = 112.dp,
                                onClick = { DetailActivity.navigateTo(context, video.url) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDescription) {
        AlertDialog(
            onDismissRequest = { showDescription = false },
            confirmButton = {
                TextButton(onClick = { showDescription = false }) {
                    Text(text = "关闭")
                }
            },
            title = { Text(text = stringResource(R.string.video_description)) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text = videoDetail.description)
                }
            }
        )
    }
}

@Composable
private fun PhoneHorizontalButtons(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (title.isNotBlank()) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
