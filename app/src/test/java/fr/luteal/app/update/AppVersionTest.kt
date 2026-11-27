package fr.luteal.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parses tags and version names with or without the v prefix`() {
        assertEquals(AppVersion(2, 0, 0), AppVersion.parse("v2.0.0"))
        assertEquals(AppVersion(2, 10, 3), AppVersion.parse("2.10.3"))
        assertEquals(AppVersion(1, 2, 3, "rc.1"), AppVersion.parse("v1.2.3-rc.1"))
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("1.2.3+build.7"))
    }

    @Test
    fun `rejects anything that is not MAJOR MINOR PATCH`() {
        listOf("", "v2", "2.0", "2.0.0.1", "latest", "v02.0.0", "2.0.x", "vv2.0.0").forEach {
            assertNull(it, AppVersion.parse(it))
        }
    }

    @Test
    fun `orders numerically, not lexically`() {
        assertTrue(AppVersion.parse("2.10.0")!! > AppVersion.parse("2.9.9")!!)
        assertTrue(AppVersion.parse("10.0.0")!! > AppVersion.parse("9.99.99")!!)
        assertTrue(AppVersion.parse("2.0.1")!! > AppVersion.parse("2.0.0")!!)
        assertEquals(0, AppVersion.parse("v2.0.0")!!.compareTo(AppVersion.parse("2.0.0+meta")!!))
    }

    @Test
    fun `a release sorts after its pre-releases`() {
        val rc = AppVersion.parse("2.1.0-rc.2")!!
        assertTrue(AppVersion.parse("2.1.0")!! > rc)
        assertTrue(rc > AppVersion.parse("2.1.0-rc.1")!!)
        assertTrue(AppVersion.parse("2.1.0-rc.10")!! > rc)
        assertTrue(AppVersion.parse("2.1.0-beta")!! > AppVersion.parse("2.1.0-alpha")!!)
        assertTrue(AppVersion.parse("2.1.0-alpha.1")!! > AppVersion.parse("2.1.0-alpha")!!)
        assertTrue(rc > AppVersion.parse("2.0.9")!!)
    }

    @Test
    fun `prints without the v prefix`() {
        assertEquals("2.0.0", AppVersion.parse("v2.0.0").toString())
        assertEquals("1.2.3-rc.1", AppVersion.parse("v1.2.3-rc.1").toString())
    }
}
