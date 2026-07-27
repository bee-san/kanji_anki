package dev.bee.kanjianki

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.HomeImportOnboardingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityHomeSyncCacheTest {
    @Test
    fun confirmSyncBuildsFreshConsentInsteadOfUsingTheCachedHomePlan() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        try {
            val activity = Robolectric.buildActivity(CountingHomeActivity::class.java)
                .create()
                .start()
                .resume()
                .get()

            activity.confirmSync()
            assertEquals(0, activity.importPlanCalls)
            awaitSyncConsent(activity)
            assertNotNull(activity.pendingHomeSyncDialog)

            activity.pendingHomeSyncDialog = null
            activity.confirmSync()
            awaitSyncConsent(activity)
            assertEquals(0, activity.importPlanCalls)
            assertNotNull(activity.pendingHomeSyncDialog)
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    private fun awaitSyncConsent(activity: CountingHomeActivity) {
        activity.io.submit { }.get(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
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
