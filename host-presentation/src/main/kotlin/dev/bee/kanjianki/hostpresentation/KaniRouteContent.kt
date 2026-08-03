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
    val onboarding: OnboardingPlan = OnboardingPlan(
        step = OnboardingStep.CONNECT_PROVIDER,
        binding = CollectionBinding(noteType = ""),
    ),
    val browse: BrowseResults = BrowseResults(),
    val detail: KanjiDetail? = null,
    val study: StudySession? = null,
    val stats: StatsDashboard? = null,
    val games: GamesScreen? = null,
    val settings: SettingsScreen? = null,
)
