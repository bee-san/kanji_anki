package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.presentation.BrowseResults
import dev.bee.kanjianki.presentation.CollectionBinding
import dev.bee.kanjianki.presentation.GamesScreen
import dev.bee.kanjianki.presentation.HomeDashboard
import dev.bee.kanjianki.presentation.KanjiDetail
import dev.bee.kanjianki.presentation.OnboardingPlan
import dev.bee.kanjianki.presentation.OnboardingStep
import dev.bee.kanjianki.presentation.SettingsScreen
import dev.bee.kanjianki.presentation.StatsDashboard
import dev.bee.kanjianki.presentation.StudyKeybindings
import dev.bee.kanjianki.presentation.StudySession

/**
 * What a host route currently has to show, as portable presentation DTOs.
 *
 * One type for every route rather than a per-route sealed hierarchy: the fields a
 * route does not draw stay at their defaults, and a host fills only what its current
 * destination renders. Every field is a `:presentation-api` type both hosts render
 * identically, which is the point — this is the payload the shared [KaniShellHost]
 * carries so `:app` and `:desktop-app` drive one presentation model instead of two.
 *
 * [providerMessage] is the provider's status line as plain copy, not the probe it came
 * from: the desktop AnkiConnect probe and the Android provider check are different
 * objects, but both project to a line the placeholder routes display. Onboarding and
 * sync-availability read a host's fresh probe, never this snapshot.
 *
 * [studyItemCount] and [dueCount] are the cheapest evidence a route loaded through the
 * real startup lifecycle rather than a stub. The Home models carry defaults so a route
 * that is not Home — and a test that only cares about load state — can construct this
 * without assembling a dashboard.
 */
data class KaniRouteContent(
    val providerMessage: String,
    val studyItemCount: Int,
    val dueCount: Int,
    val themeChoice: dev.bee.kanjianki.core.KaniThemeChoice,
    val home: HomeDashboard = HomeDashboard(),
    val onboarding: OnboardingPlan = UNLOADED_ONBOARDING,
    val browse: BrowseResults = BrowseResults(),
    val detail: KanjiDetail? = null,
    val study: StudySession? = null,
    val stats: StatsDashboard? = null,
    val games: GamesScreen? = null,
    val settings: SettingsScreen? = null,
    /**
     * The Study keybindings in force, for the Study surface and native accelerators.
     *
     * On the content rather than read separately by the Study route, so the keyboard a
     * card is graded with and the editor row that shows it come from the same load — a
     * remap takes effect on the next route load, and nothing can render one binding
     * while the shortcut handler uses another.
     */
    val studyKeybindings: StudyKeybindings = StudyKeybindings.DEFAULT,
)

/**
 * The onboarding plan for content that has not loaded yet.
 *
 * Named rather than inline so a host can resolve confirmation copy before its first
 * load completes without inventing a plan of its own: "no provider, no note type" is
 * the honest reading of "we have not asked yet", and it is the same plan the default
 * argument produces.
 */
val UNLOADED_ONBOARDING: OnboardingPlan = OnboardingPlan(
    step = OnboardingStep.CONNECT_PROVIDER,
    binding = CollectionBinding(noteType = ""),
)
