package fr.luteal.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseParserTest {

    @Test
    fun `reads the fields used by the updater and ignores the rest`() {
        val release = GitHubReleaseParser.parse(SAMPLE_RELEASE)

        assertEquals("v2.0.0", release.tagName)
        assertEquals("https://github.com/JoeriKaiser/luteal/releases/tag/v2.0.0", release.htmlUrl)
        assertEquals("- Operator-blind encryption", release.body)
        assertFalse(release.prerelease)
        assertFalse(release.draft)
        assertEquals(2, release.assets.size)
        val apk = release.assets.first()
        assertEquals("luteal-v2.0.0.apk", apk.name)
        assertEquals(
            "https://github.com/JoeriKaiser/luteal/releases/download/v2.0.0/luteal-v2.0.0.apk",
            apk.downloadUrl
        )
        assertEquals(3756998L, apk.size)
        assertEquals("sha256:dc9db0cff835e0d4627fc67eb9e94553ef56440267096585f923aefb7fb172fa", apk.digest)
        assertNull(release.assets[1].digest)
    }

    @Test
    fun `null body, missing assets and flags default safely`() {
        val release = GitHubReleaseParser.parse(
            """{"tag_name":"v2.1.0","html_url":"https://example.invalid/r","body":null,"prerelease":true}"""
        )
        assertNull(release.body)
        assertTrue(release.prerelease)
        assertFalse(release.draft)
        assertTrue(release.assets.isEmpty())
    }

    @Test(expected = ReleaseParseException::class)
    fun `missing tag is rejected`() {
        GitHubReleaseParser.parse("""{"html_url":"https://example.invalid/r","assets":[]}""")
    }

    @Test(expected = ReleaseParseException::class)
    fun `non-object payload is rejected`() {
        GitHubReleaseParser.parse("""[{"tag_name":"v2.0.0"}]""")
    }

    @Test(expected = ReleaseParseException::class)
    fun `malformed json is rejected`() {
        GitHubReleaseParser.parse("""{"tag_name":""")
    }

    companion object {
        /** Trimmed from the real v2.0.0 response, plus an asset without a digest. */
        val SAMPLE_RELEASE = """
            {
              "url": "https://api.github.com/repos/JoeriKaiser/luteal/releases/1",
              "html_url": "https://github.com/JoeriKaiser/luteal/releases/tag/v2.0.0",
              "id": 1,
              "author": {"login": "JoeriKaiser", "id": 2},
              "tag_name": "v2.0.0",
              "name": "v2.0.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "luteal-v2.0.0.apk",
                  "content_type": "application/vnd.android.package-archive",
                  "size": 3756998,
                  "digest": "sha256:dc9db0cff835e0d4627fc67eb9e94553ef56440267096585f923aefb7fb172fa",
                  "browser_download_url": "https://github.com/JoeriKaiser/luteal/releases/download/v2.0.0/luteal-v2.0.0.apk"
                },
                {
                  "name": "notes.txt",
                  "size": 12,
                  "digest": null,
                  "browser_download_url": "https://github.com/JoeriKaiser/luteal/releases/download/v2.0.0/notes.txt"
                }
              ],
              "body": "- Operator-blind encryption"
            }
        """.trimIndent()
    }
}
