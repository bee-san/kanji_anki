package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.ClipboardService

/**
 * Desktop's [ClipboardService].
 *
 * The AWT/Compose clipboard is injected rather than reached for directly so this
 * stays testable headlessly — a `Toolkit.getDefaultToolkit()` call throws on a
 * host with no display, which is every CI runner in this repo's desktop gate.
 *
 * The contract's `label` is dropped, because desktop clipboards have no equivalent:
 * on Android it is the user-visible description in the clipboard toast. Keeping the
 * parameter in the shared contract and ignoring it here beats splitting the port,
 * since the alternative is shared code that must ask which host it is on before
 * copying text.
 *
 * Copy failure is reported, not thrown. The clipboard can be legitimately owned by
 * another application, and the Home hand-off (`tag:kani_repaired is:suspended`) has
 * a visible fallback — the query is shown on screen — so a false success would be
 * worse than a false failure.
 */
class DesktopClipboardService(
    private val write: (String) -> Boolean,
) : ClipboardService {
    override fun setText(label: String, text: String): Boolean {
        if (text.isEmpty()) return false
        return runCatching { write(text) }.getOrDefault(false)
    }
}
