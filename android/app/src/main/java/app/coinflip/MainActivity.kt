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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.sin

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
private val elevated = Color(0xFF08111B)
private val success = Color(0xFF91CCAE)
private val errorTone = Color(0xFFEB9A96)
private val disabled = Color(0xFF657786)
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
            outline = line, outlineVariant = divider, error = errorTone
        ),
        typography = type
    ) { CompositionLocalProvider(LocalContentColor provides silver, content = content) }
}

@Composable internal fun MatchScreen(
    state: Screen, rules: Rules, edit: ((Match) -> Match) -> Unit,
    flip: () -> Unit, resume: (Boolean) -> Unit, copy: (String) -> Unit, export: (String) -> Unit
) {
    val match = state.match
    val screen = LocalConfiguration.current
    val compactHeight = screen.screenHeightDp < 500
    val wide = screen.screenWidthDp >= 530
    val sideBySide = wide && compactHeight && match.winner == null
    val coinSize = if (compactHeight) 64.dp else (screen.screenHeightDp - 495).coerceIn(116, 224).dp
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
        Row(Modifier.widthIn(max = 800.dp).fillMaxWidth().heightIn(min = if (compactHeight) 48.dp else 64.dp),
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
            SettingsScreen(match, rules, edit, Modifier.widthIn(max = 1000.dp).weight(1f).fillMaxWidth().reveal("settings", match.settings.reduced))
        } else {
            Column(Modifier.widthIn(max = 1000.dp).weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(vertical = if (compactHeight) 6.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.ready) CircularProgressIndicator()
                if (state.ready && !state.resume) {
                    if (!match.complete) Column(Modifier.fillMaxWidth().background(elevated, largeCard).border(1.dp, divider, largeCard)
                        .padding(if (compactHeight) 8.dp else 18.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compactHeight) 6.dp else 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        if (match.winner == null) {
                            if (!sideBySide) Text("Жеребьёвка", Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall)
                            val field: @Composable (Int, Modifier) -> Unit = { index, modifier ->
                                OutlinedTextField(match.teams[index],
                                    { name -> edit { it.copy(teams = it.teams.toMutableList().apply { set(index, name) }) } },
                                    modifier.testTag("team$index"), singleLine = true,
                                    label = { Text(if (index == 0) "Орёл" else "Решка") }, shape = rounded,
                                    leadingIcon = { Text(if (index == 0) "●" else "◇", color = amber) },
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = highlight))
                            }
                            if (sideBySide) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        field(0, Modifier.fillMaxWidth())
                                        field(1, Modifier.fillMaxWidth())
                                    }
                                    CoinLaunch(state, coinSize, flip, Modifier.weight(1f))
                                }
                            } else if (wide) {
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
                                Column(Modifier.weight(1f).reveal(winner, match.settings.reduced)) {
                                    Text("Победитель жеребьёвки", color = success, style = MaterialTheme.typography.labelMedium)
                                    Text(match.teams[winner!!], style = MaterialTheme.typography.titleLarge)
                                }
                                TextButton(onClick = { reset = true }) { Text("Сброс") }
                            }
                        } else if (!sideBySide) {
                            if (winner == null) {
                                CoinLaunch(state, coinSize, flip)
                            } else {
                                Coin(state, Modifier.size(coinSize))
                                Text("${if (winner == 0) "Орёл" else "Решка"} · ${match.teams[winner]}",
                                    modifier = Modifier.reveal(winner, match.settings.reduced),
                                    color = amber, style = MaterialTheme.typography.headlineSmall)
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
            FormatSelector(match.format, state.ready && match.winner == null) { format ->
                edit { it.copy(format = format) }
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

@Composable private fun CoinLaunch(state: Screen, size: androidx.compose.ui.unit.Dp, flip: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Coin(state, Modifier.size(size))
        AmberButton(flip, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.flipping) {
            Text(if (state.flipping) "Жеребьёвка…" else "Подбросить")
        }
    }
}

@Composable private fun Confirm(title: String, message: String, yes: String, no: String, accept: () -> Unit, reject: () -> Unit) {
    AlertDialog(onDismissRequest = {}, containerColor = raised,
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
        val stage = size.minDimension
        val lift = if (state.flipping && !match.settings.reduced)
            sin(motion.value * Math.PI).toFloat() * stage * .09f else 0f
        val faceCenter = center - Offset(0f, lift)
        val diameter = if (stage > 100.dp.toPx()) stage * .87f else stage
        val height = (diameter * abs(turn).coerceAtLeast(0.02f)).roundToInt().coerceAtLeast(1)
        val edge = sqrt((1f - turn * turn).coerceAtLeast(0f))
        if (match.settings.effects && diameter > 50.dp.toPx()) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF694322), Color.Transparent), center, stage / 2), stage / 2)
            drawCircle(amber.copy(alpha = .22f), stage * .455f, style = Stroke(1.dp.toPx()))
            drawCircle(amberMuted.copy(alpha = .21f), stage * .49f, style = Stroke(1.dp.toPx()))
        }
        if (edge > 0.02f) {
            val edgeSize = Size(diameter * 0.86f, diameter * edge * 0.055f)
            drawRoundRect(amberMuted, topLeft = faceCenter - Offset(edgeSize.width / 2, edgeSize.height / 2),
                size = edgeSize, cornerRadius = CornerRadius(3.dp.toPx()))
        }
        val face = if (turn < 0f) tails else heads
        val width = diameter.roundToInt()
        drawImage(face, dstOffset = IntOffset((faceCenter.x - width / 2f).roundToInt(),
            (faceCenter.y - height / 2f).roundToInt()), dstSize = IntSize(width, height))
    }
}

@Composable internal fun AmberButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (pressed) .975f else 1f, tween(100), label = "button press")
    Button(onClick, modifier.graphicsLayer { scaleX = scale; scaleY = scale }, enabled, shape = rounded,
        border = if (focused) BorderStroke(2.dp, highlight) else null,
        colors = ButtonDefaults.buttonColors(containerColor = if (pressed) amberPressed else amber, contentColor = night),
        interactionSource = source, content = content)
}

@Composable private fun Veto(
    match: Match, rules: Rules, onMap: (Int) -> Unit, undo: () -> Unit, reset: () -> Unit,
    copy: () -> Unit, export: () -> Unit
) {
    Text(if (match.complete) "Результат матча" else "Map Veto", style = MaterialTheme.typography.headlineSmall)
    val decider = match.decider
    if (decider != null) {
        TurnPanel(match.format, match.teams.joinToString("  ·  "),
            "Победитель жеребьёвки · ${match.teams[match.winner!!]}", match.settings.reduced)
        val picks = match.picks(rules)
        picks.forEachIndexed { index, (step, map) ->
            SeriesMap(index + 1, match.maps[map], match.teams[match.actor(step)!!], decisive = false)
        }
        SeriesMap(picks.size + 1, match.maps[decider], "Решающая карта", decisive = true)
        HistoryPanel(match, rules)
        AmberButton(copy, Modifier.fillMaxWidth()) { Text("Скопировать результат") }
        OutlinedButton(export, Modifier.fillMaxWidth(), shape = rounded) { Text("Сохранить PNG") }
        OutlinedButton(reset, Modifier.fillMaxWidth(), shape = rounded) { Text("Новый матч") }
        OutlinedButton(undo, shape = rounded) { Text("Отменить ход") }
        return
    } else {
        val action = match.action(rules)
        TurnPanel("$action  ·  Ход ${match.moves.size + 1} / 6", match.teams[match.actor()!!],
            if (action == "PICK") "Выберите карту ${match.picks(rules).size + 1}" else "Исключите карту из пула",
            match.settings.reduced)
    }
    val columns = if (LocalConfiguration.current.screenWidthDp >= 680) 4 else 2
    match.maps.indices.chunked(columns).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (index in row) {
                val step = match.moves.indexOf(index)
                val status = if (step >= 0) match.action(rules, step) else null
                MapCard(match.maps[index], status, Modifier.weight(1f).testTag("map$index")) { onMap(index) }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (!match.complete && match.moves.isNotEmpty()) {
        HistoryPanel(match, rules)
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

@Composable private fun Modifier.reveal(state: Any?, reduced: Boolean): Modifier {
    var ready by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state) { ready = true }
    val progress by animateFloatAsState(if (ready || reduced) 1f else .82f, tween(180), label = "panel reveal")
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 24.dp.toPx()
    }
}

@Composable internal fun WorkspacePanel(
    modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().background(panel, largeCard).border(1.dp, divider, largeCard).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable private fun TurnPanel(caption: String, title: String, detail: String, reduced: Boolean) {
    WorkspacePanel(Modifier.reveal(caption, reduced)) {
        Text(caption, color = amber, style = MaterialTheme.typography.labelLarge)
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(detail, color = muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun HistoryPanel(match: Match, rules: Rules) {
    WorkspacePanel {
        Text("История veto", style = MaterialTheme.typography.titleMedium)
        match.moves.forEachIndexed { step, map ->
            HorizontalDivider(color = divider)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val action = match.action(rules, step).orEmpty()
                Text((step + 1).toString().padStart(2, '0'), color = muted, style = MaterialTheme.typography.labelMedium)
                Text(action, color = if (action == "PICK") amber else errorTone, style = MaterialTheme.typography.labelLarge)
                Column(Modifier.weight(1f)) {
                    Text(match.maps[map], style = MaterialTheme.typography.titleMedium)
                    Text(match.teams[match.actor(step)!!], color = muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable private fun FormatSelector(value: String, enabled: Boolean, select: (String) -> Unit) {
    Row(Modifier.widthIn(max = 800.dp).fillMaxWidth().padding(vertical = 8.dp)
        .background(navigation, largeCard).border(1.dp, divider, largeCard).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("BO1", "BO3", "BO5").forEach { format ->
            val active = value == format
            Box(Modifier.weight(1f).heightIn(min = 48.dp)
                .background(if (active) raised else navigation, rounded)
                .border(1.dp, if (active) amber else Color.Transparent, rounded)
                .selectable(active, enabled = enabled, role = Role.RadioButton, onClick = { select(format) }),
                contentAlignment = Alignment.Center) {
                Text(format, color = if (active) amber else if (enabled) muted else disabled,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable private fun MapCard(name: String, status: String?, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = status == null, modifier = modifier.heightIn(min = 76.dp),
        color = if (status == "PICK") Color(0xFF302116) else raised, contentColor = silver,
        shape = largeCard, border = BorderStroke(1.dp, if (status == "PICK") amber else divider)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(status ?: "Доступна", color = when (status) { "PICK" -> amber; "BAN" -> errorTone; else -> muted },
                style = MaterialTheme.typography.labelMedium)
        }
    }
}
