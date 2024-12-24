package com.castle.sefirah.presentation.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sefirah.domain.model.PreferencesSettings
import sefirah.domain.repository.PreferencesRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    application: Application
) : AndroidViewModel(application) {
    private val _preferencesSettings = MutableStateFlow<PreferencesSettings?>(null)
    val preferencesSettings: StateFlow<PreferencesSettings?> = _preferencesSettings

    init {
        viewModelScope.launch {
            preferencesRepository.preferenceSettings().collectLatest {
                _preferencesSettings.value = it
            }
        }
    }

    fun saveAutoDiscoverySettings(boolean: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveAutoDiscoverySettings(boolean)
        }
    }

    fun saveImageClipboardSettings(boolean: Boolean) {
        viewModelScope.launch {
            preferencesRepository.saveImageClipboardSettings(boolean)
        }
    }

    fun updateStorageLocation(string: String) {
        viewModelScope.launch {
            preferencesRepository.updateStorageLocation(string)
        }
    }
}