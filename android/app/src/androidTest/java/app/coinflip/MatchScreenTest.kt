package app.coinflip

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class MatchScreenTest {
    @get:Rule val compose = createComposeRule()
    private val rules = Rules(listOf("Ancient", "Anubis", "Cache", "Dust II", "Inferno", "Mirage", "Nuke"),
        mapOf("BO3" to listOf("BAN", "BAN", "PICK", "PICK", "BAN", "BAN"),
            "BO5" to listOf("BAN", "BAN", "PICK", "PICK", "PICK", "PICK")))

    @Test fun confirmsMapAndSupportsUndo() {
        compose.setContent {
            var match by remember {
                mutableStateOf(initialMatch(rules).copy(teams = listOf("A", "B")).flip(0).start(false))
            }
            MidnightAmber { MatchScreen(Screen(match, ready = true), rules, { match = it(match) }, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("by gearbarium").assertIsDisplayed()
        compose.onNodeWithTag("map0").performScrollTo().performClick()
        compose.onNodeWithText("Подтвердить").performClick()
        compose.onNodeWithTag("map0").assertIsNotEnabled()
        compose.onNodeWithText("Отменить ход").performScrollTo().performClick()
        compose.onNodeWithTag("map0").assertIsEnabled()
    }

    @Test fun finalMoveShowsBo3Series() = verifySeries("BO3", listOf("Cache", "Dust II"))

    @Test fun finalMoveShowsBo5Series() = verifySeries("BO5", listOf("Cache", "Dust II", "Inferno", "Mirage"))

    private fun verifySeries(format: String, picks: List<String>) {
        compose.setContent {
            var match by remember {
                mutableStateOf(initialMatch(rules).copy(teams = listOf("A", "B"))
                    .flip(0).start(false).copy(format = format, moves = (0..4).toList()))
            }
            MidnightAmber { MatchScreen(Screen(match, ready = true), rules, { match = it(match) }, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("decider").assertDoesNotExist()
        compose.onNodeWithTag("map5").performScrollTo().performClick()
        compose.onNodeWithText("Подтвердить").performClick()
        picks.forEachIndexed { index, name ->
            val card = hasTestTag("series-map-${index + 1}")
            compose.onNode(card).performScrollTo().assertIsDisplayed()
            compose.onNode(hasText(name) and hasAnyAncestor(card)).assertIsDisplayed()
        }
        compose.onNodeWithTag("decider").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nuke").assertIsDisplayed()
        compose.onNodeWithTag("map6").assertDoesNotExist()
    }
}
