package dev.bee.kanjianki

internal class SevenItemRouteActivity : MainActivity() {
    override fun renderStudy() {
        val progress = studySessionViewModel.acceptedRouteSnapshot().progress
        if (progress.targetCount > 0 && progress.completedCount == progress.targetCount) {
            super.renderStudy()
        }
    }
}