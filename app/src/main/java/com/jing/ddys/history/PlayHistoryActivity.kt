package com.jing.ddys.history

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.jing.ddys.compose.DdysAppFrame
import com.jing.ddys.compose.screen.PlayHistoryScreen
import com.jing.ddys.compose.theme.DdysTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class PlayHistoryActivity : ComponentActivity() {

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel by viewModel<PlayHistoryViewModel>()
        setContent {
            DdysTheme {
                DdysAppFrame {
                    PlayHistoryScreen(viewModel = viewModel)
                }
            }
        }
    }

    companion object {
        fun navigateTo(context: Context) {
            Intent(context, PlayHistoryActivity::class.java).apply {
                context.startActivity(this)
            }
        }
    }
}
