package dev.bee.kanjianki;

import android.view.View;

final class RunnableClickListener implements View.OnClickListener {
    private final Runnable action;

    RunnableClickListener(Runnable action) {
        this.action = action;
    }

    @Override
    public void onClick(View v) {
        action.run();
    }
}
