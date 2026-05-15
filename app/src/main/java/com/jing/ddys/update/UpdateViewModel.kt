package com.jing.ddys.update

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class UpdateViewModel(
    private val updateManager: UpdateManager
) : ViewModel() {

    val updateState = updateManager.state

    fun checkDaily() {
        viewModelScope.launch {
            updateManager.checkDaily()
        }
    }

    fun checkNow() {
        viewModelScope.launch {
            updateManager.checkNow()
        }
    }

    fun downloadAndInstall(activity: Activity) {
        viewModelScope.launch {
            updateManager.downloadAndInstall(activity)
        }
    }

    fun resumePendingInstall(activity: Activity) {
        viewModelScope.launch {
            updateManager.resumePendingInstall(activity)
        }
    }
}
