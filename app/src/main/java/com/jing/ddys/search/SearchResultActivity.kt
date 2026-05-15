package com.jing.ddys.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.jing.ddys.compose.DdysAppFrame
import com.jing.ddys.compose.screen.SearchResultScreen
import com.jing.ddys.compose.theme.DdysTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class SearchResultActivity : ComponentActivity() {

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val keyword = intent.getStringExtra("k")!!
        val viewModel by viewModel<SearchResultViewModel> { parametersOf(keyword) }
        setContent {
            DdysTheme {
                DdysAppFrame {
                    SearchResultScreen(viewModel = viewModel)
                }
            }
        }
    }

    companion object {
        fun navigateTo(context: Context, keyword: String) {
            Intent(context, SearchResultActivity::class.java).apply {
                putExtra("k", keyword)
                context.startActivity(this)
            }
        }
    }
}
