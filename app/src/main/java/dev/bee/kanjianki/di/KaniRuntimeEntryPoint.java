package dev.bee.kanjianki.di;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import dev.bee.kanjianki.RoomLegacyStudyReadBridge;

@EntryPoint
@InstallIn(SingletonComponent.class)
public interface KaniRuntimeEntryPoint {
    RoomLegacyStudyReadBridge roomLegacyStudyReadBridge();
}
