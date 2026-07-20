package fr.luteal.app.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fr.luteal.core.data.local.CycleDao;
import fr.luteal.core.data.local.LutealDatabase;
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
public final class DatabaseModule_ProvideCycleDaoFactory implements Factory<CycleDao> {
  private final Provider<LutealDatabase> databaseProvider;

  public DatabaseModule_ProvideCycleDaoFactory(Provider<LutealDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public CycleDao get() {
    return provideCycleDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideCycleDaoFactory create(
      Provider<LutealDatabase> databaseProvider) {
    return new DatabaseModule_ProvideCycleDaoFactory(databaseProvider);
  }

  public static CycleDao provideCycleDao(LutealDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideCycleDao(database));
  }
}
