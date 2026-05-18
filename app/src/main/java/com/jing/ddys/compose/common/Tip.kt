package com.jing.ddys.compose.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jing.ddys.compose.AppFormFactor
import com.jing.ddys.compose.rememberAppFormFactor
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jing.ddys.R
import androidx.compose.material3.Button as PhoneButton
import androidx.compose.material3.Text as PhoneText

enum class ErrorTipControls {
    PhoneTouch,
    TvFocus
}

fun errorTipControlsFor(formFactor: AppFormFactor): ErrorTipControls = when (formFactor) {
    AppFormFactor.Phone -> ErrorTipControls.PhoneTouch
    AppFormFactor.Tv -> ErrorTipControls.TvFocus
}


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Loading(text: String = "Loading"): Unit {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = text)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ErrorTip(
    message: String,
    primaryActionText: String? = null,
    primaryAction: (() -> Unit)? = null,
    retry: () -> Unit = { }
) {
    val controls = errorTipControlsFor(rememberAppFormFactor())
    if (controls == ErrorTipControls.PhoneTouch) {
        PhoneErrorTip(
            message = message,
            primaryActionText = primaryActionText,
            primaryAction = primaryAction,
            retry = retry
        )
        return
    }

    val focusRequester = remember {
        FocusRequester()
    }
    val hasPrimaryAction = primaryActionText != null && primaryAction != null
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = message)
        Spacer(modifier = Modifier.height(10.dp))
        if (hasPrimaryAction) {
            ErrorButton(
                text = primaryActionText!!,
                onClick = primaryAction!!,
                modifier = Modifier.focusRequester(focusRequester)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        ErrorButton(
            text = stringResource(R.string.button_retry),
            onClick = retry,
            modifier = if (hasPrimaryAction) Modifier else Modifier.focusRequester(focusRequester)
        )
    }
    LaunchedEffect(hasPrimaryAction) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun PhoneErrorTip(
    message: String,
    primaryActionText: String? = null,
    primaryAction: (() -> Unit)? = null,
    retry: () -> Unit = { }
) {
    val hasPrimaryAction = primaryActionText != null && primaryAction != null
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PhoneText(text = message)
        Spacer(modifier = Modifier.height(12.dp))
        if (hasPrimaryAction) {
            PhoneButton(onClick = primaryAction!!) {
                PhoneText(text = primaryActionText!!)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        PhoneButton(onClick = retry) {
            PhoneText(text = stringResource(R.string.button_retry))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        border = ButtonDefaults.border(
            focusedBorder = Border(
                BorderStroke(2.dp, MaterialTheme.colorScheme.border),
                shape = MaterialTheme.shapes.extraLarge
            )
        ),
        shape = ButtonDefaults.shape(shape = MaterialTheme.shapes.extraLarge),
        scale = ButtonScale.None,
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            focusedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    ) {
        Text(text = text)
    }
}
