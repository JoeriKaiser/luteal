package fr.luteal.core.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
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
public final class SymptomRepositoryImpl_Factory implements Factory<SymptomRepositoryImpl> {
  private final Provider<SymptomDao> symptomDaoProvider;

  public SymptomRepositoryImpl_Factory(Provider<SymptomDao> symptomDaoProvider) {
    this.symptomDaoProvider = symptomDaoProvider;
  }

  @Override
  public SymptomRepositoryImpl get() {
    return newInstance(symptomDaoProvider.get());
  }

  public static SymptomRepositoryImpl_Factory create(Provider<SymptomDao> symptomDaoProvider) {
    return new SymptomRepositoryImpl_Factory(symptomDaoProvider);
  }

  public static SymptomRepositoryImpl newInstance(SymptomDao symptomDao) {
    return new SymptomRepositoryImpl(symptomDao);
  }
}
