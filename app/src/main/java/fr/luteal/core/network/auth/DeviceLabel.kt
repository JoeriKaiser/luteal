package fr.luteal.core.network.auth

import java.security.SecureRandom

/**
 * Human-readable device label sent at registration.
 *
 * Deliberately NOT [android.os.Build.MODEL]. The hardware model is a
 * fingerprinting signal that narrows an account to a device population and
 * serves no synchronisation purpose: the label exists only so a user can tell
 * their own devices apart in a list. A random pair drawn from a small French
 * word list carries enough distinguishability for that, and nothing else.
 *
 * Generated once per device and then persisted, so the label stays stable
 * across syncs.
 */
object DeviceLabel {

    private val ADJECTIVES = listOf(
        "calme", "clair", "discret", "doux", "lent", "leger",
        "lointain", "paisible", "serein", "silencieux", "sobre", "tranquille"
    )

    private val NOUNS = listOf(
        "aurore", "brume", "cirrus", "comete", "eclipse", "halo",
        "nuage", "orbite", "quartz", "reflet", "sillage", "zenith"
    )

    /** Number of distinct labels this generator can produce. */
    const val LABEL_SPACE: Int = 12 * 12

    fun random(random: SecureRandom = SecureRandom()): String {
        val adjective = ADJECTIVES[random.nextInt(ADJECTIVES.size)]
        val noun = NOUNS[random.nextInt(NOUNS.size)]
        return "$noun-$adjective"
    }
}
