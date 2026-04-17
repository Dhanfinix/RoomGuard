package dev.dhanfinix.roomguard.hilt

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.dhanfinix.roomguard.RoomGuard
import dev.dhanfinix.roomguard.core.*
import dev.dhanfinix.roomguard.drive.DriveTokenStore
import dev.dhanfinix.roomguard.drive.RoomGuardDrive
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoomGuardDriveModule {

    /**
     * Build the shared RoomGuard facade once, then expose its pieces through Hilt.
     * The host only needs to provide [appName] once; the Local file prefix is derived
     * automatically unless the builder overrides it.
     */
    @Provides
    @Singleton
    fun provideRoomGuard(
        @ApplicationContext context: Context,
        @Named("appName") appName: String,
        databaseProvider: DatabaseProvider,
        serializer: CsvSerializer,
        tokenStore: DriveTokenStore,
        config: RoomGuardConfig
    ): RoomGuard = RoomGuard.Builder(context)
        .appName(appName)
        .databaseProvider(databaseProvider)
        .tokenStore(tokenStore)
        .csvSerializer(serializer)
        .config(config)
        .build()

    /**
     * Also bind [RoomGuardDrive] and [DriveBackupManager] to the same instance.
     */
    @Provides
    @Singleton
    fun provideDriveManager(roomGuard: RoomGuard): RoomGuardDrive = roomGuard.driveManager()!!

    @Provides
    @Singleton
    fun bindDriveBackupManager(
        drive: RoomGuardDrive
    ): DriveBackupManager = drive

    @Provides
    @Singleton
    fun provideDriveTokenStore(roomGuard: RoomGuard): DriveTokenStore = roomGuard.tokenStore()!!
}
