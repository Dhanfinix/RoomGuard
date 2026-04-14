package dev.dhanfinix.roomguard.hilt;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.dhanfinix.roomguard.core.CsvSerializer;
import dev.dhanfinix.roomguard.core.LocalBackupManager;
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
public final class RoomGuardLocalModule_ProvideLocalBackupManagerFactory implements Factory<LocalBackupManager> {
  private final Provider<Context> contextProvider;

  private final Provider<CsvSerializer> serializerProvider;

  private final Provider<String> filePrefixProvider;

  public RoomGuardLocalModule_ProvideLocalBackupManagerFactory(Provider<Context> contextProvider,
      Provider<CsvSerializer> serializerProvider, Provider<String> filePrefixProvider) {
    this.contextProvider = contextProvider;
    this.serializerProvider = serializerProvider;
    this.filePrefixProvider = filePrefixProvider;
  }

  @Override
  public LocalBackupManager get() {
    return provideLocalBackupManager(contextProvider.get(), serializerProvider.get(), filePrefixProvider.get());
  }

  public static RoomGuardLocalModule_ProvideLocalBackupManagerFactory create(
      Provider<Context> contextProvider, Provider<CsvSerializer> serializerProvider,
      Provider<String> filePrefixProvider) {
    return new RoomGuardLocalModule_ProvideLocalBackupManagerFactory(contextProvider, serializerProvider, filePrefixProvider);
  }

  public static LocalBackupManager provideLocalBackupManager(Context context,
      CsvSerializer serializer, String filePrefix) {
    return Preconditions.checkNotNullFromProvides(RoomGuardLocalModule.INSTANCE.provideLocalBackupManager(context, serializer, filePrefix));
  }
}
