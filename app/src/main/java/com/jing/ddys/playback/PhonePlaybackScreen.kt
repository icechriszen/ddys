package com.jing.ddys.playback

import TrafficSpeedCalculatorBandwidthMeter
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.PlayerView
import com.jing.ddys.ext.secondsToDuration
import com.jing.ddys.compose.common.OperationModeButton
import com.jing.ddys.repository.Resource
import com.jing.ddys.repository.VideoDetailInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun PhonePlaybackScreen(
    videoDetail: VideoDetailInfo,
    viewModel: PlaybackViewModel,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val trafficSpeedCalculator = remember {
        TrafficSpeedCalculatorBandwidthMeter(DefaultBandwidthMeter.getSingletonInstance(context))
    }
    val dataSourceFactory = remember { createPlaybackDataSourceFactory(context) }
    val mediaSourceFactory = remember { createPlaybackMediaSourceFactory(context) }
    val player = remember {
        ExoPlayer.Builder(context)
            .setBandwidthMeter(trafficSpeedCalculator)
            .setLoadControl(createPlaybackLoadControl())
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredTextLanguage("zh")
                    .build()
                playWhenReady = true
            }
    }

    val videoUrlState by viewModel.videoUrl.collectAsState()
    val episodeName by viewModel.episodeName.collectAsState()
    val videoIndex by viewModel.videoIndex.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }
    var showEpisodeDialog by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf("Loading") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var speedText by remember { mutableStateOf("") }
    var backPressed by remember { mutableStateOf(false) }
    var loadedEpisodeIndex by remember { mutableStateOf<Int?>(null) }
    var capturedOnPause by remember { mutableStateOf(false) }
    val topControlsFocusRequester = remember { FocusRequester() }
    val showEpisodeChooser = PlaybackEpisodeControls.shouldShowEpisodeChooser(videoDetail.episodes.size)

    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == ExoPlayer.STATE_ENDED) {
                    viewModel.playNextEpisodeIfExists()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && loadedEpisodeIndex == viewModel.videoIndex.value) {
                    viewModel.startSaveHistory()
                } else {
                    if (loadedEpisodeIndex == viewModel.videoIndex.value) viewModel.saveHistory()
                    viewModel.stopSaveHistory()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val message = "播放失败:${error.cause?.message ?: error.message}"
                errorText = message
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) capturedOnPause = false
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.capturePlaybackUiState(player, loadedEpisodeIndex)
                capturedOnPause = true
                player.pause()
                viewModel.stopSaveHistory()
            }
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            if (!capturedOnPause) viewModel.capturePlaybackUiState(player, loadedEpisodeIndex)
            viewModel.stopSaveHistory()
            player.removeListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            if (loadedEpisodeIndex == viewModel.videoIndex.value) {
                viewModel.currentPlayPosition = player.currentPosition
                viewModel.videoDuration = player.duration.coerceAtLeast(0)
            }
            speedText = "${trafficSpeedCalculator.getNetworkSpeed()} kb/s"
            delay(800L)
        }
    }

    LaunchedEffect(videoUrlState) {
        when (val resource = videoUrlState) {
            Resource.Loading -> {
                errorText = null
                loadingText = "Loading"
            }

            is Resource.Error -> {
                errorText = resource.message
                Toast.makeText(context, resource.message, Toast.LENGTH_LONG).show()
            }

            is Resource.Success -> {
                errorText = null
                val history = resource.data
                if (history.episodeIndex != viewModel.videoIndex.value) return@LaunchedEffect
                val uiState = viewModel.consumePlaybackUiState(history.episodeIndex)
                loadedEpisodeIndex = history.episodeIndex
                val resumePosition = playbackStartPosition(uiState, history.lastPlayPosition, history.videoDuration)
                viewModel.currentPlayPosition = resumePosition
                viewModel.videoDuration = history.videoDuration.coerceAtLeast(0)
                val videoMediaSource = mediaSourceFactory.createMediaSource(
                    MediaItem.Builder().setUri(history.url.url).build()
                )
                if (history.url.subtitleUrl != null) {
                    val subtitleConfiguration =
                        MediaItem.SubtitleConfiguration.Builder(history.url.subtitleUrl)
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setSelectionFlags(C.SELECTION_FLAG_FORCED)
                            .setLanguage("zh")
                            .build()
                    val subtitleMediaSource =
                        SingleSampleMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(subtitleConfiguration, C.TIME_UNSET)
                    player.setMediaSource(MergingMediaSource(videoMediaSource, subtitleMediaSource))
                } else {
                    player.setMediaSource(videoMediaSource)
                }
                player.restorePlaybackUiState(uiState, history)
                player.prepare()
                if (uiState == null && resumePosition > 0) {
                    Toast.makeText(
                        context,
                        "已定位到上次播放位置:${(resumePosition / 1000).secondsToDuration()}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (uiState == null && history.videoDuration > 0 && history.videoDuration - history.lastPlayPosition < 10_000) {
                    Toast.makeText(context, "上次已播放完,将从头开始播放", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    BackHandler {
        if (player.isPlaying && !backPressed) {
            backPressed = true
            Toast.makeText(context, "再按一次退出播放", Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                delay(2000)
                backPressed = false
            }
        } else {
            onExit()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = {
                object : PlayerView(it) {
                    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                        if (PlaybackRemoteFocus.shouldMoveFocusToTopControls(
                                keyCode = event.keyCode,
                                action = event.action,
                                controlsVisible = controlsVisible
                            )
                        ) {
                            return runCatching {
                                topControlsFocusRequester.requestFocus()
                                true
                            }.getOrDefault(false)
                        }
                        return super.dispatchKeyEvent(event)
                    }
                }.apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    useController = true
                    controllerAutoShow = true
                    controllerHideOnTouch = true
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        controlsVisible = visibility == View.VISIBLE
                    })
                    this.player = player
                }
            },
            update = { playerView ->
                playerView.player = player
            },
            modifier = Modifier.fillMaxSize()
        )

        if (controlsVisible) {
            PhonePlaybackTopControls(
                title = videoDetail.title,
                episodeName = episodeName,
                hasNext = videoIndex < videoDetail.episodes.size - 1,
                showEpisodeChooser = showEpisodeChooser,
                onExit = onExit,
                onReplay = {
                    player.seekTo(0L)
                    player.play()
                },
                firstActionFocusRequester = topControlsFocusRequester,
                onNext = {
                    player.pause()
                    viewModel.playNextEpisodeIfExists()
                },
                onChooseEpisode = { showEpisodeDialog = true }
            )
        }

        if (videoUrlState is Resource.Loading) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator()
                Text(text = loadingText, color = Color.White)
                Text(text = speedText, color = Color.White)
            }
        }

        errorText?.let {
            Text(
                text = it,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        }
    }

    if (showEpisodeDialog) {
        AlertDialog(
            onDismissRequest = { showEpisodeDialog = false },
            title = { Text(text = "选集") },
            confirmButton = {
                TextButton(onClick = { showEpisodeDialog = false }) {
                    Text(text = "关闭")
                }
            },
            text = {
                LazyColumn {
                    itemsIndexed(videoDetail.episodes, key = { _, episode -> episode.id }) { index, episode ->
                        Button(
                            onClick = {
                                showEpisodeDialog = false
                                player.pause()
                                viewModel.changePlayVideoIndex(index)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(text = episode.displayName)
                        }
                    }
                }
            }
        )
    }


}

@Composable
private fun PhonePlaybackTopControls(
    title: String,
    episodeName: String,
    hasNext: Boolean,
    showEpisodeChooser: Boolean,
    firstActionFocusRequester: FocusRequester,
    onExit: () -> Unit,
    onReplay: () -> Unit,
    onNext: () -> Unit,
    onChooseEpisode: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = episodeName,
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showEpisodeChooser) {
            IconButton(
                onClick = onChooseEpisode,
                modifier = Modifier.focusRequester(firstActionFocusRequester)
            ) {
                Icon(Icons.Default.PlaylistPlay, contentDescription = "episodes", tint = Color.White)
            }
        }
        IconButton(
            onClick = onReplay,
            modifier = if (showEpisodeChooser) Modifier else Modifier.focusRequester(firstActionFocusRequester)
        ) {
            Icon(Icons.Default.Replay, contentDescription = "replay", tint = Color.White)
        }
        OperationModeButton(tint = Color.White)
        IconButton(onClick = onNext, enabled = hasNext) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "next",
                tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.36f)
            )
        }
        IconButton(onClick = onExit) {
            Icon(Icons.Default.Close, contentDescription = "close", tint = Color.White)
        }
    }
}
