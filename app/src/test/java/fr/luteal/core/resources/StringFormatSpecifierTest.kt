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

    private val resourceFiles = listOf(
        File("src/main/res/values/strings.xml"),
        File("src/main/res/values-fr/strings.xml")
    )

    /** Matches a well-formed positional specifier, or a literal `%%`. */
    private val validSpecifier =
        Regex("""%(?:%|\d+\$[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z])""")

    /** Matches any `%` that begins a specifier. */
    private val anySpecifier = Regex("""%.?""")

    @Test
    fun `resource files are present`() {
        resourceFiles.forEach { file ->
            assertTrue("Missing resource file: ${file.absolutePath}", file.isFile)
        }
    }

    @Test
    fun `every format specifier is positional and has a conversion type`() {
        val offenders = mutableListOf<String>()

        resourceFiles.forEach { file ->
            val text = file.readText()
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
                            offenders += "${file.name}: $name -> \"$shown\" in \"$body\""
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
