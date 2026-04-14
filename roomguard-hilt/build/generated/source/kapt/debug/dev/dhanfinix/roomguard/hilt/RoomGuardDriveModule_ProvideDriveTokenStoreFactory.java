package dev.dhanfinix.roomguard.hilt;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.dhanfinix.roomguard.drive.DriveTokenStore;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class RoomGuardDriveModule_ProvideDriveTokenStoreFactory implements Factory<DriveTokenStore> {
  private final Provider<Context> contextProvider;

  public RoomGuardDriveModule_ProvideDriveTokenStoreFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public DriveTokenStore get() {
    return provideDriveTokenStore(contextProvider.get());
  }

  public static RoomGuardDriveModule_ProvideDriveTokenStoreFactory create(
      Provider<Context> contextProvider) {
    return new RoomGuardDriveModule_ProvideDriveTokenStoreFactory(contextProvider);
  }

  public static DriveTokenStore provideDriveTokenStore(Context context) {
    return Preconditions.checkNotNullFromProvides(RoomGuardDriveModule.INSTANCE.provideDriveTokenStore(context));
  }
}
