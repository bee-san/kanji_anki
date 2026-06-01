package dev.bee.kanjianki.syncdomain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class SyncModelValidatorTest {
    @Test
    fun validatesModelNameAndRequiredFields() {
        assertTrue(
            validateModelFields(
                "Kiku",
                listOf("Expression", "Meaning"),
                "Kiku",
                listOf("Expression", "Meaning"),
            ).isEmpty(),
        )
        assertEquals(
            1,
            validateModelFields(
                "Other",
                listOf("Expression", "Meaning"),
                "Kiku",
                listOf("Expression", "Meaning"),
            ).size,
        )
        assertEquals(
            2,
            validateModelFields(
                "Kiku",
                listOf("Expression"),
                "Kiku",
                listOf("Expression", "Meaning", "Sentence"),
            ).size,
        )
    }

    @Test
    fun hasPrivateConstructorForKotlinAndJavaInterop() {
        val constructor = SyncModelValidator::class.java.getDeclaredConstructor()

        assertTrue(Modifier.isPrivate(constructor.modifiers))
        constructor.isAccessible = true
        assertNotNull(constructor.newInstance())
    }

    @Test
    fun classifiesProviderFailures() {
        assertEquals("permanent_permission", classifyProviderFailure(SecurityException("denied")))
        assertEquals("permanent_configuration", classifyProviderFailure(RuntimeException("missing field Expression")))
        assertEquals("permanent_configuration", classifyProviderFailure(RuntimeException("wrong model")))
        assertEquals("permanent_configuration", classifyProviderFailure(RuntimeException("missing note type")))
        assertEquals("permanent_permission", classifyProviderFailure(RuntimeException("provider permission denied")))
        assertEquals("retryable_provider", classifyProviderFailure(null))
        assertEquals("retryable_provider", classifyProviderFailure(RuntimeException()))
        assertEquals("retryable_provider", classifyProviderFailure(RuntimeException("cursor timed out")))
    }

    private fun validateModelFields(
        actualModelName: String?,
        actualFields: List<String>,
        expectedModelName: String,
        requiredFields: List<String>,
    ): List<String> {
        val method = SyncModelValidator::class.java.getDeclaredMethod(
            "validateModelFields",
            String::class.java,
            List::class.java,
            String::class.java,
            List::class.java,
        )

        @Suppress("UNCHECKED_CAST")
        return method.invoke(null, actualModelName, actualFields, expectedModelName, requiredFields) as List<String>
    }

    private fun classifyProviderFailure(error: Throwable?): String {
        val method = SyncModelValidator::class.java.getDeclaredMethod(
            "classifyProviderFailure",
            Throwable::class.java,
        )
        return method.invoke(null, error) as String
    }
}
