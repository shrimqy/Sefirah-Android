package com.castle.sefirah.presentation.onboarding

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings.Global
import android.provider.Settings.Secure
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import sefirah.data.repository.AppRepository
import sefirah.database.model.toEntity
import sefirah.domain.model.LocalDevice
import sefirah.domain.repository.PreferencesRepository
import sefirah.network.util.ECDHHelper
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val appRepository: AppRepository,
    application: Application
) : AndroidViewModel(application) {

    fun updateStorageLocation(string: String) {
        viewModelScope.launch {
            preferencesRepository.updateStorageLocation(string)
        }
    }

    @SuppressLint("HardwareIds")
    fun addDeviceToDatabase() {
        viewModelScope.launch {
            try {
                val (publicKey, privateKey) = ECDHHelper.generateKeys()
                val deviceName = Global.getString(getApplication<Application>().contentResolver, "device_name")
                val androidId = Secure.getString(getApplication<Application>().contentResolver, Secure.ANDROID_ID)

                appRepository.addLocalDevice(
                    LocalDevice(
                    deviceId = androidId,
                    deviceName = deviceName ?: "Android Device",
                    publicKey = publicKey,
                    privateKey = privateKey
                ).toEntity()
                )
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "Error adding device to database", e)
            }
        }
    }

    fun saveAppEntry() {
        viewModelScope.launch {
            addDeviceToDatabase()
            preferencesRepository.saveAppEntry()
        }
    }
}