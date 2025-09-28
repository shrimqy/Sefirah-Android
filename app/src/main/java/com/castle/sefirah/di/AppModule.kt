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
import sefirah.common.notifications.NotificationCenter
import sefirah.data.repository.AppUpdateChecker
import sefirah.data.repository.PreferencesDatastore
import sefirah.data.repository.ReleaseRepository
import sefirah.database.AppRepository
import sefirah.domain.repository.NetworkManager
import sefirah.domain.repository.PreferencesRepository
import sefirah.domain.repository.SocketFactory
import sefirah.network.NetworkHelper
import sefirah.network.NetworkManagerImpl
import sefirah.network.NsdService
import sefirah.network.SocketFactoryImpl
import sefirah.network.extensions.ActionHandler
import sefirah.network.sftp.SftpServer
import sefirah.network.util.MessageSerializer
import sefirah.network.util.TrustManager
import sefirah.notification.NotificationCallback
import sefirah.notification.NotificationHandler
import sefirah.notification.NotificationService
import sefirah.projection.media.MediaHandler
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
        customTrustManager: TrustManager,
        appRepository: AppRepository
    ): SftpServer = SftpServer(application, customTrustManager, appRepository)

    @Provides
    @Singleton
    fun providesNotificationService(
        context: Context,
        networkManager: NetworkManager,
        preferencesRepository: PreferencesRepository
    ): NotificationService {
        return NotificationService(context, networkManager, preferencesRepository)
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
        context: Context,
        socketFactory: SocketFactory,
        appRepository: AppRepository
    ): ClipboardHandler {
        return ClipboardHandler(context, socketFactory, appRepository)
    }

    @Provides
    @Singleton
    fun providesMediaHandler(
        context: Context,
        networkManager: NetworkManager,
        notificationCenter: NotificationCenter,
        preferencesRepository: PreferencesRepository
    ) : MediaHandler {
        return MediaHandler(context, networkManager, notificationCenter, preferencesRepository)
    }

    @Provides
    @Singleton
    fun providesPreferencesRepository(
        application: Application
    ): PreferencesRepository = PreferencesDatastore(context = application)

    @Provides
    @Singleton
    fun providesAppUpdateChecker(
        application: Application,
        releaseRepository: ReleaseRepository
    ) : AppUpdateChecker {
        return AppUpdateChecker(application, releaseRepository)
    }

    @Provides
    @Singleton
    fun providesReleaseRepository(
        preferencesRepository: PreferencesRepository,
        networkHelper: NetworkHelper
    ) : ReleaseRepository {
        return ReleaseRepository(preferencesRepository, networkHelper)
    }

    @Provides
    @Singleton
    fun providesActionHandler(): ActionHandler {
        return ActionHandler()
    }
}