package dev.bee.kanjianki

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class RunnableClickListenerTest {
    @Test
    fun onClickRunsActionWhenViewIsNull() {
        val clicked = AtomicBoolean(false)

        RunnableClickListener(Runnable { clicked.set(true) }).onClick(null)

        assertTrue(clicked.get())
    }

    @Test(expected = NullPointerException::class)
    fun nullActionFailsWhenClicked() {
        RunnableClickListener(null).onClick(null)
    }
}
