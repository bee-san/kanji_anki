package dev.bee.kanjianki.testing

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Properties

internal object GoldenFixtureResources {
    fun bytes(path: String): ByteArray {
        return requireNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Missing golden fixture resource: $path"
        }.use { it.readBytes() }
    }

    fun text(path: String): String {
        return bytes(path).toString(StandardCharsets.UTF_8).replace("\r\n", "\n")
    }

    fun properties(path: String): Properties {
        return Properties().apply {
            bytes(path).inputStream().use(::load)
        }
    }

    fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun sha256(text: String): String = sha256(text.toByteArray(StandardCharsets.UTF_8))
}
