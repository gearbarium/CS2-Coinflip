package app.coinflip

import java.security.SecureRandom
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class Rules(val pool: List<String>, val formats: Map<String, List<String>>)
@Serializable data class Pool(val name: String, val maps: List<String>)
@Serializable data class Settings(
    val pools: List<Pool>, val active: Int = 0, val duration: Int = 2200,
    val sound: Boolean = false, val reduced: Boolean = false, val choose: Boolean = true,
    val scale: Float = 1f, val effects: Boolean = true, val confirm: Boolean = true
)
@Serializable data class Match(
    val settings: Settings, val version: Int = 1, val teams: List<String> = listOf("", ""),
    val format: String = "BO3", val winner: Int? = null, val first: Int? = null,
    val moves: List<Int> = emptyList()
) {
    val maps get() = settings.pools[settings.active].maps
    val complete get() = moves.size == 6
    val decider get() = if (complete) maps.indices.first { it !in moves } else null
    fun actor(step: Int = moves.size) = first?.let { (it + step) % 2 }
    fun action(rules: Rules, step: Int = moves.size) = rules.formats.getValue(format).getOrNull(step)
    fun select(index: Int): Match =
        if (first == null || complete || index !in maps.indices || index in moves) this
        else copy(moves = moves + index)
    fun undo() = copy(moves = moves.dropLast(1))
    fun reset() = copy(winner = null, first = null, moves = emptyList())
    fun start(pass: Boolean) = if (first != null) this else copy(first = winner?.let { if (pass) 1 - it else it })
    fun error(): String? = when {
        teams.any { it.isBlank() } -> "Введите названия обеих команд"
        teams[0].trim().equals(teams[1].trim(), true) -> "Названия команд должны различаться"
        !validPool(maps) -> "В активном пуле нужны семь уникальных карт"
        else -> null
    }
    fun flip(side: Int): Match = if (winner != null || error() != null || side !in 0..1) this else copy(
        teams = teams.map { it.trim() }, winner = side, first = if (settings.choose) null else side
    )
    fun picks(rules: Rules) = moves.withIndex().filter { action(rules, it.index) == "PICK" }
    fun result(rules: Rules, includeSeries: Boolean = true) = buildString {
        appendLine("${teams.joinToString(" — ")} · $format")
        appendLine("Победитель жеребьёвки: ${winner?.let(teams::get).orEmpty()}")
        if (includeSeries) {
            val picks = picks(rules)
            picks.forEachIndexed { order, (step, map) ->
                appendLine("Map ${order + 1}: ${maps[map]} — ${teams[actor(step)!!]}")
            }
            decider?.let { appendLine("Map ${picks.size + 1}: ${maps[it]} — DECIDER") }
        }
        appendLine("История")
        moves.forEachIndexed { i, map -> appendLine("${teams[actor(i)!!]} · ${action(rules, i)} · ${maps[map]}") }
    }.trimEnd()
    fun isValid(rules: Rules): Boolean =
        version == 1 && teams.size == 2 && format in rules.formats &&
            settings.active in settings.pools.indices && settings.duration in 500..4000 &&
            settings.scale in 0.85f..1.4f && (winner == null || winner in 0..1) &&
            (first == null || (winner != null && first in 0..1)) &&
            (winner == null || error() == null) && (moves.isEmpty() || first != null) &&
            moves.size <= 6 && moves.distinct() == moves && moves.all { it in maps.indices }
}
fun validPool(maps: List<String>) =
    maps.size == 7 && maps.none { it.isBlank() } && maps.map { it.trim().lowercase() }.toSet().size == 7

val codec = Json { encodeDefaults = true; ignoreUnknownKeys = true }
private val secureRandom = SecureRandom()
fun flipSide() = if (secureRandom.nextBoolean()) 1 else 0
fun initialMatch(rules: Rules) = Match(Settings(listOf(Pool("Стандартный", rules.pool))))
