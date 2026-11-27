package fr.luteal.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallerPolicyTest {

    @Test
    fun `store installs keep their own update path`() {
        listOf(
            "org.fdroid.fdroid",
            "org.fdroid.basic",
            "com.looker.droidify",
            "com.machiav3lli.fdroid",
            "com.aurora.adroid",
            "com.android.vending"
        ).forEach { assertFalse(it, InstallerPolicy.allowsSelfUpdate(it)) }
    }

    @Test
    fun `sideloads, Obtainium, adb and self-updates show the updater`() {
        listOf(
            null,
            "dev.imranr.obtainium",
            "com.google.android.packageinstaller",
            "com.android.shell",
            "fr.luteal.app"
        ).forEach { assertTrue(it.toString(), InstallerPolicy.allowsSelfUpdate(it)) }
    }
}
