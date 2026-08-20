package fr.luteal.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.luteal.core.data.security.KeystorePinSecretStore
import fr.luteal.core.data.security.PinSecretStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindPinSecretStore(impl: KeystorePinSecretStore): PinSecretStore
}
