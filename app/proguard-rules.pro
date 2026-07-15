# Kani R8 keep rules
# ---
# The app uses R8 full mode (via proguard-android-optimize.txt) for maximum
# shrinking and optimization. These rules cover the reflection, system-loaded,
# and external-contract paths that R8 cannot trace statically.

# --- Glance / AppWidget ---
# GlanceAppWidgetReceiver is loaded by the Android framework via the manifest.
# The GlanceAppWidget it returns is instantiated by the receiver at runtime.
-keep class dev.bee.kanjianki.widget.KaniWidgetReceiver { *; }
-keep class dev.bee.kanjianki.widget.KaniWidget { *; }

# --- ML Kit Digital Ink Recognition ---
# ML Kit uses reflection to load model classes and recognizer implementations.
-keep class com.google.mlkit.vision.digitalink.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_digital_ink.** { *; }

# --- WorkManager Workers ---
# Workers are instantiated by WorkManager via reflection (default WorkerFactory).
-keep class dev.bee.kanjianki.update.AutoUpdateWorker { *; }
-keep class dev.bee.kanjianki.fsrs.FsrsFitWorker { *; }
-keep class dev.bee.kanjianki.sync.AutoSyncRetryWorker { *; }
-keep class dev.bee.kanjianki.backup.DatabaseBackupWorker { *; }

# --- JobService ---
# Declared in the manifest; loaded by the system's JobScheduler.
-keep class dev.bee.kanjianki.sync.AutoSyncJobService { *; }

# --- BroadcastReceivers ---
# Declared in the manifest; loaded by the system.
-keep class dev.bee.kanjianki.reminders.ReminderReceiver { *; }
-keep class dev.bee.kanjianki.reminders.BootReminderReceiver { *; }
-keep class dev.bee.kanjianki.update.PackageInstallStatusReceiver { *; }

# --- Application ---
-keep class dev.bee.kanjianki.KaniApplication { *; }

# --- BuildConfig ---
# The update engine reads these fields via generated accessors.
-keep class dev.bee.kanjianki.BuildConfig { *; }

# --- Debug benchmark fixture (reflection-loaded in debug only) ---
-dontwarn dev.bee.kanjianki.ButtonLatencyBenchmarkFixtureSeeder

# --- Content provider column names ---
# AnkiDroid provider queries use string-based column names; R8 cannot strip
# anything from the provider side. No keep rules needed — the string constants
# are never obfuscated because they are literal arguments, not field names.

# --- AndroidX / Compose / Glance infrastructure ---
# R8 rules ship as consumer rules in the AAR; these are supplementary.
-keep class androidx.glance.** { *; }
-keep class androidx.work.impl.** { *; }

# --- Suppress warnings for optional dependencies ---
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn kotlin.reflect.jvm.internal.**
