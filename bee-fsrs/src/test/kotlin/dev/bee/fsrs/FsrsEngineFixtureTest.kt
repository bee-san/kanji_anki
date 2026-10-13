@file:Suppress("DEPRECATION")

package dev.bee.fsrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

import java.nio.file.Files
import java.nio.file.Path

private const val TOLERANCE = 1.0e-9

class FsrsEngineFixtureTest {
    @Test
    fun generatedReferenceFixtureIsTheEngineOracle() {
        val root = asObject(Json.parse(Files.readString(referenceCasesPath())))
        val cases = asList(root["cases"])

        assertEquals(38, cases.size)
        for (rawCase in cases) {
            val testCase = asObject(rawCase)
            val name = text(testCase, "name")
            when (text(testCase, "kind")) {
                "initial" -> assertInitialCase(name, testCase)
                "review" -> assertReviewCase(name, testCase)
                "interval" -> assertIntervalCase(name, testCase)
                "shortTerm" -> assertShortTermCase(name, testCase)
                "validation" -> assertValidationCase(name, testCase)
                else -> fail("Unknown fixture case kind for $name")
            }
        }
    }

    private fun assertInitialCase(name: String, testCase: Map<String, Any?>) {
        val state = engine(testCase).initialState(rating(testCase))
        assertState(name, state, asObject(testCase["expectedInitialState"]))
    }

    private fun assertReviewCase(name: String, testCase: Map<String, Any?>) {
        val previous = asObject(testCase["previousState"])
        val output = requireNotNull(
            engine(testCase).review(
                FsrsReviewInput(
                    FsrsMemoryState(number(previous, "stability"), number(previous, "difficulty")),
                    rating(testCase),
                    integer(testCase, "elapsedDays"),
                    number(testCase, "desiredRetention"),
                    integer(testCase, "maximumInterval"),
                )
            )
        )

        assertState(name, output.nextState, asObject(testCase["expectedNextState"]))
        assertEquals(name + " retrievability", number(testCase, "expectedRetrievability"), output.retrievability, TOLERANCE)
        assertEquals(name + " interval", integer(testCase, "expectedNextIntervalDays"), output.nextIntervalDays)
    }

    private fun assertIntervalCase(name: String, testCase: Map<String, Any?>) {
        val interval = engine(testCase).nextIntervalDays(
            number(testCase, "stability"),
            number(testCase, "desiredRetention"),
            integer(testCase, "maximumInterval"),
        )

        assertEquals(name + " interval", integer(testCase, "expectedNextIntervalDays"), interval)
    }

    private fun assertShortTermCase(name: String, testCase: Map<String, Any?>) {
        val stability = engine(testCase).shortTermStability(number(testCase, "stability"), rating(testCase))

        assertEquals(name + " short-term stability", number(testCase, "expectedShortTermStability"), stability, TOLERANCE)
    }

    private fun assertValidationCase(name: String, testCase: Map<String, Any?>) {
        val payload = asObject(testCase["payload"])
        expectIllegalArgument {
            when (text(testCase, "target")) {
                "FsrsParameters.of" -> FsrsParameters.of(doubleArray(asList(payload["parameters"])))
                "FsrsEngine.nextState" -> {
                    val previous = asObject(payload["previousState"])
                    engine(testCase).nextState(
                        FsrsMemoryState(number(previous, "stability"), number(previous, "difficulty")),
                        rating(payload),
                        integer(payload, "elapsedDays"),
                    )
                }
                "FsrsMemoryState" -> FsrsMemoryState(number(payload, "stability"), number(payload, "difficulty"))
                "FsrsEngine.nextIntervalDays" -> engine(testCase).nextIntervalDays(
                    number(payload, "stability"),
                    number(payload, "desiredRetention"),
                    integer(payload, "maximumInterval"),
                )
                else -> fail("Unknown validation target for $name")
            }
        }
    }

    private fun engine(testCase: Map<String, Any?>): FsrsEngine {
        return FsrsEngine.create(FsrsParameters.of(doubleArray(asList(testCase["parameters"]))))
    }

    private fun rating(values: Map<String, Any?>): FsrsRating {
        return FsrsRating.valueOf(text(values, "rating"))
    }

    private fun assertState(name: String, state: FsrsMemoryState?, expected: Map<String, Any?>) {
        val actual = requireNotNull(state)
        assertEquals(name + " stability", number(expected, "stability"), actual.stability, TOLERANCE)
        assertEquals(name + " difficulty", number(expected, "difficulty"), actual.difficulty, TOLERANCE)
    }

    private fun referenceCasesPath(): Path {
        val modulePath = Path.of("testdata", "upstream-reference-cases.json")
        if (Files.exists(modulePath)) {
            return modulePath
        }
        return Path.of("testdata", "upstream-reference-cases.json")
    }

    private fun doubleArray(values: List<Any?>): DoubleArray {
        val out = DoubleArray(values.size)
        for (i in values.indices) {
            out[i] = number(values[i])
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun asObject(value: Any?): Map<String, Any?> {
        return value as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun asList(value: Any?): List<Any?> {
        return value as List<Any?>
    }

    private fun text(values: Map<String, Any?>, key: String): String {
        return values[key] as String
    }

    private fun integer(values: Map<String, Any?>, key: String): Int {
        return Math.round(number(values, key)).toInt()
    }

    private fun number(values: Map<String, Any?>, key: String): Double {
        return number(values[key])
    }

    private fun number(value: Any?): Double {
        if (value is Number) {
            return value.toDouble()
        }
        if (value == "NaN") {
            return Double.NaN
        }
        throw IllegalArgumentException("Expected number but got $value")
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertNotNull(expected.message)
        }
    }
}
