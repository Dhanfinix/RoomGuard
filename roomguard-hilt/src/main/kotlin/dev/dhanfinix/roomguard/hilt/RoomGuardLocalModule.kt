package dev.dhanfinix.roomguard.hilt

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.dhanfinix.roomguard.core.*
import dev.dhanfinix.roomguard.local.RoomGuardLocal
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoomGuardLocalModule {

    /**
     * Provide [LocalBackupManager] with host-supplied [CsvSerializer] and file prefix.
     * The host must bind [CsvSerializer] in their own Hilt module.
     *
     * @Named("csvFilePrefix") — host must provide a String with this qualifier.
     *        Example: "myapp_backup"
     */
    @Provides
    @Singleton
    fun provideLocalBackupManager(
        @ApplicationContext context: Context,
        serializer: CsvSerializer,
        @Named("csvFilePrefix") filePrefix: String,
        config: RoomGuardConfig
    ): LocalBackupManager = RoomGuardLocal(context, serializer, filePrefix, config)
}
