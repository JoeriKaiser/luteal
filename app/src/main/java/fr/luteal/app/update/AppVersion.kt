package fr.luteal.app.update

/** SemVer `MAJOR.MINOR.PATCH[-pre][+build]`, with an optional leading `v` as in release tags. */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null
) : Comparable<AppVersion> {

    val isPreRelease: Boolean get() = preRelease != null

    override fun compareTo(other: AppVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        return comparePreRelease(preRelease, other.preRelease)
    }

    override fun toString(): String =
        "$major.$minor.$patch" + (preRelease?.let { "-$it" } ?: "")

    companion object {
        private val PATTERN = Regex(
            """^v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$"""
        )

        fun parse(raw: String): AppVersion? {
            val match = PATTERN.matchEntire(raw.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            return AppVersion(
                major = major.toIntOrNull() ?: return null,
                minor = minor.toIntOrNull() ?: return null,
                patch = patch.toIntOrNull() ?: return null,
                preRelease = match.groupValues[4].ifEmpty { null }
            )
        }

        // SemVer 2.0.0 section 11.
        private fun comparePreRelease(a: String?, b: String?): Int {
            if (a == b) return 0
            if (a == null) return 1
            if (b == null) return -1
            val left = a.split('.')
            val right = b.split('.')
            for (i in 0 until minOf(left.size, right.size)) {
                val l = left[i]
                val r = right[i]
                val ln = l.toLongOrNull()
                val rn = r.toLongOrNull()
                val cmp = when {
                    ln != null && rn != null -> ln.compareTo(rn)
                    ln != null -> -1
                    rn != null -> 1
                    else -> l.compareTo(r)
                }
                if (cmp != 0) return cmp
            }
            return left.size.compareTo(right.size)
        }
    }
}
