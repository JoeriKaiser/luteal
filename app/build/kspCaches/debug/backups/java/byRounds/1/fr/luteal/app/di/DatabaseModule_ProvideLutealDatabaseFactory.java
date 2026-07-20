package fr.luteal.app.di;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fr.luteal.core.data.local.LutealDatabase;
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
public final class DatabaseModule_ProvideLutealDatabaseFactory implements Factory<LutealDatabase> {
  private final Provider<Context> contextProvider;

  public DatabaseModule_ProvideLutealDatabaseFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public LutealDatabase get() {
    return provideLutealDatabase(contextProvider.get());
  }

  public static DatabaseModule_ProvideLutealDatabaseFactory create(
      Provider<Context> contextProvider) {
    return new DatabaseModule_ProvideLutealDatabaseFactory(contextProvider);
  }

  public static LutealDatabase provideLutealDatabase(Context context) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideLutealDatabase(context));
  }
}
