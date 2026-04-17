package dev.dhanfinix.roomguard.hilt

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.dhanfinix.roomguard.RoomGuard
import dev.dhanfinix.roomguard.core.LocalBackupManager
import dev.dhanfinix.roomguard.local.RoomGuardLocal
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoomGuardLocalModule {

    /**
     * Bind the Local manager from the shared RoomGuard instance.
     */
    @Provides
    @Singleton
    fun provideLocalManager(roomGuard: RoomGuard): RoomGuardLocal = roomGuard.localManager()!!

    @Provides
    @Singleton
    fun bindLocalBackupManager(local: RoomGuardLocal): LocalBackupManager = local
}
