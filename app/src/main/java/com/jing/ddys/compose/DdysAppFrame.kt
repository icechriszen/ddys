package com.jing.ddys.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.jing.ddys.R
import androidx.compose.material3.LocalContentColor as PhoneLocalContentColor
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor as TvLocalContentColor
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DdysAppFrame(
    modifier: Modifier = Modifier,
    formFactor: AppFormFactor = rememberAppFormFactor(),
    content: @Composable () -> Unit
) {
    val framedModifier = when (formFactor) {
        AppFormFactor.Tv -> modifier
            .background(TvMaterialTheme.colorScheme.surface)
            .padding(
                horizontal = dimensionResource(id = R.dimen.screen_h_padding),
                vertical = dimensionResource(id = R.dimen.screen_v_padding)
            )
            .fillMaxWidth()

        AppFormFactor.Phone -> modifier
            .background(TvMaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .fillMaxSize()
    }

    Box(modifier = framedModifier) {
        CompositionLocalProvider(
            TvLocalContentColor provides TvMaterialTheme.colorScheme.onSurface,
            PhoneLocalContentColor provides TvMaterialTheme.colorScheme.onSurface
        ) {
            content()
        }
    }
}
