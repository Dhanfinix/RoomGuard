package dev.dhanfinix.roomguard.hilt;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.dhanfinix.roomguard.core.DatabaseProvider;
import dev.dhanfinix.roomguard.drive.DriveTokenStore;
import dev.dhanfinix.roomguard.drive.RoomGuardDrive;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata({
    "dagger.hilt.android.qualifiers.ApplicationContext",
    "javax.inject.Named"
})
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class RoomGuardDriveModule_ProvideDriveBackupManagerFactory implements Factory<RoomGuardDrive> {
  private final Provider<Context> contextProvider;

  private final Provider<String> appNameProvider;

  private final Provider<DatabaseProvider> databaseProvider;

  private final Provider<DriveTokenStore> tokenStoreProvider;

  public RoomGuardDriveModule_ProvideDriveBackupManagerFactory(Provider<Context> contextProvider,
      Provider<String> appNameProvider, Provider<DatabaseProvider> databaseProvider,
      Provider<DriveTokenStore> tokenStoreProvider) {
    this.contextProvider = contextProvider;
    this.appNameProvider = appNameProvider;
    this.databaseProvider = databaseProvider;
    this.tokenStoreProvider = tokenStoreProvider;
  }

  @Override
  public RoomGuardDrive get() {
    return provideDriveBackupManager(contextProvider.get(), appNameProvider.get(), databaseProvider.get(), tokenStoreProvider.get());
  }

  public static RoomGuardDriveModule_ProvideDriveBackupManagerFactory create(
      Provider<Context> contextProvider, Provider<String> appNameProvider,
      Provider<DatabaseProvider> databaseProvider, Provider<DriveTokenStore> tokenStoreProvider) {
    return new RoomGuardDriveModule_ProvideDriveBackupManagerFactory(contextProvider, appNameProvider, databaseProvider, tokenStoreProvider);
  }

  public static RoomGuardDrive provideDriveBackupManager(Context context, String appName,
      DatabaseProvider databaseProvider, DriveTokenStore tokenStore) {
    return Preconditions.checkNotNullFromProvides(RoomGuardDriveModule.INSTANCE.provideDriveBackupManager(context, appName, databaseProvider, tokenStore));
  }
}
