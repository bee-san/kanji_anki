package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.DeviceSettingKey
import dev.bee.kanjianki.platform.DeviceSettingValueType
import dev.bee.kanjianki.platform.DeviceSettingsEditor
import dev.bee.kanjianki.platform.DeviceSettingsReader
import dev.bee.kanjianki.platform.DeviceSettingsStore
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties
import java.util.SortedMap
import java.util.TreeMap

/**
 * Desktop's [DeviceSettingsStore]: the device-local settings that must never enter
 * a portable backup, kept in one owner-only text file beside the profile.
 *
 * A file rather than the profile database, for the reason the port exists at all:
 * these keys are host-specific (window geometry, tray/login, provider endpoint and
 * secret *references*, reminder bookkeeping). Storing them in the database would
 * publish them into every backup and carry one machine's window position and
 * provider binding onto another. `DeviceSettingKeys.portableExclusionStorageNames`
 * is the same list from the other direction.
 *
 * **Durability.** [edit] applies the whole block to an in-memory copy, then
 * replaces the file by write-temp / fsync / `ATOMIC_MOVE` / fsync-directory, so a
 * crash mid-write leaves the previous complete file rather than a truncated one.
 * `SharedPreferences.commit()` gives the Android adapter the same all-or-nothing
 * guarantee; matching it here is what lets shared code treat one `edit` block as
 * one durable decision on both hosts.
 *
 * **Fail-fast on durability loss.** A failed write throws and *latches*: every
 * later operation throws too, until the process restarts. This mirrors
 * `AndroidDeviceSettingsStore` deliberately. The alternative — carry on with
 * in-memory values the disk does not have — means a user who turns off automatic
 * sync sees it turn back on at next launch, and no error was ever shown. Silently
 * diverging from disk is worse than a loud failure.
 *
 * The file is `0600` on POSIX hosts. On Windows, isolation comes from the
 * per-user profile root under `%LOCALAPPDATA%`, the same reasoning (and the same
 * limitation) as the profile provisioner's.
 */
class DesktopDeviceSettingsStore private constructor(
    private val file: Path,
    private var values: SortedMap<String, String>,
) : DeviceSettingsStore {
    private var durabilityFailure: IllegalStateException? = null

    override fun contains(key: DeviceSettingKey<*>): Boolean = synchronized(this) {
        checkHealthy()
        values.containsKey(key.storageName)
    }

    override fun <T : Any> read(key: DeviceSettingKey<T>): T? = synchronized(this) {
        checkHealthy()
        readValue(values, key)
    }

    override fun snapshot(): DeviceSettingsReader = synchronized(this) {
        checkHealthy()
        // A copy, not a view: the caller's point-in-time guarantee is the whole
        // point of snapshot(), and a concurrent edit() must not mutate it under
        // them mid-decision.
        MapReader(TreeMap(values))
    }

    override fun edit(block: DeviceSettingsEditor.() -> Unit) {
        synchronized(this) {
            checkHealthy()
            val editor = MapEditor(TreeMap(values))
            editor.block()
            if (!editor.changed) return
            try {
                writeAtomically(file, editor.values)
            } catch (error: IOException) {
                val failure = IllegalStateException(
                    "Unable to persist device settings; restart required",
                    error,
                )
                durabilityFailure = failure
                throw failure
            }
            // Published only after the file is durable, so an observer that reads
            // back during the same edit can never see a value the disk lost.
            values = editor.values
        }
    }

    private fun checkHealthy() {
        durabilityFailure?.let { failure ->
            throw IllegalStateException(
                "Device settings durability previously failed; restart required",
                failure,
            )
        }
    }

    private class MapReader(private val values: Map<String, String>) : DeviceSettingsReader {
        override fun contains(key: DeviceSettingKey<*>): Boolean =
            values.containsKey(key.storageName)

        override fun <T : Any> read(key: DeviceSettingKey<T>): T? = readValue(values, key)
    }

    private class MapEditor(val values: SortedMap<String, String>) : DeviceSettingsEditor {
        var changed = false
            private set

        override fun contains(key: DeviceSettingKey<*>): Boolean =
            values.containsKey(key.storageName)

        override fun <T : Any> read(key: DeviceSettingKey<T>): T? = readValue(values, key)

        override fun <T : Any> put(key: DeviceSettingKey<T>, value: T) {
            val matches = when (key.valueType) {
                DeviceSettingValueType.BOOLEAN -> value is Boolean
                DeviceSettingValueType.INT -> value is Int
                DeviceSettingValueType.LONG -> value is Long
                DeviceSettingValueType.STRING -> value is String
            }
            require(matches) {
                "Value for ${key.storageName} does not match ${key.valueType}"
            }
            values[key.storageName] = value.toString()
            changed = true
        }

        override fun remove(key: DeviceSettingKey<*>) {
            values.remove(key.storageName)
            changed = true
        }
    }

    companion object {
        const val FILE_NAME: String = "device-settings.properties"

        private const val HEADER =
            "Kani device-local settings. Host-specific; never included in a portable backup."

        private val FILE_0600 = PosixFilePermissions.fromString("rw-------")

        /**
         * Opens the store at [file], reading any existing content.
         *
         * An unreadable or malformed file is treated as empty rather than fatal:
         * every key here has a product default, and refusing to start because a
         * window position could not be parsed would be a worse outcome than
         * opening at the default size. A *write* failure is fatal, because there
         * the user made a choice and it was lost.
         */
        fun open(file: Path): DesktopDeviceSettingsStore =
            DesktopDeviceSettingsStore(file, readOrEmpty(file))

        private fun readOrEmpty(file: Path): SortedMap<String, String> {
            val loaded = TreeMap<String, String>()
            if (!Files.isRegularFile(file)) return loaded
            try {
                val properties = Properties()
                Files.newBufferedReader(file, StandardCharsets.UTF_8).use(properties::load)
                properties.stringPropertyNames().forEach { name ->
                    properties.getProperty(name)?.let { loaded[name] = it }
                }
            } catch (_: IOException) {
                return TreeMap()
            } catch (_: IllegalArgumentException) {
                // Properties.load rejects a malformed unicode escape this way.
                return TreeMap()
            }
            return loaded
        }

        private fun writeAtomically(file: Path, values: Map<String, String>) {
            val directory = file.toAbsolutePath().parent
                ?: throw IOException("device settings file has no parent directory")
            Files.createDirectories(directory)
            val temporary = Files.createTempFile(directory, "device-settings", ".partial")
            try {
                hardenFile(temporary)
                // Properties.store writes a timestamp comment, which would make the
                // file differ byte-for-byte on every save even when nothing changed;
                // writing the sorted pairs directly keeps saves comparable and the
                // file readable by a user inspecting their own config.
                val text = buildString {
                    append("# ").append(HEADER).append('\n')
                    values.forEach { (name, value) ->
                        append(escape(name)).append('=').append(escape(value)).append('\n')
                    }
                }
                Files.write(
                    temporary,
                    text.toByteArray(StandardCharsets.UTF_8),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                )
                fsync(temporary, directory = false)
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
                // The rename itself must reach the disk, or a crash can leave the
                // directory entry pointing at the pre-write file even though the new
                // content was fsynced.
                fsync(directory, directory = true)
            } finally {
                Files.deleteIfExists(temporary)
            }
        }

        private fun fsync(path: Path, directory: Boolean) {
            val options = if (directory) {
                StandardOpenOption.READ
            } else {
                StandardOpenOption.WRITE
            }
            try {
                FileChannel.open(path, options).use { channel -> channel.force(true) }
            } catch (error: IOException) {
                // Directory fsync is unsupported on some filesystems (notably on
                // Windows, where opening a directory as a channel fails outright).
                // The ATOMIC_MOVE still gives all-or-nothing content; only the
                // ordering guarantee against a power loss is weaker, and failing the
                // user's save over it would be the worse trade. A *file* flush
                // failure is not excusable that way: it means the bytes may not be
                // on disk, which is precisely the durability claim edit() makes.
                if (!directory) throw error
            }
        }

        private fun hardenFile(file: Path) {
            try {
                if (file.fileSystem.supportedFileAttributeViews().contains("posix")) {
                    Files.setPosixFilePermissions(file, FILE_0600)
                }
            } catch (_: IOException) {
                // Best effort, as in the profile provisioner: a filesystem that
                // cannot express owner-only permissions relies on the per-user
                // profile root for isolation.
            }
        }

        /**
         * Escapes the characters `Properties.load` treats specially, so a value
         * containing `=`, `:`, a leading space, or a newline round-trips. Reading
         * uses `Properties.load`, so writing must speak the same dialect.
         */
        private fun escape(value: String): String = buildString {
            value.forEachIndexed { index, character ->
                when {
                    character == '\\' -> append("\\\\")
                    character == '\n' -> append("\\n")
                    character == '\r' -> append("\\r")
                    character == '\t' -> append("\\t")
                    character == '=' || character == ':' -> append('\\').append(character)
                    character == '#' || character == '!' -> append('\\').append(character)
                    character == ' ' && index == 0 -> append("\\ ")
                    else -> append(character)
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T : Any> readValue(
            values: Map<String, String>,
            key: DeviceSettingKey<T>,
        ): T? {
            val stored = values[key.storageName] ?: return null
            // A stored value whose text does not parse as the key's declared type is
            // absent, not an error: same fail-open choice the Android adapter makes
            // for a type mismatch, so a hand-edited or downgrade-written file falls
            // back to the product default instead of crashing at startup.
            val parsed: Any? = when (key.valueType) {
                DeviceSettingValueType.BOOLEAN -> when (stored) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }

                DeviceSettingValueType.INT -> stored.toIntOrNull()
                DeviceSettingValueType.LONG -> stored.toLongOrNull()
                DeviceSettingValueType.STRING -> stored
            }
            return parsed as T?
        }
    }
}
