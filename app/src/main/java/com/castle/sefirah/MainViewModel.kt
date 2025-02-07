package com.castle.sefirah

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.castle.sefirah.navigation.Graph
import com.castle.sefirah.navigation.OnboardingRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sefirah.domain.repository.PreferencesRepository
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    context: Context,
    private val preferencesRepository: PreferencesRepository,
): ViewModel() {
    var splashCondition by mutableStateOf(true)
        private set

    var startDestination by mutableStateOf(OnboardingRoute.OnboardingScreen.route)
        private set

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val hasCompletedOnboarding = preferencesRepository.readAppEntry()
            
            withContext(Dispatchers.Main) {
                startDestination = if (hasCompletedOnboarding) {
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