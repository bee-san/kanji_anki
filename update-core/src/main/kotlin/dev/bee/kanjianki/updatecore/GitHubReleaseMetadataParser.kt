package dev.bee.kanjianki.updatecore

object GitHubReleaseMetadataParser {
    private const val UNICODE_ESCAPE_HEX_LENGTH = 4
    private const val KEY_ASSETS = "assets"
    private const val KEY_BODY = "body"
    private const val KEY_BROWSER_DOWNLOAD_URL = "browser_download_url"
    private const val KEY_HTML_URL = "html_url"
    private const val KEY_NAME = "name"
    private const val KEY_TAG_NAME = "tag_name"

    @JvmStatic
    fun parseLatest(json: String?): GitHubReleaseMetadata {
        val safeJson = json.orEmpty()
        val tag = stringValue(safeJson, KEY_TAG_NAME)
        val html = stringValue(safeJson, KEY_HTML_URL)
        val body = stringValue(safeJson, KEY_BODY).ifEmpty { null }
        val assets = ArrayList<GitHubReleaseMetadata.ReleaseAsset>()
        val assetsJson = arrayValue(safeJson, KEY_ASSETS)
        for (assetJson in objectValues(assetsJson)) {
            val name = stringValue(assetJson, KEY_NAME)
            val url = stringValue(assetJson, KEY_BROWSER_DOWNLOAD_URL)
            if (name.isNotEmpty() && url.isNotEmpty()) {
                assets.add(GitHubReleaseMetadata.ReleaseAsset(name, url))
            }
        }
        return GitHubReleaseMetadata(tag, html, assets, body)
    }

    @JvmStatic
    fun objectValues(arrayJson: String): List<String> {
        val out = ArrayList<String>()
        if (arrayJson.isEmpty()) {
            return out
        }
        var depth = 0
        var objectStart = -1
        var index = 0
        while (index < arrayJson.length) {
            when (arrayJson[index]) {
                '"' -> index = readString(arrayJson, index).endIndex + 1
                '{' -> {
                    if (depth == 0) {
                        objectStart = index
                    }
                    depth++
                    index++
                }
                '}' -> {
                    depth--
                    if (depth == 0) {
                        out.add(arrayJson.substring(objectStart, index + 1))
                        objectStart = -1
                    }
                    index++
                }
                else -> index++
            }
        }
        return out
    }

    private fun stringValue(json: String, key: String): String {
        val colon = findKeyColon(json, key)
        if (colon < 0) {
            return ""
        }
        val valueIndex = nextNonWhitespace(json, colon + 1)
        if (valueIndex < 0 || json[valueIndex] != '"') {
            return ""
        }
        return readString(json, valueIndex).value
    }

    private fun arrayValue(json: String, key: String): String {
        val colon = findKeyColon(json, key)
        if (colon < 0) {
            return ""
        }
        val start = nextNonWhitespace(json, colon + 1)
        if (start < 0 || json[start] != '[') {
            return ""
        }
        var depth = 0
        var index = start
        while (index < json.length) {
            when (json[index]) {
                '"' -> index = readString(json, index).endIndex + 1
                '[' -> {
                    depth++
                    index++
                }
                ']' -> {
                    depth--
                    if (depth == 0) {
                        return json.substring(start, index + 1)
                    }
                    index++
                }
                else -> index++
            }
        }
        return ""
    }

    private fun findKeyColon(json: String, key: String): Int {
        var index = 0
        while (index < json.length) {
            if (json[index] == '"') {
                val parsed = readString(json, index)
                if (key == parsed.value) {
                    val colon = nextColon(json, parsed.endIndex + 1)
                    if (colon >= 0) {
                        return colon
                    }
                }
                index = parsed.endIndex + 1
            } else {
                index++
            }
        }
        return -1
    }

    private fun nextColon(json: String, start: Int): Int {
        for (index in start until json.length) {
            val c = json[index]
            if (c == ':') {
                return index
            }
            if (!c.isWhitespace()) {
                return -1
            }
        }
        return -1
    }

    private fun nextNonWhitespace(json: String, start: Int): Int {
        for (index in maxOf(start, 0) until json.length) {
            if (!json[index].isWhitespace()) {
                return index
            }
        }
        return -1
    }

    private fun readString(json: String, quoteIndex: Int): ParsedString {
        val out = StringBuilder()
        var index = quoteIndex + 1
        while (index < json.length) {
            val c = json[index]
            if (c == '"') {
                return ParsedString(out.toString(), index)
            }
            if (c != '\\' || index + 1 >= json.length) {
                out.append(c)
                index++
            } else {
                val escape = readEscape(json, index + 1)
                out.append(escape.value)
                index = escape.nextIndex
            }
        }
        return ParsedString(out.toString(), json.length - 1)
    }

    private fun readEscape(json: String, escapeIndex: Int): ParsedEscape {
        return when (val escaped = json[escapeIndex]) {
            '"', '\\', '/' -> ParsedEscape(escaped.toString(), escapeIndex + 1)
            'b' -> ParsedEscape("\b", escapeIndex + 1)
            'f' -> ParsedEscape("\u000C", escapeIndex + 1)
            'n' -> ParsedEscape("\n", escapeIndex + 1)
            'r' -> ParsedEscape("\r", escapeIndex + 1)
            't' -> ParsedEscape("\t", escapeIndex + 1)
            'u' -> readUnicodeEscape(json, escapeIndex)
            else -> ParsedEscape(escaped.toString(), escapeIndex + 1)
        }
    }

    private fun readUnicodeEscape(json: String, escapeIndex: Int): ParsedEscape {
        if (escapeIndex + UNICODE_ESCAPE_HEX_LENGTH >= json.length) {
            return ParsedEscape("\\u", escapeIndex + 1)
        }
        val hex = json.substring(escapeIndex + 1, escapeIndex + 1 + UNICODE_ESCAPE_HEX_LENGTH)
        return try {
            val unicode = hex.toInt(16).toChar()
            ParsedEscape(unicode.toString(), escapeIndex + 1 + UNICODE_ESCAPE_HEX_LENGTH)
        } catch (_: NumberFormatException) {
            ParsedEscape("\\u$hex", escapeIndex + 1 + UNICODE_ESCAPE_HEX_LENGTH)
        }
    }

    private class ParsedEscape(
        val value: String,
        val nextIndex: Int,
    )

    private class ParsedString(
        val value: String,
        val endIndex: Int,
    )
}
