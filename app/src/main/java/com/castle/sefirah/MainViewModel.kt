package com.castle.sefirah

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castle.sefirah.navigation.Graph
import com.castle.sefirah.navigation.OnboardingRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sefirah.domain.repository.PreferencesRepository
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    context: Context,
    preferencesRepository: PreferencesRepository,
): ViewModel() {
    var splashCondition by mutableStateOf(true)
        private set

    var startDestination by mutableStateOf(Graph.MainScreenGraph)
        private set

    init {
        Log.d("MainViewModel", "ViewModel initialized")
        viewModelScope.launch {
            preferencesRepository.readAppEntry().collectLatest { shouldStartFromHomeScreen ->
                Log.d("AppEntry", shouldStartFromHomeScreen.toString())
                startDestination = if(shouldStartFromHomeScreen) {
                    Graph.MainScreenGraph
                } else {
                    OnboardingRoute.OnboardingScreen.route
                }
                delay(150)
                splashCondition = false
            }
        }
    }
}