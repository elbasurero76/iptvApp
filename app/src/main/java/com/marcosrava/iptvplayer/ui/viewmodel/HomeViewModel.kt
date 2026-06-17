package com.marcosrava.iptvplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcosrava.iptvplayer.data.model.Channel
import com.marcosrava.iptvplayer.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PlaylistRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    val allGroups: StateFlow<List<String>> = repository.allGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentChannels: StateFlow<List<Channel>> = repository.recentChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteChannels: StateFlow<List<Channel>> = repository.favoriteChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val channels: StateFlow<List<Channel>> = combine(
        _searchQuery.debounce(300),
        _selectedGroup
    ) { query, group -> Pair(query, group) }
        .flatMapLatest { (query, group) ->
            when {
                query.isNotBlank() -> repository.searchChannels(query)
                group != null -> repository.getChannelsByGroup(group)
                else -> repository.allChannels
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setGroup(group: String?) { _selectedGroup.value = group }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            repository.setFavorite(channel.id, !channel.isFavorite)
        }
    }
}
