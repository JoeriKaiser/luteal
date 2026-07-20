package fr.luteal.core.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fr.luteal.core.data.datastore.UserPreferencesDataStore;
import fr.luteal.core.data.local.UserProfileDao;
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
public final class UserRepositoryImpl_Factory implements Factory<UserRepositoryImpl> {
  private final Provider<UserProfileDao> userProfileDaoProvider;

  private final Provider<UserPreferencesDataStore> userPreferencesDataStoreProvider;

  public UserRepositoryImpl_Factory(Provider<UserProfileDao> userProfileDaoProvider,
      Provider<UserPreferencesDataStore> userPreferencesDataStoreProvider) {
    this.userProfileDaoProvider = userProfileDaoProvider;
    this.userPreferencesDataStoreProvider = userPreferencesDataStoreProvider;
  }

  @Override
  public UserRepositoryImpl get() {
    return newInstance(userProfileDaoProvider.get(), userPreferencesDataStoreProvider.get());
  }

  public static UserRepositoryImpl_Factory create(Provider<UserProfileDao> userProfileDaoProvider,
      Provider<UserPreferencesDataStore> userPreferencesDataStoreProvider) {
    return new UserRepositoryImpl_Factory(userProfileDaoProvider, userPreferencesDataStoreProvider);
  }

  public static UserRepositoryImpl newInstance(UserProfileDao userProfileDao,
      UserPreferencesDataStore userPreferencesDataStore) {
    return new UserRepositoryImpl(userProfileDao, userPreferencesDataStore);
  }
}
