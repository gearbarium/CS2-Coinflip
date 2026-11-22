package app.coinflip

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val ComponentActivity.dataStore by preferencesDataStore("coinflip")
private val stateKey = stringPreferencesKey("match")

class MainActivity : ComponentActivity() {
    private val model by viewModels<MatchViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val store = dataStore
                val rules = assets.open("veto-rules.json").bufferedReader().use {
                    codec.decodeFromString<Rules>(it.readText())
                }
                return MatchViewModel(rules, object : MatchStore {
                    override suspend fun read() = store.data.first()[stateKey]
                    override suspend fun write(value: String) { store.edit { it[stateKey] = value } }
                }) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val state by model.screen.collectAsState()
            val density = LocalDensity.current
            var wasFlipping by remember { mutableStateOf(false) }
            LaunchedEffect(state.flipping) {
                if (wasFlipping && !state.flipping && state.match.settings.sound) {
                    val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 45)
                    try { tone.startTone(ToneGenerator.TONE_PROP_ACK, 140); delay(180) }
                    finally { tone.release() }
                }
                wasFlipping = state.flipping
            }
            CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * state.match.settings.scale)) {
                val scope = rememberCoroutineScope()
                var exportText by rememberSaveable { mutableStateOf<String?>(null) }
                val png = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
                    val value = exportText
                    exportText = null
                    if (uri != null && value != null) scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            runCatching {
                                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = 0xFFF1F7FB.toInt()
                                    textSize = 25f
                                    typeface = resources.getFont(R.font.inter)
                                }
                                val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, 840)
                                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(5f, 1f).build()
                                val bitmap = Bitmap.createBitmap(920, layout.height + 80, Bitmap.Config.ARGB_8888)
                                try {
                                    val canvas = android.graphics.Canvas(bitmap)
                                    canvas.drawColor(0xFF0A1520.toInt())
                                    canvas.translate(40f, 40f)
                                    layout.draw(canvas)
                                    contentResolver.openOutputStream(uri)?.use {
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                                    } == true
                                } finally { bitmap.recycle() }
                            }.getOrDefault(false)
                        }
                        Toast.makeText(this@MainActivity, if (saved) "PNG сохранён" else "Не удалось сохранить PNG",
                            Toast.LENGTH_SHORT).show()
                    }
                }
                MidnightAmber {
                    MatchScreen(state, model.rules, model::edit, model::flip, model::resume,
                        copy = { value -> getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("CS2 Coinflip", value)) },
                        export = { value -> exportText = value; png.launch("CS2 Coinflip.png") })
                }
            }
        }
    }
}

internal val night = Color(0xFF050B12)
internal val panel = Color(0xFF0A1520)
internal val raised = Color(0xFF11212E)
internal val amber = Color(0xFFFFB02E)
internal val silver = Color(0xFFF1F7FB)
internal val muted = Color(0xFF9CAEBB)
internal val line = Color(0xFF526A79)
internal val divider = Color(0xFF243744)
internal val rounded = RoundedCornerShape(12.dp)
internal val highlight = Color(0xFFFFD184)
private val amberPressed = Color(0xFFD88B18)
private val amberMuted = Color(0xFFB88A52)
private val navigation = Color(0xFF07121B)
private val largeCard = RoundedCornerShape(16.dp)
private val inter = FontFamily(Font(R.font.inter))
private val type = Typography(
    headlineLarge = Typography().headlineLarge.copy(fontFamily = inter, fontWeight = FontWeight.Bold),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold),
    titleLarge = Typography().titleLarge.copy(fontFamily = inter),
    titleMedium = Typography().titleMedium.copy(fontFamily = inter),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = inter),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = inter),
    labelLarge = Typography().labelLarge.copy(fontFamily = inter),
    labelMedium = Typography().labelMedium.copy(fontFamily = inter)
)

@Composable internal fun MidnightAmber(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = amber, onPrimary = night, background = night, onBackground = silver,
            surface = panel, onSurface = silver, surfaceVariant = raised, onSurfaceVariant = muted,
            primaryContainer = raised, onPrimaryContainer = amber,
            secondaryContainer = raised, onSecondaryContainer = amber,
            secondary = amberMuted, onSecondary = night,
            surfaceContainer = panel, surfaceContainerHigh = raised,
            outline = line, outlineVariant = divider
        ),
        typography = type
    ) { CompositionLocalProvider(LocalContentColor provides silver, content = content) }
}

@Composable internal fun MatchScreen(
    state: Screen, rules: Rules, edit: ((Match) -> Match) -> Unit,
    flip: () -> Unit, resume: (Boolean) -> Unit, copy: (String) -> Unit, export: (String) -> Unit
) {
    val match = state.match
    val compactHeight = LocalConfiguration.current.screenHeightDp < 500
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    var settings by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<Int?>(null) }
    var reset by rememberSaveable { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val winner = match.winner.takeUnless { state.flipping }
    val veto = match.first != null && !state.flipping
    LaunchedEffect(copied) { if (copied) { delay(1800); copied = false } }
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A1B28), night, night)))
            .safeDrawingPadding().imePadding().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.widthIn(max = 800.dp).fillMaxWidth().heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (settings) Text("Настройки", style = MaterialTheme.typography.headlineSmall)
                else BrandLogo(Modifier)
            }
            TextButton(onClick = { settings = !settings }, enabled = state.ready && !state.flipping && !state.resume) {
                Text(if (settings) "Готово" else "⚙", fontSize = if (settings) 15.sp else 26.sp,
                    modifier = Modifier.testTag("settings").semantics {
                        if (!settings) contentDescription = "Настройки"
                    })
            }
        }
        HorizontalDivider(color = divider)
        if (settings) {
            SettingsScreen(match, rules, edit, Modifier.widthIn(max = 800.dp).weight(1f).fillMaxWidth())
        } else {
            Column(Modifier.widthIn(max = 800.dp).weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.ready) CircularProgressIndicator()
                if (state.ready && !state.resume) {
                    Column(Modifier.fillMaxWidth().background(panel, largeCard).border(1.dp, divider, largeCard).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (match.winner == null) {
                            val field: @Composable (Int, Modifier) -> Unit = { index, modifier ->
                                OutlinedTextField(match.teams[index],
                                    { name -> edit { it.copy(teams = it.teams.toMutableList().apply { set(index, name) }) } },
                                    modifier.testTag("team$index"), singleLine = true,
                                    label = { Text(if (index == 0) "Орёл" else "Решка") }, shape = rounded,
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = highlight))
                            }
                            if (wide) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    field(0, Modifier.weight(1f))
                                    field(1, Modifier.weight(1f))
                                }
                            } else {
                                field(0, Modifier.fillMaxWidth())
                                field(1, Modifier.fillMaxWidth())
                            }
                        }
                        if (veto) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Coin(state, Modifier.size(42.dp))
                                Text("Победитель · ${match.teams[winner!!]}", Modifier.weight(1f), color = amber)
                                TextButton(onClick = { reset = true }) { Text("Сброс") }
                            }
                        } else {
                            Coin(state, Modifier.size(if (compactHeight) 96.dp else 184.dp))
                            if (winner == null) {
                                AmberButton(flip, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.flipping) {
                                    Text(if (state.flipping) "Жеребьёвка…" else "Подбросить")
                                }
                            } else {
                                Text("${if (winner == 0) "Орёл" else "Решка"} · ${match.teams[winner]}",
                                    color = amber, style = MaterialTheme.typography.titleLarge)
                                AmberButton({ edit { it.start(false) } }, Modifier.fillMaxWidth()) { Text("Начать первым") }
                                OutlinedButton({ edit { it.start(true) } }, Modifier.fillMaxWidth(), shape = rounded) { Text("Передать первый ход") }
                                TextButton({ reset = true }) { Text("Сброс") }
                            }
                        }
                    }
                    if (veto) Veto(match, rules, onMap = { index ->
                        if (match.settings.confirm) pending = index else edit { it.select(index) }
                    }, undo = { edit { it.undo() } }, reset = { reset = true },
                        copy = { copy(match.result(rules)); copied = true }, export = { export(match.result(rules)) })
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (copied) Text("Результат скопирован", color = amber)
                }
            }
            HorizontalDivider(color = divider)
            Row(Modifier.widthIn(max = 800.dp).fillMaxWidth().background(navigation).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("BO1", "BO3", "BO5").forEach { format ->
                    FilterChip(selected = match.format == format, onClick = { edit { it.copy(format = format) } },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        enabled = state.ready && match.winner == null, shape = rounded, label = { Text(format) })
                }
            }
        }
    }
    if (state.resume) Confirm("Продолжить матч?", "Орёл · ${match.teams[0]}\nРешка · ${match.teams[1]}", "Продолжить",
        "Новый матч", { resume(false) }, { resume(true) })
    if (reset) Confirm("Сбросить матч?", "Жеребьёвка и ходы будут удалены.", "Сбросить", "Отмена",
        { reset = false; edit { it.reset() } }, { reset = false })
    pending?.takeIf { veto && !state.resume && !match.complete }?.let { index ->
        Confirm("${match.action(rules)} · ${match.maps[index]}", match.teams[match.actor()!!], "Подтвердить", "Отмена",
            { pending = null; edit { it.select(index) } }, { pending = null })
    }
}

@Composable private fun Confirm(title: String, message: String, yes: String, no: String, accept: () -> Unit, reject: () -> Unit) {
    AlertDialog(onDismissRequest = {},
        title = { Text(title) }, text = { Text(message) },
        confirmButton = { AmberButton(accept) { Text(yes) } },
        dismissButton = { TextButton(reject) { Text(no) } }, shape = rounded)
}

@Composable private fun BrandLogo(modifier: Modifier) {
    val bitmap = ImageBitmap.imageResource(R.drawable.logo)
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Image(
            painter = remember(bitmap) { BitmapPainter(bitmap, IntOffset(96, 180), IntSize(1980, 372)) },
            contentDescription = "CS2 Coinflip",
            modifier = Modifier.weight(1f, fill = false).widthIn(max = 188.dp).height(36.dp),
            alignment = Alignment.CenterStart
        )
        Text("by gearbarium", color = muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun Coin(state: Screen, modifier: Modifier) {
    val motion = remember { Animatable(0f) }
    val match = state.match
    val heads = ImageBitmap.imageResource(R.drawable.coinside1)
    val tails = ImageBitmap.imageResource(R.drawable.coinside2)
    LaunchedEffect(state.flipping) {
        motion.snapTo(0f)
        if (state.flipping && !match.settings.reduced) {
            motion.animateTo(1f, tween(match.settings.duration, easing = Easing { t -> t * t * (3f - 2f * t) }))
        }
    }
    val description = when {
        state.flipping -> "Монетка вращается"
        match.winner == 0 -> "Орёл"
        match.winner == 1 -> "Решка"
        else -> "Монетка"
    }
    Canvas(modifier.testTag("coin").semantics { contentDescription = description }) {
        val target = if (match.winner == 1) -1f else 1f
        val turns = if (target < 0f) 9 else 8
        val turn = when {
            !state.flipping -> target
            match.settings.reduced -> 1f
            else -> cos(motion.value * turns * Math.PI).toFloat()
        }
        // Rotation about X keeps the horizontal diameter fixed.
        val diameter = size.minDimension
        val height = (diameter * abs(turn).coerceAtLeast(0.02f)).roundToInt().coerceAtLeast(1)
        val edge = sqrt((1f - turn * turn).coerceAtLeast(0f))
        if (match.settings.effects && diameter > 50.dp.toPx()) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF49301A), Color.Transparent), center, diameter / 2), diameter / 2)
        }
        if (edge > 0.02f) {
            val edgeSize = Size(diameter * 0.86f, diameter * edge * 0.055f)
            drawRoundRect(amberMuted, topLeft = center - Offset(edgeSize.width / 2, edgeSize.height / 2),
                size = edgeSize, cornerRadius = CornerRadius(3.dp.toPx()))
        }
        val face = if (turn < 0f) tails else heads
        val width = diameter.roundToInt()
        drawImage(face, dstOffset = IntOffset(((size.width - width) / 2).roundToInt(),
            ((size.height - height) / 2).roundToInt()), dstSize = IntSize(width, height))
    }
}

@Composable internal fun AmberButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    Button(onClick, modifier, enabled, shape = rounded,
        border = if (focused) BorderStroke(2.dp, highlight) else null,
        colors = ButtonDefaults.buttonColors(containerColor = if (pressed) amberPressed else amber, contentColor = night),
        interactionSource = source, content = content)
}

@Composable private fun Veto(
    match: Match, rules: Rules, onMap: (Int) -> Unit, undo: () -> Unit, reset: () -> Unit,
    copy: () -> Unit, export: () -> Unit
) {
    Text("Map Veto", style = MaterialTheme.typography.headlineSmall)
    val decider = match.decider
    if (decider != null) {
        val picks = match.picks(rules)
        picks.forEachIndexed { index, (step, map) ->
            SeriesMap(index + 1, match.maps[map], match.teams[match.actor(step)!!], decisive = false)
        }
        SeriesMap(picks.size + 1, match.maps[decider], "Решающая карта", decisive = true)
        Text(match.result(rules, includeSeries = false), Modifier.fillMaxWidth().background(panel, largeCard).border(1.dp, divider, largeCard).padding(16.dp),
            style = MaterialTheme.typography.bodyLarge)
        AmberButton(copy, Modifier.fillMaxWidth()) { Text("Скопировать результат") }
        OutlinedButton(export, Modifier.fillMaxWidth(), shape = rounded) { Text("Сохранить PNG") }
        OutlinedButton(reset, Modifier.fillMaxWidth(), shape = rounded) { Text("Новый матч") }
        OutlinedButton(undo, shape = rounded) { Text("Отменить ход") }
        return
    } else {
        Text("${match.teams[match.actor()!!]} · ${match.action(rules)}", color = amber)
    }
    match.maps.indices.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (index in row) {
                val step = match.moves.indexOf(index)
                val status = if (step >= 0) match.action(rules, step) else null
                OutlinedButton(onClick = { onMap(index) }, enabled = !match.complete && status == null,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("map$index"), shape = rounded,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = panel, contentColor = silver,
                        disabledContainerColor = if (status == "PICK") Color(0xFF302116) else raised,
                        disabledContentColor = if (status == "BAN") muted else amber)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(match.maps[index])
                        status?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    if (!match.complete && match.moves.isNotEmpty()) {
        Text("История", style = MaterialTheme.typography.titleMedium)
        match.moves.forEachIndexed { step, index ->
            Text("${match.teams[match.actor(step)!!]} · ${match.action(rules, step)} · ${match.maps[index]}", color = muted)
        }
    }
    OutlinedButton(undo, enabled = match.moves.isNotEmpty(), shape = rounded) { Text("Отменить ход") }
}

@Composable private fun SeriesMap(number: Int, name: String, detail: String, decisive: Boolean) {
    Column(Modifier.fillMaxWidth()
        .background(if (decisive) Color(0xFF302116) else raised, largeCard)
        .border(1.dp, if (decisive) amber else line, largeCard)
        .padding(horizontal = 18.dp, vertical = 14.dp)
        .testTag(if (decisive) "decider" else "series-map-$number"),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Карта $number", color = if (decisive) highlight else amber, style = MaterialTheme.typography.labelLarge)
        Text(name, style = MaterialTheme.typography.headlineSmall)
        Text(detail, color = if (decisive) highlight else muted, style = MaterialTheme.typography.bodyMedium)
    }
}
