package dev.bee.kanjianki;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ComposeShellPresenterTest {
    @Test
    public void createBuildsStableCopyFromTheAppVersion() {
        ComposeShellModel model = ComposeShellPresenter.create("0.4.33");

        assertEquals("Compose shell", model.getTitle());
        assertTrue(model.getBody().contains("Kotlin/Compose shell"));
        assertEquals("App version 0.4.33", model.getVersionLabel());
        assertEquals("Close", model.getCloseLabel());
    }
}
