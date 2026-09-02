package fr.luteal.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64

/**
 * In-memory holder for pending backup payloads transferred between internal activities.
 * Prevents unauthenticated external backup injection via public intent extras.
 */
object PendingBackupStore {
    private var backupJson: String? = null

    @Synchronized
    fun set(json: String) {
        backupJson = json
    }

    @Synchronized
    fun consume(): String? {
        val j = backupJson
        backupJson = null
        return j
    }
}

/**
 * Forwards backup JSON from the adb shell to the normal in-app import flow.
 * The manifest protects this bridge with the platform DUMP permission.
 */
class AdbBackupImportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val backupJson = intent.getStringExtra(MainActivity.EXTRA_IMPORT_JSON_BASE64)
            ?.let { encoded ->
                runCatching {
                    Base64.decode(encoded, Base64.DEFAULT).toString(Charsets.UTF_8)
                }.getOrNull()
            }
        if (!backupJson.isNullOrBlank()) {
            PendingBackupStore.set(backupJson)
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_IMPORT_BACKUP
                    addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }
            )
        }
        finish()
    }
}
