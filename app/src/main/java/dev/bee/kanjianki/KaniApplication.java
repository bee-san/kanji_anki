package dev.bee.kanjianki;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.work.Configuration;

public final class KaniApplication extends Application implements Configuration.Provider {
    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setJobSchedulerJobIdRange(10_000, 11_000)
                .build();
    }
}
