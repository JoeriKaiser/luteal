package fr.luteal.app.update

data class UpdateInfo(
    val version: AppVersion,
    val tagName: String,
    val releaseUrl: String,
    val changelog: String?,
    val apk: GitHubReleaseAsset?,
    val apkSha256: String?
) {
    val canInstall: Boolean get() = apk != null && apkSha256 != null
}

sealed interface UpdateCheckOutcome {
    data class UpToDate(val installed: AppVersion) : UpdateCheckOutcome
    data class Available(val info: UpdateInfo) : UpdateCheckOutcome
}

class UnusableReleaseException(message: String) : Exception(message)

/** Only stable releases are offered, the same ones F-Droid builds. */
object UpdateEvaluator {

    const val CHANGELOG_MAX_CHARS = 600

    fun evaluate(release: GitHubRelease, installedVersionName: String): UpdateCheckOutcome {
        val installed = AppVersion.parse(installedVersionName)
            ?: throw UnusableReleaseException("Installed version '$installedVersionName' is not MAJOR.MINOR.PATCH")
        if (release.draft || release.prerelease) return UpdateCheckOutcome.UpToDate(installed)

        val latest = AppVersion.parse(release.tagName)
            ?: throw UnusableReleaseException("Release tag '${release.tagName}' is not vMAJOR.MINOR.PATCH")
        if (latest.isPreRelease || latest <= installed) return UpdateCheckOutcome.UpToDate(installed)

        val apk = release.assets.firstOrNull { it.name == expectedApkName(latest) }
        return UpdateCheckOutcome.Available(
            UpdateInfo(
                version = latest,
                tagName = release.tagName,
                releaseUrl = release.htmlUrl,
                changelog = trimChangelog(release.body),
                apk = apk,
                apkSha256 = apk?.digest?.let(::parseSha256Digest)
            )
        )
    }

    // Matches the asset name in .github/workflows/release.yml.
    fun expectedApkName(version: AppVersion): String = "luteal-v$version.apk"

    fun parseSha256Digest(digest: String): String? {
        val parts = digest.trim().split(':', limit = 2)
        if (parts.size != 2 || !parts[0].equals("sha256", ignoreCase = true)) return null
        val hex = parts[1].lowercase()
        return hex.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    fun trimChangelog(body: String?, maxChars: Int = CHANGELOG_MAX_CHARS): String? {
        val text = body?.let(::plainChangelog)?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (text.length <= maxChars) return text
        val cut = text.substring(0, maxChars)
        val lastBreak = cut.lastIndexOfAny(charArrayOf(' ', '\n'))
        val head = if (lastBreak > maxChars / 2) cut.substring(0, lastBreak) else cut
        return head.trimEnd() + "…"
    }

    /**
     * Release notes are GitHub-generated Markdown. The card shows plain text,
     * so drop the compare link and the heading, bold and list markers.
     */
    fun plainChangelog(body: String): String = body.replace("\r\n", "\n")
        .lines()
        .filterNot { it.trimStart().startsWith("**Full Changelog**") }
        .joinToString("\n") { line ->
            line.trimEnd()
                .replace(HEADING, "")
                .replace(LIST_ITEM, "• ")
                .replace("**", "")
        }
        .replace(BLANK_RUNS, "\n\n")

    private val HEADING = Regex("^#{1,6}\\s+")
    private val LIST_ITEM = Regex("^\\s*[*-]\\s+")
    private val BLANK_RUNS = Regex("\n{3,}")
}
