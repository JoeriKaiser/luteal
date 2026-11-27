package fr.luteal.app.update

/**
 * F-Droid ships the same signed APK as GitHub, so the updater is hidden at run
 * time for store installs instead of with a build flavor.
 */
object InstallerPolicy {

    val STORE_INSTALLERS: Set<String> = setOf(
        "org.fdroid.fdroid",            // F-Droid
        "org.fdroid.fdroid.privileged", // F-Droid Privileged Extension
        "org.fdroid.basic",             // F-Droid Basic
        "com.looker.droidify",          // Droid-ify
        "com.machiav3lli.fdroid",       // Neo Store
        "com.aurora.adroid",            // Aurora Droid
        "com.android.vending"           // Google Play
    )

    fun allowsSelfUpdate(installerPackageName: String?): Boolean =
        installerPackageName == null || installerPackageName !in STORE_INSTALLERS
}
