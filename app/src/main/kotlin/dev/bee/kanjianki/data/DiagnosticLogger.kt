package dev.bee.kanjianki.data

interface DiagnosticLogger {
    fun isCapturing(): Boolean

    fun log(message: String)

    fun traceStudyLoad(message: String)
}

internal object NoOpDiagnosticLogger : DiagnosticLogger {
    override fun isCapturing(): Boolean = false

    override fun log(message: String) = Unit

    override fun traceStudyLoad(message: String) = Unit
}
