package com.jing.ddys.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.jing.ddys.compose.DdysAppFrame
import com.jing.ddys.compose.screen.DetailScreen
import com.jing.ddys.compose.theme.DdysTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class DetailActivity : ComponentActivity() {

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(URL_KEY)!!
        val viewModel by viewModel<DetailViewModel> { parametersOf(url) }
        setContent {
            DdysTheme {
                DdysAppFrame {
                    DetailScreen(viewModel = viewModel)
                }
            }
        }
    }

    companion object {
        const val URL_KEY = "url"
        fun navigateTo(context: Context, url: String) {
            Intent(context, DetailActivity::class.java).apply {
                putExtra(URL_KEY, url)
                context.startActivity(this)
            }
        }
    }
}
