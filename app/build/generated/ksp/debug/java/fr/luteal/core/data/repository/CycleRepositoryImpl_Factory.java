package fr.luteal.core.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import fr.luteal.core.data.local.CycleDao;
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
public final class CycleRepositoryImpl_Factory implements Factory<CycleRepositoryImpl> {
  private final Provider<CycleDao> cycleDaoProvider;

  public CycleRepositoryImpl_Factory(Provider<CycleDao> cycleDaoProvider) {
    this.cycleDaoProvider = cycleDaoProvider;
  }

  @Override
  public CycleRepositoryImpl get() {
    return newInstance(cycleDaoProvider.get());
  }

  public static CycleRepositoryImpl_Factory create(Provider<CycleDao> cycleDaoProvider) {
    return new CycleRepositoryImpl_Factory(cycleDaoProvider);
  }

  public static CycleRepositoryImpl newInstance(CycleDao cycleDao) {
    return new CycleRepositoryImpl(cycleDao);
  }
}
