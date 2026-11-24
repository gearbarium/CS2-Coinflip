package app.coinflip

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun SettingsScreen(match: Match, rules: Rules, edit: ((Match) -> Match) -> Unit, modifier: Modifier) {
    val settings = match.settings
    val canEdit = match.winner == null
    var poolIndex by remember { mutableIntStateOf(settings.active) }
    val pool = settings.pools[poolIndex]
    fun update(change: (Settings) -> Settings) = edit { it.copy(settings = change(it.settings)) }
    fun updatePool(change: (Pool) -> Pool) = update { old ->
        old.copy(pools = old.pools.toMutableList().apply { set(poolIndex, change(get(poolIndex))) })
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WorkspacePanel {
            Text("Карты", style = MaterialTheme.typography.headlineSmall)
            if (!canEdit) Text("Пул можно изменить после сброса матча.", color = muted)
            var selectingPool by remember { mutableStateOf(false) }
            Box {
                OutlinedButton({ selectingPool = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = rounded) {
                    Text(if (poolIndex == settings.active) "${pool.name} · активный ▾" else "${pool.name} ▾")
                }
                DropdownMenu(expanded = selectingPool, onDismissRequest = { selectingPool = false }) {
                    settings.pools.forEachIndexed { index, item ->
                        DropdownMenuItem(text = { Text(if (index == settings.active) "${item.name} · активный" else item.name) },
                            onClick = { poolIndex = index; selectingPool = false })
                    }
                }
            }
            OutlinedTextField(pool.name, { value -> updatePool { it.copy(name = value) } },
                Modifier.fillMaxWidth(), enabled = canEdit, singleLine = true, label = { Text("Название пула") }, shape = rounded)
            pool.maps.forEachIndexed { index, map ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    key(index) {
                        OutlinedTextField(map, { value -> updatePool { it.copy(maps = it.maps.toMutableList().apply { set(index, value) }) } },
                            Modifier.weight(1f), enabled = canEdit, singleLine = true, shape = rounded)
                        IconButton(onClick = { updatePool { it.copy(maps = it.maps.toMutableList().apply { add(index - 1, removeAt(index)) }) } },
                            enabled = canEdit && index > 0) { Text("↑") }
                        IconButton(onClick = { updatePool { it.copy(maps = it.maps.toMutableList().apply { add(index + 1, removeAt(index)) }) } },
                            enabled = canEdit && index < pool.maps.lastIndex) { Text("↓") }
                        IconButton(onClick = { updatePool { it.copy(maps = it.maps.filterIndexed { i, _ -> i != index }) } },
                            enabled = canEdit) { Text("×") }
                    }
                }
            }
            OutlinedButton({ updatePool { it.copy(maps = it.maps + "") } }, enabled = canEdit, shape = rounded) { Text("Добавить карту") }
            OutlinedButton({ update { it.copy(pools = it.pools + Pool("Пул ${it.pools.size + 1}", rules.pool)) }; poolIndex = settings.pools.size },
                enabled = canEdit, shape = rounded) { Text("Создать пул") }
            AmberButton({ update { it.copy(active = poolIndex) } }, enabled = canEdit && poolIndex != settings.active && validPool(pool.maps)) { Text("Сделать активным") }
            OutlinedButton({ updatePool { it.copy(maps = rules.pool) } }, enabled = canEdit, shape = rounded) { Text("Восстановить стандартные карты") }
            if (!validPool(pool.maps)) Text("Для активации нужны семь уникальных карт.", color = muted)
        }
        WorkspacePanel {
            Text("Монетка", style = MaterialTheme.typography.headlineSmall)
            Text("Длительность · ${settings.duration} мс")
            Slider(settings.duration.toFloat(), { value -> update { it.copy(duration = value.toInt()) } }, valueRange = 500f..4000f, steps = 34)
            Toggle("Звуковые эффекты", settings.sound) { value -> update { it.copy(sound = value) } }
            Toggle("Упрощённая анимация", settings.reduced) { value -> update { it.copy(reduced = value) } }
            Toggle("Победитель выбирает первый ход", settings.choose) { value -> update { it.copy(choose = value) } }
        }
        WorkspacePanel {
            Text("Интерфейс", style = MaterialTheme.typography.headlineSmall)
            Text("Масштаб текста · ${(settings.scale * 100).toInt()}%")
            Slider(settings.scale, { value -> update { it.copy(scale = value) } }, valueRange = 0.85f..1.4f, steps = 10)
            Toggle("Декоративные эффекты", settings.effects) { value -> update { it.copy(effects = value) } }
            Toggle("Подтверждать выбор карты", settings.confirm) { value -> update { it.copy(confirm = value) } }
        }
        Text("CoinFlip CS2", style = MaterialTheme.typography.titleLarge)
        Text("Jetpack Compose, DataStore, Kotlin · Apache 2.0\nInter · SIL OFL 1.1", color = muted)
    }
}

@Composable private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
