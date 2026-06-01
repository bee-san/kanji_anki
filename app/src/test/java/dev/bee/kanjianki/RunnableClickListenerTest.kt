package dev.bee.kanjianki

import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Test

class RunnableClickListenerTest {
    @Test
    fun onClickRunsActionWhenViewIsNull() {
        val clicked = AtomicBoolean(false)

        RunnableClickListener { clicked.set(true) }.onClick(null)

        assertTrue(clicked.get())
    }

    @Test(expected = NullPointerException::class)
    fun nullActionFailsWhenClicked() {
        RunnableClickListener(null).onClick(null)
    }
}
