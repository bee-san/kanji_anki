package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.data.desktop.DesktopWindowBoundsPolicy
import dev.bee.kanjianki.platform.DeviceSettingKeys
import dev.bee.kanjianki.platform.DeviceSettingsStore
import java.awt.GraphicsEnvironment

/**
 * The I/O either side of [DesktopWindowBoundsPolicy]: reads the stored geometry
 * and the attached screens, and writes back geometry the policy has approved.
 *
 * Split from the policy so the decisions are testable without a display. Every
 * function here is a translation — settings keys to a [DesktopWindowBoundsPolicy.StoredWindow],
 * AWT screen devices to [DesktopWindowBoundsPolicy.ScreenRect] — and none of them
 * decides anything. The corresponding rule is that this file must stay free of
 * arithmetic on bounds; if a coordinate is being compared or adjusted, it belongs
 * in the policy where a test can reach it.
 */
internal object DesktopWindowGeometry {
    /**
     * The screens currently attached, primary first.
     *
     * Empty in a headless JVM rather than throwing, which is the case the policy's
     * no-screens branch exists for: the installed-image smoke gate runs with a
     * display, but a unit test or a misconfigured host may not have one, and
     * "cannot enumerate screens" must not be a startup failure.
     */
    fun attachedScreens(): List<DesktopWindowBoundsPolicy.ScreenRect> {
        if (GraphicsEnvironment.isHeadless()) return emptyList()
        return runCatching {
            val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
            val primary = environment.defaultScreenDevice
            // Primary first because the policy re-centres on `screens.first()` when
            // stored bounds are unreachable, and centring on an arbitrary monitor
            // would move the window somewhere the user did not leave it.
            val devices = listOf(primary) + environment.screenDevices.filterNot { it == primary }
            devices.mapNotNull { device ->
                val bounds = device.defaultConfiguration.bounds
                // A zero-size device is reported by some virtual displays; the policy's
                // `ScreenRect` rejects it, and one bad device must not lose the others.
                if (bounds.width <= 0 || bounds.height <= 0) {
                    null
                } else {
                    DesktopWindowBoundsPolicy.ScreenRect(
                        x = bounds.x,
                        y = bounds.y,
                        width = bounds.width,
                        height = bounds.height,
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * The geometry a closing window reported, or null when the toolkit has no
     * usable answer.
     *
     * `WindowState` reports `Dp` values that are unspecified before the window is
     * realised and can be `NaN` while it is being torn down — and a closing window is
     * exactly when this is read. Handing `NaN.toInt()` to the policy would persist
     * `0`, a placement that looks valid and puts the next launch in the corner, so
     * "the toolkit has no answer" is filtered here rather than encoded as a number.
     *
     * Takes raw floats rather than a `WindowState` so the filtering is reachable from
     * a test without a window: the values are what the state exposes, and the
     * decisions made about them are the part worth pinning.
     */
    fun reportedGeometry(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        positionSpecified: Boolean,
    ): DesktopWindowBoundsPolicy.WindowBounds? {
        if (!positionSpecified) return null
        val values = listOf(x, y, width, height)
        if (values.any { it.isNaN() || it.isInfinite() }) return null
        return DesktopWindowBoundsPolicy.WindowBounds(
            x = x.toInt(),
            y = y.toInt(),
            width = width.toInt(),
            height = height.toInt(),
        )
    }

    /** Reads the stored geometry. Absent keys stay null so the policy can default. */
    fun storedWindow(settings: DeviceSettingsStore): DesktopWindowBoundsPolicy.StoredWindow {
        val snapshot = settings.snapshot()
        return DesktopWindowBoundsPolicy.StoredWindow(
            x = snapshot.read(DeviceSettingKeys.windowX),
            y = snapshot.read(DeviceSettingKeys.windowY),
            width = snapshot.read(DeviceSettingKeys.windowWidth),
            height = snapshot.read(DeviceSettingKeys.windowHeight),
            maximized = snapshot.read(DeviceSettingKeys.windowMaximized) ?: false,
        )
    }

    /**
     * Persists geometry only if the policy accepts it, and returns whether it did.
     *
     * A rejected capture leaves the previous stored geometry untouched rather than
     * clearing it. That is the point of validating before writing: the last known
     * good placement is more useful than no placement, and a window that ended a
     * session in a degenerate state is exactly when the old value is worth keeping.
     */
    fun persist(
        settings: DeviceSettingsStore,
        bounds: DesktopWindowBoundsPolicy.WindowBounds,
        maximized: Boolean,
        screens: List<DesktopWindowBoundsPolicy.ScreenRect>,
    ): Boolean {
        val approved = DesktopWindowBoundsPolicy.capture(bounds, maximized, screens)
            ?: return false
        settings.edit {
            approved.x?.let { put(DeviceSettingKeys.windowX, it) }
            approved.y?.let { put(DeviceSettingKeys.windowY, it) }
            approved.width?.let { put(DeviceSettingKeys.windowWidth, it) }
            approved.height?.let { put(DeviceSettingKeys.windowHeight, it) }
            put(DeviceSettingKeys.windowMaximized, approved.maximized)
        }
        return true
    }
}
