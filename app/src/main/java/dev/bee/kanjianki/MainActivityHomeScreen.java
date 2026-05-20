package dev.bee.kanjianki;

final class MainActivityHomeScreen {
    private final MainActivityHome home;

    MainActivityHomeScreen(MainActivityHome home) {
        this.home = home;
    }

    void renderHome() {
        MainActivityHomeScreenRenderer.renderHomeScreen(home);
    }
}
