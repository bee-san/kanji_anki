plugins {
    id("kani.kotlin-library-conventions")
}

dependencies {
    // The progress-analytics computation reads :data-api stats snapshots and applies
    // :core policies; it produces the display-ready ProgressAnalyticsState both hosts'
    // dashboards render. Plain JVM, no Android — which is what lets the desktop host
    // compute the same analytics the Android host does.
    api(project(":core"))
    api(project(":data-api"))
    testImplementation(testFixtures(project(":data-api")))
}
