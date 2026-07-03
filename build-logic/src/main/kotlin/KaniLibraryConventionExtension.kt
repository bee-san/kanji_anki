import org.gradle.api.provider.ListProperty

/**
 * Extension point for the `kani.kotlin-library-conventions` plugin. Modules can add
 * JaCoCo coverage class-file exclusion globs (e.g. Kotlin-generated `*WhenMappings*`).
 */
abstract class KaniLibraryConventionExtension {
    abstract val coverageExcludes: ListProperty<String>
}
