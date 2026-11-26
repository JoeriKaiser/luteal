package fr.luteal.app.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

data class GitHubRelease(
    val tagName: String,
    val htmlUrl: String,
    val body: String?,
    val prerelease: Boolean,
    val draft: Boolean,
    val assets: List<GitHubReleaseAsset>
)

data class GitHubReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    /** `sha256:<hex>`; missing on assets uploaded before GitHub added it. */
    val digest: String?
)

class ReleaseParseException(message: String) : Exception(message)

object GitHubReleaseParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): GitHubRelease {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: IllegalArgumentException) {
            throw ReleaseParseException("Release response is not a JSON object")
        }
        val assets = (root["assets"] as? JsonArray ?: JsonArray(emptyList()))
            .mapNotNull { element -> (element as? JsonObject)?.let(::parseAsset) }
        return GitHubRelease(
            tagName = root.requireString("tag_name"),
            htmlUrl = root.requireString("html_url"),
            body = root.optString("body"),
            prerelease = root.optBoolean("prerelease"),
            draft = root.optBoolean("draft"),
            assets = assets
        )
    }

    private fun parseAsset(obj: JsonObject): GitHubReleaseAsset? {
        val name = obj.optString("name") ?: return null
        val url = obj.optString("browser_download_url") ?: return null
        return GitHubReleaseAsset(
            name = name,
            downloadUrl = url,
            size = (obj["size"] as? JsonPrimitive)?.longOrNull ?: -1L,
            digest = obj.optString("digest")
        )
    }

    private fun JsonObject.requireString(key: String): String =
        optString(key) ?: throw ReleaseParseException("Release response has no $key")

    private fun JsonObject.optString(key: String): String? {
        val value: JsonElement = this[key] ?: return null
        val primitive = value as? JsonPrimitive ?: return null
        if (primitive is JsonNull || !primitive.isString) return null
        return primitive.content.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.optBoolean(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
}
