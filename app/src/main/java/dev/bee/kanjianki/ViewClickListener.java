package dev.bee.kanjianki;

import android.view.View;

import java.util.function.Consumer;

final class ViewClickListener implements View.OnClickListener {
    private final Consumer<View> action;

    ViewClickListener(Consumer<View> action) {
        this.action = action;
    }

    @Override
    public void onClick(View v) {
        action.accept(v);
    }
}
