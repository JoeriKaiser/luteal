package fr.luteal.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val packageManager: PackageManager get() = context.packageManager

    /** Null for adb installs and sideloads that recorded no installer. */
    fun installerPackageName(): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(context.packageName)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    fun canRequestPackageInstalls(): Boolean = packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun updateDirectory(): File = File(context.cacheDir, UPDATE_DIR)

    fun installedIdentity(): ApkIdentity {
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageInfo(context.packageName, signatureFlags())
        return checkNotNull(info.toIdentity())
    }

    fun archiveIdentity(apk: File): ApkIdentity? {
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageArchiveInfo(apk.absolutePath, signatureFlags()) ?: return null
        return info.toIdentity()
    }

    /** The result reaches [UpdateInstallReceiver]. [apk] can be deleted once this returns. */
    fun install(apk: File) {
        val installer = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite(SESSION_APK_NAME, 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(statusReceiver(sessionId).intentSender)
            }
        } catch (e: Exception) {
            installer.abandonSessionQuietly(sessionId)
            throw if (e is IOException) e else IOException(e.message, e)
        }
    }

    private fun statusReceiver(sessionId: Int): PendingIntent {
        val intent = Intent(context, UpdateInstallReceiver::class.java)
            .setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS)
            .setPackage(context.packageName)
        // Mutable so the installer can fill in EXTRA_STATUS; the intent is explicit.
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutability
        )
    }

    private fun PackageInstaller.abandonSessionQuietly(sessionId: Int) {
        try {
            abandonSession(sessionId)
        } catch (_: SecurityException) {
            // Already gone.
        }
    }

    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun PackageInfo.toIdentity(): ApkIdentity? {
        val name = packageName ?: return null
        val (current, history) = signerDigests()
        return ApkIdentity(
            packageName = name,
            versionCode = PackageInfoCompat.getLongVersionCode(this),
            signers = current,
            signingHistory = history
        )
    }

    private fun PackageInfo.signerDigests(): Pair<Set<String>, Set<String>> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = signingInfo ?: return emptySet<String>() to emptySet()
            val current = signing.apkContentsSigners.orEmpty().digests()
            val history = if (signing.hasMultipleSigners()) {
                current
            } else {
                signing.signingCertificateHistory.orEmpty().digests() + current
            }
            return current to history
        }
        @Suppress("DEPRECATION")
        val legacy = signatures.orEmpty().digests()
        return legacy to legacy
    }

    private fun Array<out Signature>.digests(): Set<String> =
        mapTo(mutableSetOf()) { ApkVerifier.sha256Hex(it.toByteArray()) }

    companion object {
        const val UPDATE_DIR = "updates"
        private const val SESSION_APK_NAME = "base.apk"
    }
}
