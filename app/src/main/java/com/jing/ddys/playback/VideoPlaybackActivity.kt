package com.jing.ddys.playback

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.util.UnstableApi
import com.jing.ddys.compose.AppFormFactor
import com.jing.ddys.compose.appFormFactorFromUiMode
import com.jing.ddys.R
import com.jing.ddys.compose.theme.DdysTheme
import com.jing.ddys.repository.VideoDetailInfo
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

@UnstableApi
class VideoPlaybackActivity : FragmentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoDetail = intent.getSerializableExtra(VIDEO_KEY) as VideoDetailInfo
        val playEpisodeIndex = intent.getIntExtra(PLAY_INDEX, 0)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (appFormFactorFromUiMode(resources.configuration.uiMode) == AppFormFactor.Phone) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val viewModel by viewModel<PlaybackViewModel> {
                parametersOf(videoDetail, playEpisodeIndex)
            }
            setContent {
                DdysTheme {
                    PhonePlaybackScreen(
                        videoDetail = videoDetail,
                        viewModel = viewModel,
                        onExit = { finish() }
                    )
                }
            }
        } else {
            setContentView(R.layout.activity_playback)
            supportFragmentManager.beginTransaction()
                .replace(R.id.playback_fragment, VideoPlaybackFragment::class.java, intent.extras)
                .commit()
        }
    }

    companion object {
        const val VIDEO_KEY = "video"
        const val PLAY_INDEX = "idx"

        fun navigateTo(context: Context, videoDetailInfo: VideoDetailInfo, playEpisodeIndex: Int) {
            Intent(context, VideoPlaybackActivity::class.java).apply {
                putExtra(
                    VIDEO_KEY, videoDetailInfo.copy(
                        coverUrl = "",
                        seasons = emptyList(),
                        relatedVideo = emptyList(),
                        rating = "",
                        description = ""
                    )
                )
                putExtra(PLAY_INDEX, playEpisodeIndex)
                context.startActivity(this)
            }
        }
    }
}
