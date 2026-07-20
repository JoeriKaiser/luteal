package fr.luteal.app.di;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fr.luteal.core.data.datastore.UserPreferencesDataStore;
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
public final class RepositoryModule_Companion_ProvideUserPreferencesDataStoreFactory implements Factory<UserPreferencesDataStore> {
  private final Provider<Context> contextProvider;

  public RepositoryModule_Companion_ProvideUserPreferencesDataStoreFactory(
      Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public UserPreferencesDataStore get() {
    return provideUserPreferencesDataStore(contextProvider.get());
  }

  public static RepositoryModule_Companion_ProvideUserPreferencesDataStoreFactory create(
      Provider<Context> contextProvider) {
    return new RepositoryModule_Companion_ProvideUserPreferencesDataStoreFactory(contextProvider);
  }

  public static UserPreferencesDataStore provideUserPreferencesDataStore(Context context) {
    return Preconditions.checkNotNullFromProvides(RepositoryModule.Companion.provideUserPreferencesDataStore(context));
  }
}
