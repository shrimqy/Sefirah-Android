package com.castle.sefirah.di

import android.app.Application
import android.content.Context
import com.komu.sekia.di.AppCoroutineScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import sefirah.clipboard.ClipboardHandler
import sefirah.clipboard.ClipboardService
import sefirah.common.notifications.NotificationCenter
import sefirah.data.repository.PlaybackRepositoryImpl
import sefirah.data.repository.PreferencesDatastore
import sefirah.domain.repository.NetworkManager
import sefirah.domain.repository.PlaybackRepository
import sefirah.domain.repository.PreferencesRepository
import sefirah.domain.repository.SocketFactory
import sefirah.media.MediaHandler
import sefirah.media.MediaService
import sefirah.network.NetworkManagerImpl
import sefirah.network.NsdService
import sefirah.network.SftpServer
import sefirah.network.SocketFactoryImpl
import sefirah.network.util.MessageSerializer
import sefirah.network.util.TrustManager
import sefirah.notification.NotificationCallback
import sefirah.notification.NotificationHandler
import sefirah.notification.NotificationService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
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

    @Provides
    @Singleton
    fun providesMessageSerializer(): MessageSerializer = MessageSerializer()

    @Provides
    @Singleton
    fun providesNsdService(
        application: Application
    ): NsdService = NsdService(context = application)

    @Provides
    @Singleton
    fun providesCustomTrustManager(
        application: Application
    ): TrustManager {
        return TrustManager(application)
    }

    @Provides
    @Singleton
    fun providesSocketFactory(
        application: Application,
        customTrustManager: TrustManager
    ): SocketFactory {
        return SocketFactoryImpl(application, customTrustManager)
    }

    @Provides
    @Singleton
    fun providesMessageDispatcher(
        context: Context
    ): NetworkManager {
        return NetworkManagerImpl(context)
    }

    @Provides
    @Singleton
    fun providesSftpServer(
        application: Application,
        customTrustManager: TrustManager
    ): SftpServer = SftpServer(application, customTrustManager)

    @Provides
    @Singleton
    fun providesNotificationService(
        context: Context,
        networkManager: NetworkManager
    ): NotificationService {
        return NotificationService(context, networkManager)
    }

    @Provides
    @Singleton
    fun providesNotificationCenter(
        context: Context
    ): NotificationCenter = NotificationCenter(context)

    @Provides
    @Singleton
    fun providesNotificationHandler(
        service: NotificationService
    ): NotificationHandler {
        return service
    }

    @Provides
    @Singleton
    fun provideNotificationCallback(
        service: NotificationService
    ): NotificationCallback {
        return service
    }

    @Provides
    @Singleton
    fun providesClipboardHandler(
        service: ClipboardService
    ): ClipboardHandler {
        return service
    }

    @Provides
    @Singleton
    fun providesMediaService(
        context: Context,
        networkManager: NetworkManager,
        notificationCenter: NotificationCenter
    ) : MediaService {
        return MediaService(context, networkManager, notificationCenter)
    }

    @Provides
    @Singleton
    fun providesMediaHandler(
        service: MediaService
    ) : MediaHandler {
        return service
    }

    @Provides
    @Singleton
    fun providesPreferencesRepository(
        application: Application
    ): PreferencesRepository = PreferencesDatastore(context = application)

    @Provides
    @Singleton
    fun providedPlaybackRepository(): PlaybackRepository {
        return PlaybackRepositoryImpl()
    }
}