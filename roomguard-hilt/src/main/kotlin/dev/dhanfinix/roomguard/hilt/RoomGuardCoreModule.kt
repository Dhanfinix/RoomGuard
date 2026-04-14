package dev.dhanfinix.roomguard.hilt

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.dhanfinix.roomguard.core.RoomGuardConfig
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoomGuardCoreModule {

    /**
     * Provides the library configuration.
     * The host can override this by providing their own [RoomGuardConfig] in a module
     * that is installed in [SingletonComponent].
     */
    @Provides
    @Singleton
    fun provideRoomGuardConfig(): RoomGuardConfig = RoomGuardConfig()
}
