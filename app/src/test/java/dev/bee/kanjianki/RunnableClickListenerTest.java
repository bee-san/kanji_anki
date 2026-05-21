package dev.bee.kanjianki;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

public final class RunnableClickListenerTest {
    @Test
    public void onClickRunsActionWhenViewIsNull() {
        AtomicBoolean clicked = new AtomicBoolean(false);

        new RunnableClickListener(() -> clicked.set(true)).onClick(null);

        assertTrue(clicked.get());
    }

    @Test(expected = NullPointerException.class)
    public void nullActionFailsWhenClicked() {
        new RunnableClickListener(null).onClick(null);
    }
}
