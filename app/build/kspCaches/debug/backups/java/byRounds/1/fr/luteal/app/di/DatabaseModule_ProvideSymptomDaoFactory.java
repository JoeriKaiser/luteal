package fr.luteal.app.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fr.luteal.core.data.local.LutealDatabase;
import fr.luteal.core.data.local.SymptomDao;
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
public final class DatabaseModule_ProvideSymptomDaoFactory implements Factory<SymptomDao> {
  private final Provider<LutealDatabase> databaseProvider;

  public DatabaseModule_ProvideSymptomDaoFactory(Provider<LutealDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public SymptomDao get() {
    return provideSymptomDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideSymptomDaoFactory create(
      Provider<LutealDatabase> databaseProvider) {
    return new DatabaseModule_ProvideSymptomDaoFactory(databaseProvider);
  }

  public static SymptomDao provideSymptomDao(LutealDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideSymptomDao(database));
  }
}
