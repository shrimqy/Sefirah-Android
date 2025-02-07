package com.castle.sefirah.presentation.onboarding

import androidx.compose.runtime.Composable
import com.castle.sefirah.presentation.settings.SettingsViewModel

internal interface OnboardingStep {
    @Composable
    fun Content(viewModel: SettingsViewModel)
}