package dev.dhanfinix.roomguard.hilt;

@dagger.Module()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007J,\u0010\u0007\u001a\u00020\u00062\b\b\u0001\u0010\b\u001a\u00020\t2\b\b\u0001\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000fH\u0007J\u0012\u0010\u0010\u001a\u00020\u000f2\b\b\u0001\u0010\b\u001a\u00020\tH\u0007\u00a8\u0006\u0011"}, d2 = {"Ldev/dhanfinix/roomguard/hilt/RoomGuardDriveModule;", "", "()V", "bindDriveBackupManager", "Ldev/dhanfinix/roomguard/core/DriveBackupManager;", "drive", "Ldev/dhanfinix/roomguard/drive/RoomGuardDrive;", "provideDriveBackupManager", "context", "Landroid/content/Context;", "appName", "", "databaseProvider", "Ldev/dhanfinix/roomguard/core/DatabaseProvider;", "tokenStore", "Ldev/dhanfinix/roomguard/drive/DriveTokenStore;", "provideDriveTokenStore", "roomguard-hilt_debug"})
@dagger.hilt.InstallIn(value = {dagger.hilt.components.SingletonComponent.class})
public final class RoomGuardDriveModule {
    @org.jetbrains.annotations.NotNull()
    public static final dev.dhanfinix.roomguard.hilt.RoomGuardDriveModule INSTANCE = null;
    
    private RoomGuardDriveModule() {
        super();
    }
    
    @dagger.Provides()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public final dev.dhanfinix.roomguard.drive.DriveTokenStore provideDriveTokenStore(@dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    /**
     * Provide [RoomGuardDrive] with host-supplied [DatabaseProvider] and app name.
     * The host must bind [DatabaseProvider] in their own Hilt module.
     *
     * @Named("appName") — host must provide a String with this qualifier
     */
    @dagger.Provides()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public final dev.dhanfinix.roomguard.drive.RoomGuardDrive provideDriveBackupManager(@dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context, @javax.inject.Named(value = "appName")
    @org.jetbrains.annotations.NotNull()
    java.lang.String appName, @org.jetbrains.annotations.NotNull()
    dev.dhanfinix.roomguard.core.DatabaseProvider databaseProvider, @org.jetbrains.annotations.NotNull()
    dev.dhanfinix.roomguard.drive.DriveTokenStore tokenStore) {
        return null;
    }
    
    /**
     * Also bind [DriveBackupManager] interface to the same [RoomGuardDrive] instance.
     */
    @dagger.Provides()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public final dev.dhanfinix.roomguard.core.DriveBackupManager bindDriveBackupManager(@org.jetbrains.annotations.NotNull()
    dev.dhanfinix.roomguard.drive.RoomGuardDrive drive) {
        return null;
    }
}