package fr.luteal.app.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.repository.CycleRepository
import fr.luteal.core.data.repository.CycleRepositoryImpl
import fr.luteal.core.data.repository.SymptomRepository
import fr.luteal.core.data.repository.SymptomRepositoryImpl
import fr.luteal.core.data.repository.UserRepository
import fr.luteal.core.data.repository.UserRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCycleRepository(
        cycleRepositoryImpl: CycleRepositoryImpl
    ): CycleRepository

    @Binds
    @Singleton
    abstract fun bindSymptomRepository(
        symptomRepositoryImpl: SymptomRepositoryImpl
    ): SymptomRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository

    companion object {
        @Provides
        @Singleton
        fun provideUserPreferencesDataStore(
            @ApplicationContext context: Context
        ): UserPreferencesDataStore {
            return UserPreferencesDataStore(context)
        }
    }
}
