package com.jing.ddys.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.jing.ddys.compose.DdysAppFrame
import com.jing.ddys.compose.screen.MainScreen
import com.jing.ddys.compose.theme.DdysTheme
import com.jing.ddys.update.UpdateViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Loads [MainFragment].
 */
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel by viewModel<MainViewModel>()
        val updateViewModel by viewModel<UpdateViewModel>()
        setContent {
            DdysTheme {
                DdysAppFrame {
                    MainScreen(viewModel = viewModel, updateViewModel = updateViewModel)
                }
            }
        }
    }
}
