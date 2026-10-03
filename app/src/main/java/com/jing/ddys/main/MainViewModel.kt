package com.jing.ddys.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.jing.ddys.repository.HomeRepository
import com.jing.ddys.repository.DiscoverCount
import com.jing.ddys.repository.DiscoverDraft
import com.jing.ddys.repository.DiscoverOptions
import com.jing.ddys.repository.DiscoverRepository
import com.jing.ddys.repository.VideoCardInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DiscoverOptionsState(
    val options: DiscoverOptions? = null,
    val loading: Boolean = false,
    val error: Throwable? = null
)

class MainViewModel(
    private val homeRepository: HomeRepository,
    private val discoverRepository: DiscoverRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeFilterState())
    val state = mutableState.asStateFlow()
    private val mutableOptions = MutableStateFlow(DiscoverOptionsState())
    val optionsState = mutableOptions.asStateFlow()
    private val mutableCount = MutableStateFlow<DiscoverCount?>(null)
    val count = mutableCount.asStateFlow()
    private var optionsJob: Job? = null

    var tvGridIndex = 0
    var tvGridOffset = 0
    var phoneGridIndex = 0
    var phoneGridOffset = 0
    var focusedVideoUrl: String? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val pager: Flow<PagingData<VideoCardInfo>> = state
        .map { it.query to it.generation }
        .distinctUntilChanged()
        .flatMapLatest { (query, generation) ->
            if (!query.filters.isActive) homeRepository.pagerForCategory(query.category)
            else discoverRepository.pager(query) { total ->
                viewModelScope.launch {
                    if (mutableState.value.acceptsCount(generation)) {
                        mutableCount.value = DiscoverCount(query, total)
                    }
                }
            }
        }
        .cachedIn(viewModelScope)

    private fun change(transform: (HomeFilterState) -> HomeFilterState) {
        val previous = mutableState.value
        val next = transform(previous)
        if (previous.generation != next.generation) {
            mutableCount.value = null
            tvGridIndex = 0
            tvGridOffset = 0
            phoneGridIndex = 0
            phoneGridOffset = 0
            focusedVideoUrl = null
        }
        mutableState.value = next
    }

    fun onCategoryChoose(category: String) = change { it.category(category) }
    fun openFilters() {
        change { it.open() }
        loadOptions()
    }
    fun cancelFilters() = change { it.cancel() }
    fun editFilters(draft: DiscoverDraft) = change { it.edit(draft) }
    fun resetDraft() = change { it.resetDraft() }
    fun applyFilters() {
        if (mutableOptions.value.options != null && !mutableOptions.value.loading && mutableOptions.value.error == null) {
            change { it.apply() }
        }
    }
    fun clearFilters() = change { it.clear() }

    fun loadOptions() {
        optionsJob?.cancel()
        mutableOptions.value = DiscoverOptionsState(loading = true)
        optionsJob = viewModelScope.launch {
            try {
                val options = withContext(Dispatchers.IO) { discoverRepository.options() }
                mutableOptions.value = DiscoverOptionsState(options = options)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableOptions.value = DiscoverOptionsState(error = error)
            }
        }
    }
}
