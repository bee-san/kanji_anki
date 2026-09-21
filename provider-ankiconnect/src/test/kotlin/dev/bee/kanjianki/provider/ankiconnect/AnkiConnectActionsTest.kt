package dev.bee.kanjianki.provider.ankiconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectActionsTest {
    @Test
    fun allowlistIsTheUnionOfRequiredAndOptional() {
        assertEquals(
            AnkiConnectActions.required + AnkiConnectActions.optional,
            AnkiConnectActions.allowlist,
        )
        // No overlap between the two tiers.
        assertTrue((AnkiConnectActions.required intersect AnkiConnectActions.optional).isEmpty())
    }

    @Test
    fun allowsListedActionsAndRejectsOthers() {
        assertTrue(AnkiConnectActions.isAllowed("findNotes"))
        assertTrue(AnkiConnectActions.isAllowed("addNotes"))
        assertFalse(AnkiConnectActions.isAllowed("deleteNotes"))
        assertFalse(AnkiConnectActions.isAllowed("sync"))
    }

    @Test
    fun requireAllowedThrowsForUnlistedActions() {
        AnkiConnectActions.requireAllowed("version")
        val error = assertThrows(IllegalArgumentException::class.java) {
            AnkiConnectActions.requireAllowed("deleteDeck")
        }
        assertTrue(error.message!!.contains("deleteDeck"))
    }

    @Test
    fun missingRequiredReportsGapsAgainstAServerReflection() {
        // A server missing findCards + cardsInfo.
        val reported = AnkiConnectActions.required - setOf("findCards", "cardsInfo")
        assertEquals(setOf("findCards", "cardsInfo"), AnkiConnectActions.missingRequired(reported))
    }

    @Test
    fun missingRequiredIsEmptyWhenAllRequiredArePresent() {
        val reported = AnkiConnectActions.allowlist + setOf("someExtraAction")
        assertTrue(AnkiConnectActions.missingRequired(reported).isEmpty())
    }

    @Test
    fun availableOptionalReportsOnlySupportedOptionalActions() {
        val reported = AnkiConnectActions.required + setOf("addNotes", "createDeck", "unknown")
        assertEquals(setOf("addNotes", "createDeck"), AnkiConnectActions.availableOptional(reported))
    }
}
