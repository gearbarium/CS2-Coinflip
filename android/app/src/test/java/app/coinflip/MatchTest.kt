package app.coinflip

import kotlinx.serialization.Serializable
import org.junit.Assert.*
import org.junit.Test

private fun resource(name: String) = checkNotNull(MatchTest::class.java.getResource("/$name")).readText()
internal fun testRules() = codec.decodeFromString<Rules>(resource("veto-rules.json"))

@Serializable private data class Case(
    val format: String, val first: Int, val moves: List<Int>, val maps: List<Int>, val actors: List<Int>
)

class MatchTest {
    @Test fun sharedSequencesAndRestoration() {
        val rules = testRules()
        codec.decodeFromString<List<Case>>(resource("test-cases.json")).forEach { case ->
            var game = initialMatch(rules).copy(format = case.format, teams = listOf("Орлы".repeat(80), "Решка"))
                .flip(0).start(case.first == 1)
            case.moves.forEachIndexed { step, map ->
                assertEquals(case.actors[step], game.actor())
                game = game.select(map)
                assertEquals(game, game.select(map))
                game = codec.decodeFromString(codec.encodeToString(Match.serializer(), game))
                assertTrue(game.isValid(rules))
            }
            val maps = game.moves.filterIndexed { i, _ -> game.action(rules, i) == "PICK" } + game.decider!!
            assertEquals(case.maps, maps)
            assertNull(game.undo().decider)
        }
    }

    @Test fun secureCoinSamplesBothSides() {
        assertTrue(List(1000) { flipSide() }.sum() in 300..700)
    }
}
