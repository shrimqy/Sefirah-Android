package com.castle.sefirah.di

import android.content.Context
import com.komu.sekia.di.AppCoroutineScope
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import sefirah.data.repository.DeviceManagerImpl
import sefirah.data.repository.PreferencesDatastore
import sefirah.domain.repository.DeviceManager
import sefirah.domain.repository.NetworkManager
import sefirah.domain.repository.PreferencesRepository
import sefirah.domain.repository.SocketFactory
import sefirah.network.NetworkManagerImpl
import sefirah.network.SocketFactoryImpl
import sefirah.notification.NotificationCallback
import sefirah.notification.NotificationHandler
import sefirah.notification.NotificationService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppModule {

    @Binds
    abstract fun bindSocketFactory(impl: SocketFactoryImpl): SocketFactory

    @Binds
    abstract fun bindDeviceManager(impl: DeviceManagerImpl): DeviceManager

    @Binds
    abstract fun bindNetworkManager(impl: NetworkManagerImpl): NetworkManager

    @Binds
    abstract fun bindPreferencesRepository(impl: PreferencesDatastore): PreferencesRepository

    @Binds
    abstract fun bindNotificationHandler(impl: NotificationService): NotificationHandler

    @Binds
    abstract fun bindNotificationCallback(impl: NotificationService): NotificationCallback

    companion object {
        @Provides
        fun provideAppContext(@ApplicationContext context: Context) = context

        @Provides
        @Singleton
        fun provideAppCoroutineScope(): AppCoroutineScope {
            return object : AppCoroutineScope {
                override val coroutineContext =
                    SupervisorJob() + Dispatchers.Main.immediate + CoroutineName("App")
            }
        }
    }
}