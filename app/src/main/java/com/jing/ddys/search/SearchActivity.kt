package com.jing.ddys.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.jing.ddys.compose.DdysAppFrame
import com.jing.ddys.compose.screen.SearchScreen
import com.jing.ddys.compose.theme.DdysTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class SearchActivity : ComponentActivity() {

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel by viewModel<SearchViewModel>()
        setContent {
            DdysTheme {
                DdysAppFrame {
                    SearchScreen(viewModel = viewModel)
                }
            }
        }
    }

    companion object {
        fun navigateTo(context: Context) {
            Intent(context, SearchActivity::class.java).apply {
                context.startActivity(this)
            }
        }
    }
}
