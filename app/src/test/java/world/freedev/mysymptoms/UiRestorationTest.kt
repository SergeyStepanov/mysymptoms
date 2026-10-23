package world.freedev.mysymptoms

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class UiRestorationTest {
    @get:Rule val compose = createComposeRule()

    private val store = mock(SymptomStore::class.java)
    private val event = SymptomEvent(
        42, 7, "headache",
        LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        "original note"
    )

    @Before
    fun setUp() {
        `when`(store.ensureInitialProfile()).thenReturn(listOf(Profile(7, "Test profile")))
        `when`(store.activeProfileId(anyList())).thenReturn(7)
        `when`(store.savedLayout()).thenReturn(SymptomStore.LAYOUT_TABLE)
        `when`(store.eventsBetween(anyLong(), anyLong(), anyLong())).thenReturn(listOf(event))
    }

    @Test
    fun addScreenRestoresSymptomNotePresetAndTimestampWithoutSavingEarly() {
        val restoration = launchApp()
        compose.onNodeWithContentDescription("Headache").performClick()
        compose.onNodeWithText("Note").performTextInput("draft note")
        val beforePreset = System.currentTimeMillis()
        compose.onNodeWithText("15 min ago").performClick()
        val beforeRestore = System.currentTimeMillis()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Headache").assertIsDisplayed()
        compose.onNodeWithText("draft note").assertIsDisplayed()
        compose.onNodeWithText("15 min ago").assertIsSelected()
        verify(store, never()).saveEvent(anyLong(), anyString(), anyLong(), anyString())
        compose.onNodeWithText("Save entry").performScrollTo().performClick()
        val timestamp = ArgumentCaptor.forClass(Long::class.javaObjectType)
        verify(store).saveEvent(
            eq(7L), eq("headache") ?: "", timestamp.capture() ?: 0L, eq("draft note") ?: ""
        )
        assertTrue(timestamp.value <= beforeRestore - 15 * 60_000L)
        assertTrue(timestamp.value >= beforePreset - 15 * 60_000L)
    }

    @Test
    fun editScreenRestoresEventAndUnsavedNote() {
        val restoration = launchApp()
        openEvent()
        compose.onNodeWithText("Note").performTextReplacement("edited draft")

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("edited draft").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        verify(store).updateEvent(42, 7, event.occurredAt, "edited draft")
    }

    @Test
    fun deleteConfirmationSurvivesRestorationAndDoesNotDeleteUntilConfirmed() {
        val restoration = launchApp()
        openEvent()
        compose.onNodeWithText("\uD83D\uDDD1").performClick()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Are you sure you want to delete this entry?").assertIsDisplayed()
        verify(store, never()).deleteEvent(anyLong(), anyLong())
        compose.onNodeWithText("Yes").performClick()
        verify(store).deleteEvent(42, 7)
    }

    @Test
    fun eventSaverRoundTripsAllFieldsIncludingLargeIdsAndUnicode() {
        val original = SymptomEvent(Long.MAX_VALUE, 8, "headache", Long.MAX_VALUE, "Заметка\n'quoted'")
        val scope = SaverScope { true }
        val saved = with(symptomEventSaver) { scope.save(original) }

        assertNotNull(saved)
        assertEquals(original, symptomEventSaver.restore(checkNotNull(saved)))
        assertNull(symptomEventSaver.restore(emptyList()))
        assertNull(symptomEventSaver.restore(listOf("wrong", 8L, "headache", 123L, "")))
        assertNull(symptomEventSaver.restore(listOf(42L, 8L, "headache", 123L)))
    }

    private fun launchApp(): StateRestorationTester =
        StateRestorationTester(compose).also { restoration ->
            restoration.setContent { SymptomJournalApp(store) }
        }

    private fun openEvent() {
        compose.onNodeWithText("☰").performClick()
        compose.onNodeWithText("Calendar").performClick()
        compose.onNodeWithText("original note", substring = true).performScrollTo().performClick()
    }
}
