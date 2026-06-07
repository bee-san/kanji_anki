package dev.bee.kanjianki

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.HomeImportOnboardingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityHomeSyncCacheTest {
    @Test
    fun confirmSyncReusesCachedPlanUntilHomeRendersAgain() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        try {
            val activity = Robolectric.buildActivity(CountingHomeActivity::class.java)
                .create()
                .start()
                .resume()
                .get()

            activity.confirmSync()
            assertEquals(1, activity.importPlanCalls)
            assertNotNull(activity.pendingHomeSyncDialog)

            activity.confirmSync()
            assertEquals(1, activity.importPlanCalls)
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    private class CountingHomeActivity : MainActivity() {
        var importPlanCalls = 0

        override fun importOnboardingPlan(): HomeImportOnboardingPolicy.Plan {
            importPlanCalls++
            return HomeImportOnboardingPolicy.Plan(
                HomeImportOnboardingPolicy.State.READY_FIRST_SYNC,
                "Sync cards when ready.",
                "Sync cards",
            )
        }
    }
}

private fun fakeAnkiDroidGateway(): AnkiDroidGateway {
    val constructor = AnkiDroidGateway::class.java.getDeclaredConstructor(Context::class.java, List::class.java)
    constructor.isAccessible = true
    return constructor.newInstance(
        ApplicationProvider.getApplicationContext<Context>(),
        emptyList<Any>(),
    ) as AnkiDroidGateway
}
