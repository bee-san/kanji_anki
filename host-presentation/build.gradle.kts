plugins {
    id("kani.kotlin-library-conventions")
}

dependencies {
    // The host-neutral presentation mappers turn :application use-case snapshots
    // (and its runtime render types) into the portable :presentation-api DTOs both
    // hosts render. Plain JVM, no Compose and no host: the @Composable route bodies
    // stay in the feature modules; only the pure snapshot -> DTO mapping lives here,
    // which is what lets :app and :desktop-app share one mapping instead of two.
    api(project(":application"))
    api(project(":presentation-api"))
    api(project(":core"))
    api(project(":data-api"))
    // DesktopStatsModel maps :progress-core's ProgressAnalyticsState to the portable
    // StatsDashboard; the analytics computation itself stays in :progress-core.
    api(project(":progress-core"))
    // The Update section reports through :update-core's own status and
    // background-option policies rather than re-deciding when an install may be
    // offered. Both hosts read the same answer that way.
    api(project(":update-core"))
    testImplementation(testFixtures(project(":data-api")))
}
