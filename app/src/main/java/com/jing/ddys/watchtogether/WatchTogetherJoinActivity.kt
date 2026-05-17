package com.jing.ddys.watchtogether

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.jing.ddys.compose.theme.DdysTheme
import com.jing.ddys.playback.VideoPlaybackActivity
import com.jing.ddys.repository.HttpUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class WatchTogetherJoinActivity : ComponentActivity() {
    private val client: WatchTogetherClient by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DdysTheme {
                var code by remember { mutableStateOf("") }
                var loading by remember { mutableStateOf(false) }
                var errorText by remember { mutableStateOf<String?>(null) }
                WatchTogetherJoinScreen(
                    roomCode = code,
                    loading = loading,
                    errorText = errorText,
                    onRoomCodeChange = {
                        code = it.filter(Char::isDigit).take(6)
                        errorText = null
                    },
                    onJoin = {
                        if (!WatchTogetherRoomCode.isValid(code)) {
                            errorText = "请输入 6 位数字房间码"
                            return@WatchTogetherJoinScreen
                        }
                        loading = true
                        errorText = null
                        lifecycleScope.launch {
                            runCatching {
                                val state = client.getRoom(code)
                                val detail = withContext(Dispatchers.IO) {
                                    HttpUtil.queryDetailPage(state.detailPageUrl)
                                }
                                val episodeIndex = state.episodeIndex
                                    .coerceIn(0, (detail.episodes.size - 1).coerceAtLeast(0))
                                VideoPlaybackActivity.navigateToWatchTogetherMember(
                                    context = this@WatchTogetherJoinActivity,
                                    videoDetailInfo = detail,
                                    playEpisodeIndex = episodeIndex,
                                    roomCode = code
                                )
                                finish()
                            }.onFailure {
                                val message = it.message ?: "加入一起看房间失败"
                                errorText = message
                                Toast.makeText(
                                    this@WatchTogetherJoinActivity,
                                    message,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            loading = false
                        }
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    companion object {
        fun navigateTo(context: Context) {
            context.startActivity(Intent(context, WatchTogetherJoinActivity::class.java))
        }
    }
}

@Composable
private fun WatchTogetherJoinScreen(
    roomCode: String,
    loading: Boolean,
    errorText: String?,
    onRoomCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "加入一起看", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(18.dp))
        OutlinedTextField(
            value = roomCode,
            onValueChange = onRoomCodeChange,
            label = { Text("6 位房间码") },
            singleLine = true,
            enabled = !loading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = errorText != null,
            modifier = Modifier.fillMaxWidth()
        )
        errorText?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel, enabled = !loading) {
                Text("取消")
            }
            Button(
                onClick = onJoin,
                enabled = !loading && WatchTogetherRoomCode.isValid(roomCode)
            ) {
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    Text("加入")
                }
            }
        }
    }
}
