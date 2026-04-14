package dev.dhanfinix.roomguard.hilt

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.dhanfinix.roomguard.core.*
import dev.dhanfinix.roomguard.drive.DriveTokenStore
import dev.dhanfinix.roomguard.drive.RoomGuardDrive
import dev.dhanfinix.roomguard.drive.token.DataStoreDriveTokenStore
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoomGuardDriveModule {

    @Provides
    @Singleton
    fun provideDriveTokenStore(
        @ApplicationContext context: Context
    ): DriveTokenStore = DataStoreDriveTokenStore(context)

    /**
     * Provide [RoomGuardDrive] with host-supplied [DatabaseProvider] and app name.
     * The host must bind [DatabaseProvider] in their own Hilt module.
     *
     * @Named("appName") — host must provide a String with this qualifier
     */
    @Provides
    @Singleton
    fun provideDriveBackupManager(
        @ApplicationContext context: Context,
        @Named("appName") appName: String,
        databaseProvider: DatabaseProvider,
        tokenStore: DriveTokenStore,
        config: RoomGuardConfig
    ): RoomGuardDrive = RoomGuardDrive(context, appName, databaseProvider, tokenStore, config)

    /**
     * Also bind [DriveBackupManager] interface to the same [RoomGuardDrive] instance.
     */
    @Provides
    @Singleton
    fun bindDriveBackupManager(
        drive: RoomGuardDrive
    ): DriveBackupManager = drive
}
