package app.coinflip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatchViewModelTest {
    @Test fun savesWinnerBeforeRevealAndRestoresWithoutReroll() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val rules = testRules()
            val store = object : MatchStore {
                var saved: String? = null
                override suspend fun read() = saved
                override suspend fun write(value: String) { saved = value }
            }
            var rolls = 0
            val model = MatchViewModel(rules, store) { rolls++; 1 }
            runCurrent()
            model.edit { it.copy(teams = listOf("A", "B")) }
            model.flip()
            model.flip()
            runCurrent()
            assertTrue(model.screen.value.flipping)
            assertEquals(1, rolls)
            assertEquals(1, codec.decodeFromString<Match>(store.saved!!).winner)
            advanceTimeBy(2201)
            assertFalse(model.screen.value.flipping)
            val restored = MatchViewModel(rules, store) { error("Unexpected reroll") }
            runCurrent()
            assertTrue(restored.screen.value.resume)
            restored.resume(false)
            assertEquals(1, restored.screen.value.match.winner)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
