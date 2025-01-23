package com.castle.sefirah.presentation.onboarding

import android.annotation.SuppressLint
import android.app.Application
import android.app.WallpaperManager
import android.content.Context
import android.provider.Settings
import android.provider.Settings.Global
import android.provider.Settings.Secure
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import sefirah.clipboard.ClipboardListener
import sefirah.data.repository.AppRepository
import sefirah.database.model.toEntity
import sefirah.domain.model.LocalDevice
import sefirah.domain.repository.PreferencesRepository
import sefirah.network.util.ECDHHelper
import sefirah.presentation.util.drawableToBase64Compressed
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (publicKey, privateKey) = ECDHHelper.generateKeys()
                val deviceName = Global.getString(getApplication<Application>().contentResolver, "device_name")
                val androidId = Secure.getString(getApplication<Application>().contentResolver, Secure.ANDROID_ID)
                
                val wallpaperBase64 = try {
                    val wallpaperManager = WallpaperManager.getInstance(getApplication())
                    wallpaperManager.drawable?.let { drawable ->
                        drawableToBase64Compressed(drawable)
                    }
                } catch (e: SecurityException) {
                    Log.w("OnboardingViewModel", "Unable to access wallpaper", e)
                    null
                }

                appRepository.addLocalDevice(
                    LocalDevice(
                        deviceId = androidId,
                        deviceName = deviceName,
                        publicKey = publicKey,
                        privateKey = privateKey,
                        wallpaperBase64 = wallpaperBase64
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

    fun savePermissionRequested(permission: String) {
        viewModelScope.launch {
            preferencesRepository.savePermissionRequested(permission)
        }
    }

    fun hasRequestedPermission(permission: String): Flow<Boolean> {
        return preferencesRepository.hasRequestedPermission(permission)
    }
}
