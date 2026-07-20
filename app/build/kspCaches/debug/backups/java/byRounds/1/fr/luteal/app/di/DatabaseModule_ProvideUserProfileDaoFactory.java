package fr.luteal.app.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fr.luteal.core.data.local.LutealDatabase;
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
public final class DatabaseModule_ProvideUserProfileDaoFactory implements Factory<UserProfileDao> {
  private final Provider<LutealDatabase> databaseProvider;

  public DatabaseModule_ProvideUserProfileDaoFactory(Provider<LutealDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public UserProfileDao get() {
    return provideUserProfileDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideUserProfileDaoFactory create(
      Provider<LutealDatabase> databaseProvider) {
    return new DatabaseModule_ProvideUserProfileDaoFactory(databaseProvider);
  }

  public static UserProfileDao provideUserProfileDao(LutealDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideUserProfileDao(database));
  }
}
