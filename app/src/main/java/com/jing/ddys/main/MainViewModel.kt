package com.jing.ddys.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.jing.ddys.repository.HomeRepository
import com.jing.ddys.repository.VideoCardInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class MainViewModel(
    private val homeRepository: HomeRepository
) : ViewModel() {

    private val selectedCategory = MutableStateFlow("/")

    @OptIn(ExperimentalCoroutinesApi::class)
    val pager: Flow<PagingData<VideoCardInfo>> = selectedCategory
        .flatMapLatest { category -> homeRepository.pagerForCategory(category) }
        .cachedIn(viewModelScope)

    fun onCategoryChoose(category: String) {
        if (selectedCategory.value == category) {
            return
        }
        selectedCategory.value = category
    }
}
