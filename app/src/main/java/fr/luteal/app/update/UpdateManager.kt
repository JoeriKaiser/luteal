package fr.luteal.app.update

import android.content.Intent
import fr.luteal.app.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class UpToDate(val installed: String) : UpdateCheckState
    data class Available(val info: UpdateInfo) : UpdateCheckState
    data object Failed : UpdateCheckState
}

enum class InstallFailure {
    DOWNLOAD,
    MISSING_DIGEST,
    HASH_MISMATCH,
    UNREADABLE,
    WRONG_PACKAGE,
    NOT_NEWER,
    SIGNER_MISMATCH,
    INSTALLER,
    ABORTED
}

sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState
    data object NeedsInstallPermission : UpdateInstallState
    data object Downloading : UpdateInstallState
    data object Verifying : UpdateInstallState
    data object Installing : UpdateInstallState
    /** The installer wants the user to confirm; the Settings card starts [intent] once it is visible. */
    data class NeedsConfirmation(val intent: Intent) : UpdateInstallState
    data class Failed(val reason: InstallFailure, val detail: String? = null) : UpdateInstallState
}

@Singleton
class UpdateManager @Inject constructor(
    private val client: GitHubReleaseClient,
    private val apkInstaller: ApkInstaller
) {
    private val _checkState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val checkState: StateFlow<UpdateCheckState> = _checkState.asStateFlow()

    private val _installState = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    val installState: StateFlow<UpdateInstallState> = _installState.asStateFlow()

    val isAvailableForThisInstall: Boolean by lazy {
        InstallerPolicy.allowsSelfUpdate(apkInstaller.installerPackageName())
    }

    suspend fun fetchOutcome(): UpdateCheckOutcome =
        UpdateEvaluator.evaluate(client.fetchLatestRelease(), BuildConfig.VERSION_NAME)

    suspend fun checkNow() {
        if (_installState.value is UpdateInstallState.Failed) _installState.value = UpdateInstallState.Idle
        _checkState.value = UpdateCheckState.Checking
        _checkState.value = try {
            when (val outcome = fetchOutcome()) {
                is UpdateCheckOutcome.UpToDate -> UpdateCheckState.UpToDate(outcome.installed.toString())
                is UpdateCheckOutcome.Available -> UpdateCheckState.Available(outcome.info)
            }
        } catch (ce: CancellationException) {
            _checkState.value = UpdateCheckState.Idle
            throw ce
        } catch (_: Exception) {
            UpdateCheckState.Failed
        }
    }

    fun resetCheck() {
        _checkState.value = UpdateCheckState.Idle
        _installState.value = UpdateInstallState.Idle
    }

    fun canRequestPackageInstalls(): Boolean = apkInstaller.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent() = apkInstaller.unknownSourcesSettingsIntent()

    /** The downloaded APK is deleted on every path; a committed session keeps its own copy. */
    suspend fun downloadAndInstall(info: UpdateInfo) {
        val asset = info.apk
        val expectedSha256 = info.apkSha256
        if (asset == null || expectedSha256 == null) {
            _installState.value = UpdateInstallState.Failed(InstallFailure.MISSING_DIGEST)
            return
        }
        if (!apkInstaller.canRequestPackageInstalls()) {
            _installState.value = UpdateInstallState.NeedsInstallPermission
            return
        }

        val directory = apkInstaller.updateDirectory()
        val apk = File(directory, asset.name.substringAfterLast('/'))
        try {
            _installState.value = UpdateInstallState.Downloading
            withContext(Dispatchers.IO) { directory.listFiles()?.forEach { it.delete() } }
            try {
                client.download(asset.downloadUrl, apk)
            } catch (e: IOException) {
                _installState.value = UpdateInstallState.Failed(InstallFailure.DOWNLOAD, e.message)
                return
            }

            _installState.value = UpdateInstallState.Verifying
            val verdict = withContext(Dispatchers.IO) {
                val actual = FileInputStream(apk).use { ApkVerifier.sha256Hex(it) }
                val candidate = if (actual.equals(expectedSha256, ignoreCase = true)) {
                    apkInstaller.archiveIdentity(apk)
                } else {
                    null
                }
                ApkVerifier.verify(expectedSha256, actual, candidate, apkInstaller.installedIdentity())
            }
            if (verdict != ApkVerdict.OK) {
                _installState.value = UpdateInstallState.Failed(verdict.toFailure())
                return
            }

            _installState.value = UpdateInstallState.Installing
            try {
                withContext(Dispatchers.IO) { apkInstaller.install(apk) }
            } catch (e: IOException) {
                _installState.value = UpdateInstallState.Failed(InstallFailure.INSTALLER, e.message)
            }
        } catch (ce: CancellationException) {
            _installState.value = UpdateInstallState.Idle
            throw ce
        } finally {
            withContext(Dispatchers.IO + NonCancellable) { apk.delete() }
        }
    }

    fun onUserActionRequired(intent: Intent) {
        _installState.value = UpdateInstallState.NeedsConfirmation(intent)
    }

    fun onConfirmationLaunched(launched: Boolean) {
        if (_installState.value !is UpdateInstallState.NeedsConfirmation) return
        _installState.value = if (launched) {
            UpdateInstallState.Installing
        } else {
            UpdateInstallState.Failed(InstallFailure.INSTALLER)
        }
    }

    fun onInstallSucceeded() {
        // Usually unseen: replacing the running app ends this process.
        _installState.value = UpdateInstallState.Idle
        _checkState.value = UpdateCheckState.Idle
    }

    fun onInstallAborted() {
        _installState.value = UpdateInstallState.Failed(InstallFailure.ABORTED)
    }

    fun onInstallFailed(detail: String?) {
        _installState.value = UpdateInstallState.Failed(InstallFailure.INSTALLER, detail)
    }

    private fun ApkVerdict.toFailure(): InstallFailure = when (this) {
        ApkVerdict.MISSING_DIGEST -> InstallFailure.MISSING_DIGEST
        ApkVerdict.HASH_MISMATCH -> InstallFailure.HASH_MISMATCH
        ApkVerdict.UNREADABLE -> InstallFailure.UNREADABLE
        ApkVerdict.WRONG_PACKAGE -> InstallFailure.WRONG_PACKAGE
        ApkVerdict.NOT_NEWER -> InstallFailure.NOT_NEWER
        ApkVerdict.SIGNER_MISMATCH -> InstallFailure.SIGNER_MISMATCH
        ApkVerdict.OK -> error("OK is not a failure")
    }
}
