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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.jing.ddys.repository.Resource
import com.jing.ddys.repository.VideoDetailInfo
import com.jing.ddys.watchtogether.WatchTogetherJoinActivity
import com.jing.ddys.watchtogether.WatchTogetherRole
import com.jing.ddys.watchtogether.WatchTogetherSession
import com.jing.ddys.watchtogether.WatchTogetherViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun PhonePlaybackScreen(
    videoDetail: VideoDetailInfo,
    viewModel: PlaybackViewModel,
    watchTogetherViewModel: WatchTogetherViewModel,
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
    val watchTogetherSession by watchTogetherViewModel.session.collectAsState()
    val watchTogetherError by watchTogetherViewModel.errorMessage.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }
    var showEpisodeDialog by remember { mutableStateOf(false) }
    var showWatchTogetherDialog by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf("Loading") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var speedText by remember { mutableStateOf("") }
    var backPressed by remember { mutableStateOf(false) }
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
                if (isPlaying) {
                    viewModel.startSaveHistory()
                } else {
                    viewModel.saveHistory()
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
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.resumePosition = player.currentPosition
                player.pause()
                viewModel.saveHistory()
            }
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.resumePosition = player.currentPosition
            viewModel.saveHistory()
            viewModel.stopSaveHistory()
            player.removeListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            viewModel.currentPlayPosition = player.currentPosition
            viewModel.videoDuration = player.duration
            speedText = "${trafficSpeedCalculator.getNetworkSpeed()} kb/s"
            delay(800L)
        }
    }

    LaunchedEffect(watchTogetherSession, player, videoIndex) {
        val session = watchTogetherSession ?: return@LaunchedEffect
        while (isActive) {
            when (session.role) {
                WatchTogetherRole.Host -> {
                    watchTogetherViewModel.publishHostState(
                        videoDetail = videoDetail,
                        episodeIndex = videoIndex,
                        positionMs = player.currentPosition,
                        durationMs = player.duration.coerceAtLeast(0L),
                        playbackRate = player.playbackParameters.speed,
                        paused = !player.isPlaying
                    )
                }

                WatchTogetherRole.Member -> {
                    val state = watchTogetherViewModel.refreshRoomState(session.roomCode)
                    if (state != null) {
                        if (state.episodeIndex != videoIndex) {
                            player.pause()
                            viewModel.changePlayVideoIndex(state.episodeIndex)
                        } else {
                            val targetPosition = state.estimatedPositionAt(System.currentTimeMillis())
                            val thresholdMs = if (state.paused) 200L else 1000L
                            if (kotlin.math.abs(player.currentPosition - targetPosition) > thresholdMs) {
                                player.seekTo(targetPosition)
                            }
                            if (state.paused && player.isPlaying) {
                                player.pause()
                            } else if (!state.paused && !player.isPlaying) {
                                player.play()
                            }
                        }
                    }
                }
            }
            delay(1000L)
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
                player.prepare()
                val resumePosition = when {
                    viewModel.resumePosition > 0 -> viewModel.resumePosition.also {
                        viewModel.resumePosition = 0
                    }

                    history.lastPlayPosition > 0 &&
                        !(history.videoDuration > 0 && history.videoDuration - history.lastPlayPosition < 10_000) ->
                        history.lastPlayPosition

                    else -> 0L
                }
                if (resumePosition > 0) {
                    player.seekTo(resumePosition)
                    Toast.makeText(
                        context,
                        "已定位到上次播放位置:${(resumePosition / 1000).secondsToDuration()}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (history.videoDuration > 0 && history.videoDuration - history.lastPlayPosition < 10_000) {
                    Toast.makeText(context, "上次已播放完,将从头开始播放", Toast.LENGTH_SHORT).show()
                }
                player.play()
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
                onChooseEpisode = { showEpisodeDialog = true },
                onWatchTogether = { showWatchTogetherDialog = true }
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

    if (showWatchTogetherDialog) {
        WatchTogetherDialog(
            session = watchTogetherSession,
            errorText = watchTogetherError,
            onDismiss = { showWatchTogetherDialog = false },
            onCreateRoom = {
                coroutineScope.launch {
                    runCatching {
                        watchTogetherViewModel.createRoom(
                            videoDetail = videoDetail,
                            episodeIndex = videoIndex,
                            positionMs = player.currentPosition,
                            durationMs = player.duration.coerceAtLeast(0L),
                            playbackRate = player.playbackParameters.speed,
                            paused = !player.isPlaying
                        )
                    }.onFailure {
                        Toast.makeText(
                            context,
                            it.message ?: "创建一起看房间失败",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            onJoinRoom = {
                WatchTogetherJoinActivity.navigateTo(context)
                showWatchTogetherDialog = false
            },
            onLeaveRoom = {
                watchTogetherViewModel.leaveRoom()
                showWatchTogetherDialog = false
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
    onChooseEpisode: () -> Unit,
    onWatchTogether: () -> Unit
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
            onClick = onWatchTogether,
            modifier = if (showEpisodeChooser) Modifier else Modifier.focusRequester(firstActionFocusRequester)
        ) {
            Icon(Icons.Default.Groups, contentDescription = "watch together", tint = Color.White)
        }
        IconButton(onClick = onReplay) {
            Icon(Icons.Default.Replay, contentDescription = "replay", tint = Color.White)
        }
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

@Composable
private fun WatchTogetherDialog(
    session: WatchTogetherSession?,
    errorText: String?,
    onDismiss: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onLeaveRoom: () -> Unit
) {
    if (session != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                WatchTogetherDialogTitle(text = "一起看房间")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WatchTogetherStatusRow(label = "房间码", value = session.roomCode)
                            WatchTogetherStatusRow(label = "成员", value = "${session.memberCount}")
                        }
                    }
                    errorText?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text("关闭")
                }
            },
            dismissButton = {
                TextButton(onClick = onLeaveRoom) {
                    Text("退出房间")
                }
            }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            WatchTogetherDialogTitle(text = "一起看")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "同步当前影片、集数和播放进度",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                errorText?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onCreateRoom) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("创建房间")
            }
        },
        dismissButton = {
            TextButton(onClick = onJoinRoom) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("加入房间")
            }
        }
    )
}

@Composable
private fun WatchTogetherDialogTitle(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Groups, contentDescription = null)
        Text(text = text)
    }
}

@Composable
private fun WatchTogetherStatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}
