package app.coinflip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

interface MatchStore {
    suspend fun read(): String?
    suspend fun write(value: String)
}

data class Screen(
    val match: Match, val ready: Boolean = false, val resume: Boolean = false,
    val flipping: Boolean = false, val error: String? = null
)

class MatchViewModel(
    val rules: Rules, private val store: MatchStore, private val random: () -> Int = ::flipSide
) : ViewModel() {
    private val mutable = MutableStateFlow(Screen(initialMatch(rules)))
    val screen = mutable.asStateFlow()
    private val writes = Channel<Match>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            val restored = runCatching { store.read()?.let { codec.decodeFromString<Match>(it) } }
            val match = restored.getOrNull()?.takeIf { it.isValid(rules) }
            mutable.value = Screen(
                match ?: initialMatch(rules), ready = true, resume = match?.let { it.winner != null && !it.complete } == true,
                error = if (restored.isFailure || (restored.getOrNull() != null && match == null))
                    "Не удалось восстановить сохранение" else null
            )
            for (snapshot in writes) {
                runCatching { store.write(codec.encodeToString(snapshot)) }
                    .onFailure { mutable.value = mutable.value.copy(error = "Не удалось сохранить состояние") }
            }
        }
    }

    fun edit(change: (Match) -> Match) {
        val current = mutable.value
        if (!current.ready || current.flipping || current.resume) return
        update(current.copy(match = change(current.match), error = null))
    }

    fun resume(discard: Boolean) {
        val current = mutable.value
        update(current.copy(resume = false, match = if (discard) current.match.reset() else current.match))
    }

    fun flip() {
        val current = mutable.value
        if (!current.ready || current.flipping || current.resume || current.match.winner != null) return
        val error = current.match.error()
        if (error != null) {
            mutable.value = current.copy(error = error)
            return
        }
        val outcome = current.match.flip(random())
        update(current.copy(match = outcome, flipping = true, error = null))
        viewModelScope.launch {
            delay(outcome.settings.duration.toLong())
            mutable.value = mutable.value.copy(flipping = false)
        }
    }

    private fun update(value: Screen) {
        mutable.value = value
        writes.trySend(value.match)
    }
}
