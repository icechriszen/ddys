package com.jing.ddys.setting

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.jing.ddys.compose.DdysAppFrame
import com.jing.ddys.compose.theme.DdysTheme
import com.jing.ddys.update.UpdateViewModel
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.viewModel

class SettingsActivity:ComponentActivity() {

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = get<SettingsViewModel>()
        val updateViewModel by viewModel<UpdateViewModel>()
        setContent {
            DdysTheme {
                DdysAppFrame {
                    SettingsScreen(viewModel = viewModel, updateViewModel = updateViewModel)
                }
            }
        }
    }

    companion object {
        fun navigateTo(context: Context) {
            Intent(context, SettingsActivity::class.java).apply {
                context.startActivity(this)
            }
        }
    }
}
