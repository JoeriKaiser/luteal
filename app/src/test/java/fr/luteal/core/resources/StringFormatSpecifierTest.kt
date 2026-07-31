package fr.luteal.core.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards every translatable string against malformed format specifiers.
 *
 * Android lint does not flag a specifier such as `%1` (an argument index with
 * no conversion type). It compiles, ships, and then throws
 * `UnknownFormatConversionException` the first time the string is formatted
 * with an argument. This test parses the resource files directly and rejects
 * anything `java.util.Formatter` cannot parse.
 */
class StringFormatSpecifierTest {

    /** One `values*` folder that carries strings, named for the message text. */
    private data class Translation(val folder: String, val file: File)

    /**
     * Every translation, discovered rather than listed, so a new locale is
     * covered the moment its folder exists. `values/` holds French (the
     * fallback for untranslated locales); `values-en/` holds English.
     */
    private val translations = File("src/main/res")
        .listFiles { file -> file.isDirectory && file.name.startsWith("values") }
        .orEmpty()
        .map { Translation(it.name, File(it, "strings.xml")) }
        .filter { it.file.isFile }
        .sortedBy { it.folder }

    /** Matches a well-formed positional specifier, or a literal `%%`. */
    private val validSpecifier =
        Regex("""%(?:%|\d+\$[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z])""")

    /** Matches any `%` that begins a specifier. */
    private val anySpecifier = Regex("""%.?""")

    /** Resource names declared in one file, `<string>` and `<plurals>` alike. */
    private fun names(file: File): Set<String> =
        Regex("""<(?:string|plurals)\s+name="([^"]+)"""")
            .findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun `default and english resource files are present`() {
        val found = translations.map { it.folder }
        assertTrue("Missing values/strings.xml, found $found", "values" in found)
        assertTrue("Missing values-en/strings.xml, found $found", "values-en" in found)
    }

    @Test
    fun `every translation covers the same resource names as the default`() {
        val expected = names(File("src/main/res/values/strings.xml"))

        translations
            .filter { it.folder != "values" }
            .forEach { translation ->
                val actual = names(translation.file)
                assertEquals(
                    "${translation.folder} is missing names present in values/",
                    emptySet<String>(),
                    expected - actual
                )
                assertEquals(
                    "${translation.folder} declares names absent from values/",
                    emptySet<String>(),
                    actual - expected
                )
            }
    }

    @Test
    fun `every format specifier is positional and has a conversion type`() {
        val offenders = mutableListOf<String>()

        translations.forEach { translation ->
            val text = translation.file.readText()
            Regex("""<(string|item)[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(text)
                .forEach { match ->
                    val name = Regex("""name="([^"]+)"""")
                        .find(match.value)?.groupValues?.get(1) ?: "(item)"
                    val body = match.groupValues[2]

                    var index = 0
                    while (index < body.length) {
                        if (body[index] != '%') {
                            index++
                            continue
                        }
                        val valid = validSpecifier.matchAt(body, index)
                        if (valid == null) {
                            val shown = anySpecifier.matchAt(body, index)?.value ?: "%"
                            offenders += "${translation.folder}: $name -> \"$shown\" in \"$body\""
                            index++
                        } else {
                            index += valid.value.length
                        }
                    }
                }
        }

        assertEquals(
            "Malformed format specifiers found:\n${offenders.joinToString("\n")}",
            emptyList<String>(),
            offenders
        )
    }

    @Test
    fun `malformed specifier would actually be rejected`() {
        // Documents the bug class this test exists to prevent.
        val prefix = "Jour du cycle : "
        assertTrue(validSpecifier.matchAt(prefix + "%1", prefix.length) == null)
        assertTrue(validSpecifier.matchAt(prefix + "%1\$d", prefix.length) != null)
    }
}
