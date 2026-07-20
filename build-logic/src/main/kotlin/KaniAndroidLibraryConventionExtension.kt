import org.gradle.api.provider.ListProperty

/**
 * Module-specific exclusions for Android library class-coverage verification.
 */
abstract class KaniAndroidLibraryConventionExtension {
    abstract val coverageExcludes: ListProperty<String>
}
