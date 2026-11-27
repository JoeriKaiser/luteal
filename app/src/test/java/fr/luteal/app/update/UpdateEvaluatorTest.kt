package fr.luteal.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateEvaluatorTest {

    private val digest = "sha256:" + "ab".repeat(32)

    private fun release(
        tag: String = "v2.1.0",
        prerelease: Boolean = false,
        draft: Boolean = false,
        body: String? = "Notes",
        assets: List<GitHubReleaseAsset> = listOf(apkAsset("luteal-v2.1.0.apk", digest))
    ) = GitHubRelease(
        tagName = tag,
        htmlUrl = "https://github.com/JoeriKaiser/luteal/releases/tag/$tag",
        body = body,
        prerelease = prerelease,
        draft = draft,
        assets = assets
    )

    private fun apkAsset(name: String, digest: String?) =
        GitHubReleaseAsset(name, "https://github.com/dl/$name", 100L, digest)

    @Test
    fun `newer stable release is offered with its apk and digest`() {
        val outcome = UpdateEvaluator.evaluate(release(), "2.0.0") as UpdateCheckOutcome.Available
        val info = outcome.info
        assertEquals(AppVersion(2, 1, 0), info.version)
        assertEquals("luteal-v2.1.0.apk", info.apk?.name)
        assertEquals("ab".repeat(32), info.apkSha256)
        assertEquals("Notes", info.changelog)
        assertTrue(info.canInstall)
    }

    @Test
    fun `same or older release is up to date`() {
        assertTrue(UpdateEvaluator.evaluate(release(tag = "v2.0.0"), "2.0.0") is UpdateCheckOutcome.UpToDate)
        assertTrue(UpdateEvaluator.evaluate(release(tag = "v1.9.9"), "2.0.0") is UpdateCheckOutcome.UpToDate)
    }

    @Test
    fun `prereleases, drafts and pre-release tags are ignored`() {
        assertTrue(UpdateEvaluator.evaluate(release(prerelease = true), "2.0.0") is UpdateCheckOutcome.UpToDate)
        assertTrue(UpdateEvaluator.evaluate(release(draft = true), "2.0.0") is UpdateCheckOutcome.UpToDate)
        assertTrue(UpdateEvaluator.evaluate(release(tag = "v2.1.0-rc.1"), "2.0.0") is UpdateCheckOutcome.UpToDate)
    }

    @Test
    fun `only the exact apk name is picked`() {
        val assets = listOf(
            apkAsset("luteal-v2.1.0.apk.sig", digest),
            apkAsset("luteal-v2.0.0.apk", digest),
            apkAsset("luteal-v2.1.0.apk", digest)
        )
        val info = (UpdateEvaluator.evaluate(release(assets = assets), "2.0.0") as UpdateCheckOutcome.Available).info
        assertEquals("luteal-v2.1.0.apk", info.apk?.name)
    }

    @Test
    fun `release without digest or apk is offered but cannot be installed in-app`() {
        val noDigest = (UpdateEvaluator.evaluate(
            release(assets = listOf(apkAsset("luteal-v2.1.0.apk", null))), "2.0.0"
        ) as UpdateCheckOutcome.Available).info
        assertNull(noDigest.apkSha256)
        assertFalse(noDigest.canInstall)

        val noApk = (UpdateEvaluator.evaluate(release(assets = emptyList()), "2.0.0") as UpdateCheckOutcome.Available).info
        assertNull(noApk.apk)
        assertFalse(noApk.canInstall)
        assertEquals("https://github.com/JoeriKaiser/luteal/releases/tag/v2.1.0", noApk.releaseUrl)
    }

    @Test(expected = UnusableReleaseException::class)
    fun `unparseable tag is an error, not an update`() {
        UpdateEvaluator.evaluate(release(tag = "latest"), "2.0.0")
    }

    @Test(expected = UnusableReleaseException::class)
    fun `unparseable installed version is an error`() {
        UpdateEvaluator.evaluate(release(), "2.0")
    }

    @Test
    fun `digest parsing accepts only sha256 with 64 hex chars`() {
        val hex = "0123456789abcdef".repeat(4)
        assertEquals(hex, UpdateEvaluator.parseSha256Digest("sha256:$hex"))
        assertEquals(hex, UpdateEvaluator.parseSha256Digest("SHA256:${hex.uppercase()}"))
        assertNull(UpdateEvaluator.parseSha256Digest("sha512:$hex"))
        assertNull(UpdateEvaluator.parseSha256Digest("sha256:${hex.dropLast(1)}"))
        assertNull(UpdateEvaluator.parseSha256Digest("sha256:${hex.dropLast(1)}g"))
        assertNull(UpdateEvaluator.parseSha256Digest(hex))
    }

    @Test
    fun `changelog is trimmed on a word boundary`() {
        assertNull(UpdateEvaluator.trimChangelog("  \r\n "))
        assertNull(UpdateEvaluator.trimChangelog(null))
        assertEquals("a\nb", UpdateEvaluator.trimChangelog("a\r\nb\n"))

        val long = "word ".repeat(200)
        val trimmed = UpdateEvaluator.trimChangelog(long, maxChars = 52)!!
        assertTrue(trimmed.endsWith("word…"))
        assertTrue(trimmed.length <= 53)
    }

    @Test
    fun `GitHub release notes are shown as plain text`() {
        val body = "## What's Changed\r\n* feat: something by @dev in https://github.com/o/r/pull/1\r\n\r\n\r\n" +
            "**Full Changelog**: https://github.com/o/r/compare/v1.0.0...v1.1.0"
        assertEquals(
            "What's Changed\n• feat: something by @dev in https://github.com/o/r/pull/1",
            UpdateEvaluator.trimChangelog(body)
        )
    }
}
