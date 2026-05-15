package com.jing.ddys.compose.screen

import android.Manifest
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.jing.ddys.R
import com.jing.ddys.room.entity.SearchHistory
import com.jing.ddys.search.SearchResultActivity
import com.jing.ddys.search.SearchViewModel
import com.jing.ddys.search.SpeechToTextParser
import kotlinx.coroutines.launch

@Composable
fun PhoneSearchScreen(viewModel: SearchViewModel) {
    val context = LocalContext.current
    val handleSearchRequest = { keyword: String ->
        viewModel.saveHistory(keyword)
        SearchResultActivity.navigateTo(context, keyword)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PhoneInputKeywordRow(handleSearchRequest)
        Text(text = stringResource(id = R.string.search_history), style = MaterialTheme.typography.titleMedium)
        PhoneSearchHistory(viewModel = viewModel, onKeywordClick = handleSearchRequest)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PhoneInputKeywordRow(onSearch: (String) -> Unit) {
    val context = LocalContext.current
    val speechToTextParser = remember { SpeechToTextParser(context) }
    val permissionState = rememberPermissionState(permission = Manifest.permission.RECORD_AUDIO) {
        if (it) {
            speechToTextParser.startListening()
        }
    }
    var inputKeyword by remember { mutableStateOf("") }
    val sttState by speechToTextParser.state.collectAsState()

    LaunchedEffect(sttState) {
        if (!sttState.isSpeaking && sttState.text.isNotEmpty()) {
            inputKeyword = sttState.text.trim()
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(targetState = sttState.isSpeaking, label = "speech-state") { isSpeaking ->
            IconButton(onClick = {
                if (isSpeaking) {
                    speechToTextParser.stopListening()
                } else if (permissionState.status.isGranted) {
                    speechToTextParser.startListening()
                } else {
                    permissionState.launchPermissionRequest()
                }
            }) {
                if (isSpeaking) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        tint = colorResource(id = R.color.red400),
                        contentDescription = "stop"
                    )
                } else {
                    Icon(imageVector = Icons.Rounded.Mic, contentDescription = "speak")
                }
            }
        }
        TextField(
            value = inputKeyword,
            onValueChange = { inputKeyword = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = {
                Text(
                    text = if (sttState.isSpeaking) {
                        stringResource(R.string.speak_search_keyword)
                    } else {
                        stringResource(R.string.input_search_keyword)
                    }
                )
            }
        )
        IconButton(
            onClick = { onSearch(inputKeyword.trim()) },
            enabled = inputKeyword.isNotBlank()
        ) {
            Icon(imageVector = Icons.Default.Search, contentDescription = "search")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneSearchHistory(viewModel: SearchViewModel, onKeywordClick: (String) -> Unit) {
    val pagingItems = viewModel.historyPager.collectAsLazyPagingItems()
    if (pagingItems.loadState.refresh !is LoadState.NotLoading || pagingItems.itemCount == 0) {
        return
    }

    var confirmDeleteHistory by remember { mutableStateOf<SearchHistory?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(
            count = pagingItems.itemCount,
            key = pagingItems.itemKey { it.keyword }
        ) { index ->
            val history = pagingItems[index] ?: return@items
            ListItem(
                headlineContent = {
                    Text(
                        text = history.keyword,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.combinedClickable(
                    onClick = { onKeywordClick(history.keyword) },
                    onLongClick = { confirmDeleteHistory = history }
                )
            )
            Divider()
        }
    }

    val history = confirmDeleteHistory ?: return
    PhoneDeleteDialog(
        text = String.format(stringResource(id = R.string.confirm_delete_template), history.keyword),
        onDeleteClick = {
            confirmDeleteHistory = null
            coroutineScope.launch {
                viewModel.deleteSearchHistory(history)
                pagingItems.refresh()
            }
        },
        onDeleteAllClick = {
            confirmDeleteHistory = null
            coroutineScope.launch {
                viewModel.deleteAllHistory()
                pagingItems.refresh()
            }
        },
        onCancel = { confirmDeleteHistory = null }
    )
}

@Composable
fun PhoneDeleteDialog(
    text: String,
    onDeleteClick: () -> Unit,
    onDeleteAllClick: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = text) },
        confirmButton = {
            Button(onClick = onDeleteClick) {
                Text(text = stringResource(R.string.button_delete))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDeleteAllClick) {
                    Text(text = stringResource(R.string.button_delete_all))
                }
                TextButton(onClick = onCancel) {
                    Text(text = "取消")
                }
            }
        }
    )
}
