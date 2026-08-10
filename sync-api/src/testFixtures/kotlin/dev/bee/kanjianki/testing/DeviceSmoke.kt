package dev.bee.kanjianki.testing

/** Compact, stable tests that run across the supported-device smoke matrix. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class DeviceSmoke
