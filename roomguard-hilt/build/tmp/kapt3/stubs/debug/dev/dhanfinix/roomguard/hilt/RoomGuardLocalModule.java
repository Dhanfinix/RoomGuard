package dev.dhanfinix.roomguard.hilt;

@dagger.Module()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J$\u0010\u0003\u001a\u00020\u00042\b\b\u0001\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\b\b\u0001\u0010\t\u001a\u00020\nH\u0007\u00a8\u0006\u000b"}, d2 = {"Ldev/dhanfinix/roomguard/hilt/RoomGuardLocalModule;", "", "()V", "provideLocalBackupManager", "Ldev/dhanfinix/roomguard/core/LocalBackupManager;", "context", "Landroid/content/Context;", "serializer", "Ldev/dhanfinix/roomguard/core/CsvSerializer;", "filePrefix", "", "roomguard-hilt_debug"})
@dagger.hilt.InstallIn(value = {dagger.hilt.components.SingletonComponent.class})
public final class RoomGuardLocalModule {
    @org.jetbrains.annotations.NotNull()
    public static final dev.dhanfinix.roomguard.hilt.RoomGuardLocalModule INSTANCE = null;
    
    private RoomGuardLocalModule() {
        super();
    }
    
    /**
     * Provide [LocalBackupManager] with host-supplied [CsvSerializer] and file prefix.
     * The host must bind [CsvSerializer] in their own Hilt module.
     *
     * @Named("csvFilePrefix") — host must provide a String with this qualifier.
     *       Example: "myapp_backup"
     */
    @dagger.Provides()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public final dev.dhanfinix.roomguard.core.LocalBackupManager provideLocalBackupManager(@dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    dev.dhanfinix.roomguard.core.CsvSerializer serializer, @javax.inject.Named(value = "csvFilePrefix")
    @org.jetbrains.annotations.NotNull()
    java.lang.String filePrefix) {
        return null;
    }
}