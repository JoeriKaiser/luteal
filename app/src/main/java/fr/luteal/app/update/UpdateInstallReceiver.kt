package fr.luteal.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Install session results from [ApkInstaller]. */
@AndroidEntryPoint
class UpdateInstallReceiver : BroadcastReceiver() {

    @Inject
    lateinit var updateManager: UpdateManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // The Settings card starts it: Android blocks activity starts from
                // the background, and the user may have left during the download.
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) updateManager.onInstallFailed(null) else updateManager.onUserActionRequired(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> updateManager.onInstallSucceeded()
            PackageInstaller.STATUS_FAILURE_ABORTED -> updateManager.onInstallAborted()
            else -> updateManager.onInstallFailed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
            )
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "fr.luteal.app.action.UPDATE_INSTALL_STATUS"
    }
}
