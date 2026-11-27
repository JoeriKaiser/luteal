package fr.luteal.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.luteal.app.BuildConfig
import fr.luteal.app.update.GitHubReleaseClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Separate client so nothing from the sync client (headers, pool) reaches GitHub. */
@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun provideGitHubReleaseClient(): GitHubReleaseClient = GitHubReleaseClient(
        httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build(),
        latestReleaseUrl = GitHubReleaseClient.LATEST_RELEASE_URL,
        userAgent = "Luteal/${BuildConfig.VERSION_NAME}"
    )
}
