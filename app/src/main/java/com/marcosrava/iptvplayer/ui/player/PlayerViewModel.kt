package com.marcosrava.iptvplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcosrava.iptvplayer.data.model.Channel
import com.marcosrava.iptvplayer.data.model.EpgProgram
import com.marcosrava.iptvplayer.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: PlaylistRepository
) : ViewModel() {

    private val _currentChannel = MutableStateFlow<Channel?>(null)
    val currentChannel: StateFlow<Channel?> = _currentChannel.asStateFlow()

    private val _currentProgram = MutableStateFlow<EpgProgram?>(null)
    val currentProgram: StateFlow<EpgProgram?> = _currentProgram.asStateFlow()

    private val _nextProgram = MutableStateFlow<EpgProgram?>(null)
    val nextProgram: StateFlow<EpgProgram?> = _nextProgram.asStateFlow()

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    fun setChannel(channel: Channel) {
        _currentChannel.value = channel
        viewModelScope.launch {
            repository.updateLastWatched(channel.id)
            channel.tvgId?.let { tvgId ->
                _currentProgram.value = repository.getCurrentProgram(tvgId)
                _nextProgram.value = repository.getNextProgram(tvgId)
            }
        }
    }

    fun toggleFavorite() {
        val channel = _currentChannel.value ?: return
        viewModelScope.launch {
            repository.setFavorite(channel.id, !channel.isFavorite)
            _currentChannel.value = channel.copy(isFavorite = !channel.isFavorite)
        }
    }

    fun setCasting(casting: Boolean) {
        _isCasting.value = casting
    }
}
