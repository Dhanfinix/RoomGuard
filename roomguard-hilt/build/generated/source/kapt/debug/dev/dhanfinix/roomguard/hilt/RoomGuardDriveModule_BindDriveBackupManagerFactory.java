package dev.dhanfinix.roomguard.hilt;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.dhanfinix.roomguard.core.DriveBackupManager;
import dev.dhanfinix.roomguard.drive.RoomGuardDrive;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
public final class RoomGuardDriveModule_BindDriveBackupManagerFactory implements Factory<DriveBackupManager> {
  private final Provider<RoomGuardDrive> driveProvider;

  public RoomGuardDriveModule_BindDriveBackupManagerFactory(
      Provider<RoomGuardDrive> driveProvider) {
    this.driveProvider = driveProvider;
  }

  @Override
  public DriveBackupManager get() {
    return bindDriveBackupManager(driveProvider.get());
  }

  public static RoomGuardDriveModule_BindDriveBackupManagerFactory create(
      Provider<RoomGuardDrive> driveProvider) {
    return new RoomGuardDriveModule_BindDriveBackupManagerFactory(driveProvider);
  }

  public static DriveBackupManager bindDriveBackupManager(RoomGuardDrive drive) {
    return Preconditions.checkNotNullFromProvides(RoomGuardDriveModule.INSTANCE.bindDriveBackupManager(drive));
  }
}
