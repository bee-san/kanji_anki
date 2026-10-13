package dev.bee.kanjianki.application

/**
 * Owns one process container and prevents its construction until a host restore
 * check explicitly permits startup.
 */
class RestoreGatedContainer<R, C : AutoCloseable>(
    private val restore: () -> R,
    private val allowsStartup: (R) -> Boolean,
    private val blockedMessage: (R) -> String,
    private val createContainer: () -> C,
) : AutoCloseable {
    @Volatile
    private var ownedContainer: C? = null
    private var startAttempted = false

    val container: C
        get() = checkNotNull(ownedContainer) {
            "Process container is unavailable before restore-gated startup"
        }

    @Synchronized
    fun start(): R {
        check(!startAttempted) { "Restore-gated startup may run only once" }
        startAttempted = true
        val result = restore()
        check(allowsStartup(result)) { blockedMessage(result) }
        ownedContainer = createContainer()
        return result
    }

    fun closeSuppressing(primaryFailure: Throwable) {
        try {
            close()
        } catch (closeFailure: Throwable) {
            if (closeFailure !== primaryFailure) {
                primaryFailure.addSuppressed(closeFailure)
            }
        }
    }

    @Synchronized
    override fun close() {
        val container = ownedContainer ?: return
        ownedContainer = null
        container.close()
    }
}
