# Desktop Accessibility And Keybindings

What Kani's desktop host actually supports for keyboard, pointer, and assistive
technology — and, where support is partial or absent, what is missing and on
whose authority. Goal 203's closing requirement is that the claims here match
evidence, so every row either names the test that holds it or says plainly that
it is unverified.

The rule this document follows: an accessibility claim that turns out to be
false is worse than an admitted gap. A user who is told Linux screen-reader
support works and finds silence has already committed to the app.

## Platform accessibility bridges

Compose Multiplatform's own accessibility page
([kotlinlang.org](https://kotlinlang.org/docs/multiplatform/compose-desktop-accessibility.html))
states the desktop status directly, and Kani inherits it — Kani writes semantics
into Compose and Compose is what reaches the platform, so Kani cannot exceed
this table:

| Platform | Upstream status | What Kani ships |
| --- | --- | --- |
| macOS | "Fully supported" | Semantics reach VoiceOver through the toolkit; verify with Xcode's Accessibility Inspector. |
| Windows | "Supported via Java Access Bridge" | `jdk.accessibility` is in the packaged runtime image, so the bridge exists in the installed app. The bridge is **disabled by default in the JDK** and a user enables it with `jabswitch.exe /enable`. |
| Linux | "Not supported" | No screen-reader support. Not a Kani limitation to fix; there is no bridge for Compose desktop to speak through. Orca will not read a Kani window. |

Two consequences worth stating rather than leaving implicit.

**`jdk.accessibility` had to be added by hand.** `jpackage` builds a minimal
`jlink` image, and `./gradlew :desktop-app:suggestRuntimeModules` cannot name
this module — nothing on Kani's classpath references it, because the JDK loads
the bridge reflectively when it is enabled. An image built from the scan alone
ships a Windows build that renders perfectly, passes every semantics assertion
in the suite, and reads to NVDA and JAWS as an empty window.
`KaniDesktopRuntimeModules.REQUIRED` pins it and
`KaniDesktopIdentityTest.theRuntimeImageCarriesEveryModuleTheInstalledAppNeeds`
asserts it by name. This is the same defect class as the missing `java.net.http`
recorded in `docs/desktop-performance-budgets.md`: invisible to unit tests,
visible only in the packaged image.

**The Linux row is quoted, not measured.** Kani has not tested Orca against a
Kani window and does not claim to have. The row is upstream's documented status,
which is the evidence Goal 203 asks for, and the honest position for a host whose
toolkit has no Linux bridge to test.

### What is verified, and how

Semantics correctness is verified on both hosts and off the platform bridges.
Each Compose module keeps its assertions in `commonTest` and registers them in
both a `*DesktopRenderTest` and a `*AndroidRenderTest`;
`tools/test_host_render_parity.py` fails the build when a registration exists on
only one host. So the semantics tree is checked structurally everywhere, and the
bridge that carries it to a screen reader is checked by platform on the table
above.

What that structural check covers: every interactive control has a role and an
accessible name; charts carry non-blank text alternatives
(`StatsAccessibilityAssertions`, which also pins that the section-title fallback
cannot fire unconditionally); both answers to a confirmation dialog are
independently invokable, because a dialog where only the accept is reachable
turns "I cannot decide" into "I must agree"; and no interactive control is
smaller than `KaniUiTokens.MinTouchTarget`.

## Keyboard: no route is pointer-only

Every Study task that Kani can complete with a pointer can be completed and
undone from the keyboard.

The evidence is in two layers, and it is worth being exact about which covers
what. `StudyMatrixAssertions.assertTheWholeSessionIsCompletableWithoutAPointerAtEveryScale`
reveals and grades a card through the rendered surface using only keys, in the
smallest supported window, at every font scale in the matrix — the scale sweep is
there because the keyboard path goes through the session's focus anchor, so a
layout change that moved focus would break keys and nothing else. Undo,
multiple-choice selection, and every precedence rule are pinned in
`StudyCommandsTest` against the policy rather than through a rendered keypress:
`KeyEvent` is an expect class wrapping each host's native event with no portable
way to synthesize one, and Compose's test dispatcher refuses to inject a second
key-down for a key it believes is held — which is exactly the event the repeat
filter exists to handle.

Defaults, Anki-compatible where Kani has the same semantic action:

| Keys | Command |
| --- | --- |
| `Space`, `Enter`, `Numpad Enter` | The visible card's single default action: reveal, submit a typed answer, or continue past applied feedback. |
| `1`, `Numpad 1`, `F` | Fail / Again. |
| `3`, `Numpad 3`, `P` | Pass / Good. |
| `Ctrl+Z`, `⌘Z` | The existing guarded review undo. |
| `1`–`9` on a multiple-choice card | Select the option in that position. |

`2` and `4` are unbound, deliberately. Anki's Hard and Easy have no
user-selectable equivalent in Kani: the study UI offers Pass and Fail, and the
one `hard` Kani ever submits is the writing rung's "Save hard", which is the pass
button under a different label chosen by the ink evaluator rather than by the
user. A key bound to the nearest available rating would be a keyboard-only way
to grade differently from every visible control.

On a choice card the digits are not idle — they select, and selecting *is*
grading there, which is why choice selection is resolved ahead of the bindings.

Both `Ctrl+Z` and `⌘Z` are bound on every host rather than the model asking which
OS it is on. Accepting both is correct everywhere and neither chord means
anything else in a session. Only the *labels* are platform-specific: macOS reads
`⌃⌥⇧⌘` glyphs in Apple's menu order, Windows and Linux spell `Ctrl+Alt+Shift+`
and call Meta "Super".

`Escape` is not bindable and is absent from `StudyKey` entirely. It is the
shell's back affordance, and a study shortcut on it would turn "leave this card"
into "fail this card".

### Dispatch precedence

Each of these is a way a grade could otherwise be submitted by accident, so each
is a rule in `StudyKeyboardPolicy.claims` rather than in a host:

- **A focused text field owns its printable keys.** Typing "possible" into the
  typed card's answer box must not pass the card, and typing "3" must not fail
  it. `Enter` still reaches Kani, because `Enter` is a field's submit rather than
  text. A modified chord also still reaches Kani while a field has focus —
  dropping `Ctrl+Z` there would take undo away from the card that most needs it.
- **An IME composition claims everything.** While a Japanese IME is composing,
  `Space` and `Enter` commit the candidate, not the card, so nothing at all is
  claimed.
- **A modal dialog or open menu owns its own keys.**
- **A face-down card can only be revealed.** Pass and Fail resolve to nothing
  until the answer is on screen.
- **Key-up and auto-repeat produce no action.** One press is one intent. Without
  this, holding `3` would submit a grade, continue past the feedback, and grade
  the next card, at the keyboard's repeat rate. `StudyKeyRepeatFilter` tracks
  held keys rather than debouncing on a clock, so it is exact.
- **Number-row and numpad digits are matched on the digit**, not the keycode,
  which is also what keeps the mapping independent of keyboard layout.

Behind all of it, the same authority as pointer input: a key never reaches the
scheduler or a repository. A host translates its native event into a portable
`StudyKeyPress`, the policy returns the *same* `KaniAction` the visible button
dispatches, and `StudySession.acceptsGrade` drops a second grade on an answered
card exactly as it does at the button.

Writing cards have no keyboard grade at all. There is no reveal and no shortcut
past the ink surface, because a primary that graded a writing card would be a way
to pass without writing.

### Remapping

Settings has a per-command keybinding editor: remap, unbind, and reset to
defaults. It refuses two kinds of edit rather than applying them silently — a
conflict (the key already asks for another command) and a reserved OS chord
(`Ctrl+C`, `⌘Q`, `Super+L`, and a short curated list of others), because a
binding the OS swallows first reads to the user as Kani being broken. Re-binding
a key to the command it already has is a no-op, not a conflict.

A command may legitimately end up with no key. A user who never wants to grade by
keyboard is entitled to that, and every action stays reachable by pointer.

Bindings are stored as one device-local string. A backup restore resets them to
the reviewed defaults rather than carrying them across, so a Mac user's `⌘Z` does
not arrive on their Windows install as a Super chord
(`DeviceSettingKeys.portableExclusionStorageNames`). Malformed stored state fails
open **as a whole set**, never per entry: if the entry naming Pass failed to parse
and the entry naming Fail survived, the user would be left studying with a
keyboard that can only fail cards.

### Discoverability

Active accelerators are advertised in two places, both derived from the live
bindings rather than hard-coded, so neither can name a key that would not fire:

- The **native menu bar** prints each accelerator beside its action, so a user
  who never opens Settings still learns that `Space` reveals. Off the Study route
  the items stay listed and inert — the menu still teaches the keys, and a
  disabled item carries no action at all rather than a `false` flag beside a live
  one.
- **Accessible action labels** on the visible controls read "Pass, 3", so a
  screen-reader user — who is not browsing the menu bar — discovers the shortcut
  from the button itself. Supplied only on hosts that route key events; null on
  Android, where telling TalkBack to press a key that is not there would be worse
  than saying nothing.

On the typed card the hints answer `Enter` rather than `Space`, because the answer
box owns `Space` there. A control that printed "Submit (Space)" beside that field
would be naming a key that types a space into it.

## Pointer, display, and input matrices

| Area | Status | Evidence |
| --- | --- | --- |
| Mouse and trackpad | Supported | Every interactive control is at or above `KaniUiTokens.MinTouchTarget` (44dp), measured at every font scale by `assertNoActionShrinksBelowAUsableTargetAtAnyFontScale`. Material's 40dp button defaults were under that floor in six modules and were raised. |
| Stylus | Supported on the ink surface; not separately tested | `InkCanvas` takes pointer input without asking the pointer's type, so a stylus draws as a mouse or finger does. No stylus hardware was available to this host, so this is a design property rather than a measurement. |
| Key repeat | Handled | `StudyKeyRepeatFilter` (see above). |
| IME / Japanese input | Handled in dispatch | Composition claims every key; `Enter` submits a typed answer only after composition completes. Verified as a policy over `StudyInputContext`, not against a live IME on this host. |
| Clipboard | Supported | `DesktopClipboardService` over the AWT system clipboard, behind the `platform-contracts` port the Android host also implements. |
| Multiple monitors | Not separately tested | Window placement is Compose's own; nothing in Kani pins a display. Headless CI cannot enumerate a second monitor. |
| High DPI and fractional scaling | Covered | The window matrix includes a 125% fractional-scale case, which is where fractional dp comes from, plus a 800×600 resize floor. `assertNoControlOverflowsTheWindowSidewaysAtAnyScale` and `assertEachNamedWindowReallyRendersAtItsOwnWidth`. |
| Large fonts | Covered to 200% | Font scales 1.0, 1.3, 1.5, and 2.0 across every window. A clipped label is still perfectly "displayed" to a semantics query, which is why these assertions measure bounds instead. |
| Locales | English and Japanese, plus long-string pseudo-localization | `tools/test_shared_string_locales.py` checks key parity, format-placeholder parity, and plural-name parity across locales — a key present in one locale falls back to English silently, which no single-locale render test can catch. The rendering half (does a doubled-length string still fit at 200% text) is in the per-module matrices. |

## Offline and provider-down operation

Anki not running is a normal state on the desktop, not an error path: Kani is
local-first, and AnkiConnect is only reachable while Anki is open.

`DesktopProviderProbe` distinguishes the cases the user can act on, and each has
its own message: Anki not running ("start Anki"), AnkiConnect present but not
authorized ("grant Kani access"), a stored key that the server never asks for,
and an endpoint that is not a usable loopback URL — which is a configuration
problem and must not be reported as "Anki is not running". A throwing `browse`
is an answer rather than a crash. `DesktopProviderProbeTest` pins each.

Nothing about study depends on the provider being up. Review persistence is
token-first and revision-CAS in one transaction, and only an `APPLIED` result
advances UI or session state, so a provider that disappears mid-session cannot
leave a partially-applied review. Sync history is successful-run-only. Recovery
is simply the next probe: the capability set is re-derived, and a sync that could
not commit retries later.

## Known gaps

Stated rather than closed, because each needs hardware or a live service this
host does not have:

- **Linux screen readers.** Upstream does not support them. Nothing to fix here;
  a Linux user needing a screen reader cannot use Kani's desktop host.
- **Windows and macOS bridges are not tested end-to-end.** The module is in the
  image and the semantics are asserted, but no NVDA, JAWS, or VoiceOver session
  has read a Kani window. That needs a Windows and a macOS machine with a screen
  reader; CI runners have neither.
- **Multiple monitors and stylus hardware** are untested for the same reason.
- **A live IME** has not typed into the desktop build. The dispatch rules are
  asserted; the toolkit's composition reporting is not.

When any of these is exercised, record the result here with the platform and
version rather than upgrading a row to "supported" on inference.
