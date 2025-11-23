package com.example.juegomemoria

import android.content.Context
import android.media.SoundPool
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.juegomemoria.ui.theme.JuegoMemoriaTheme
import kotlinx.coroutines.delay

// --- MODELOS DE DATOS Y ESTADO DE PANTALLA ---
sealed class GameScreen {
    object Welcome : GameScreen()
    data class LevelSelect(val relaxMode: Boolean) : GameScreen()
    data class Playing(val startLevelIndex: Int, val relaxMode: Boolean) : GameScreen()
}

private val grupoTarjetas = listOf(
    listOf("🦄", "🍦"),
    listOf("🌈", "👽"),
    listOf("👾", "🤖", "👹", "👺"),
    listOf("🤡", "💩", "🎃", "🙀"),
    listOf("☠️", "👾", "😽", "😼")
)




data class LevelData(
    val tarjetas: List<String>,
    val movimientosMax: Int,
    val timeLimitSeconds: Int,
    val columns: Int
)

private val levels = listOf(
    LevelData(tarjetas = grupoTarjetas[0], movimientosMax = 3, timeLimitSeconds = 10, columns = 2),
    LevelData(tarjetas = grupoTarjetas[0] + grupoTarjetas[1], movimientosMax = 8, timeLimitSeconds = 30, columns = 2),
    LevelData(tarjetas = grupoTarjetas[0] + grupoTarjetas[1] + grupoTarjetas[2], movimientosMax = 12, timeLimitSeconds = 45, columns = 3),
    LevelData(tarjetas = grupoTarjetas[0] + grupoTarjetas[1] + grupoTarjetas[2] + grupoTarjetas[3], movimientosMax = 25, timeLimitSeconds = 60, columns = 4),
    LevelData(tarjetas = grupoTarjetas[0] + grupoTarjetas[1] + grupoTarjetas[2] + grupoTarjetas[3] + grupoTarjetas[4], movimientosMax = 60, timeLimitSeconds = 90, columns = 4)
)

enum class CardState { HIDDEN, DISCOVERED, MATCHED, ERROR }

data class Card(val symbol: String, val state: CardState = CardState.HIDDEN, val id: Int = 0)

// --- LÓGICA DE SONIDO ---
class SoundPlayer(context: Context) {
    private val soundPool: SoundPool = SoundPool.Builder().setMaxStreams(3).build()
    private var areSoundsLoaded = false
    private val soundIds = mutableMapOf<String, Int>()

    init {
        val soundsToLoad: Map<String, Int> = mapOf("flip" to R.raw.carta, "match" to R.raw.acierto, "error" to R.raw.error)
        var loadedCount = 0
        soundPool.setOnLoadCompleteListener { _, _, status -> if (status == 0) { loadedCount++; if (loadedCount == soundsToLoad.size) areSoundsLoaded = true } }
        soundsToLoad.forEach { (name, resId) -> soundIds[name] = soundPool.load(context, resId, 1) }
    }

    private fun playSound(name: String) {
        if (areSoundsLoaded) soundIds[name]?.let { soundPool.play(it, 1f, 1f, 0, 0, 1f) }
    }

    fun playFlipSound() = playSound("flip")
    fun playMatchSound() = playSound("match")
    fun playErrorSound() = playSound("error")
    fun release() = soundPool.release()
}

// --- ACTIVITY PRINCIPAL Y NAVEGACIÓN ---
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var soundPlayer: SoundPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundPlayer = SoundPlayer(this)

        setContent {
            JuegoMemoriaTheme {
                var screen by remember { mutableStateOf<GameScreen>(GameScreen.Welcome) }

                when (val currentScreen = screen) {
                    is GameScreen.Welcome -> WelcomeScreen(
                        onNormalClick = { screen = GameScreen.LevelSelect(relaxMode = false) },
                        onRelaxClick = { screen = GameScreen.LevelSelect(relaxMode = true) }
                    )
                    is GameScreen.LevelSelect -> LevelSelectScreen(
                        onLevelSelected = { index -> screen = GameScreen.Playing(startLevelIndex = index, relaxMode = currentScreen.relaxMode) },
                        onBack = { screen = GameScreen.Welcome }
                    )
                    is GameScreen.Playing -> GameMemoryApp(
                        soundPlayer = soundPlayer,
                        startLevelIndex = currentScreen.startLevelIndex,
                        relaxMode = currentScreen.relaxMode,
                        onNavigateBack = { screen = GameScreen.LevelSelect(relaxMode = currentScreen.relaxMode) }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPlayer.release()
    }
}

// --- PANTALLAS ---
@Composable
fun WelcomeScreen(onNormalClick: () -> Unit, onRelaxClick: () -> Unit) {
    // Usamos un Box como contenedor principal para poder alinear elementos
    // en diferentes partes de la pantalla.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp) // Agregamos padding general aquí
    ) {
        // 1. Contenido principal (el saludo y los botones)
        // Lo ponemos en una Column y alineamos toda la Column en el centro del Box.
        Column(
            modifier = Modifier.align(Alignment.Center), // Esto centra el bloque vertical y horizontalmente
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("👋 ¡Bienvenido! 👋", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onNormalClick, modifier = Modifier.fillMaxWidth()) { Text("¡Quiero jugar!") }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRelaxClick, modifier = Modifier.fillMaxWidth()) { Text("Modo relax") }
        }



        Text(
            text = "Desarrolladopor: Ronald D. Condori",
            modifier = Modifier.align(Alignment.BottomCenter),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelSelectScreen(onLevelSelected: (Int) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Selecciona un Nivel") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }) }
    ) {
        LazyColumn(modifier = Modifier
            .padding(it)
            .fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(levels.size) { index ->
                Button(onClick = { onLevelSelected(index) }, modifier = Modifier.fillMaxWidth()) { Text("Nivel ${index + 1}") }
            }
        }
    }
}

// --- LÓGICA PRINCIPAL DEL JUEGO ---
fun generateCards(symbols: List<String>): List<Card> {
    val allCards = symbols + symbols
    return allCards.shuffled().mapIndexed { index, symbol -> Card(symbol = symbol, id = index) }
}

fun formatNumber(num: Int): String = if (num < 10) "0$num" else "$num"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameMemoryApp(
    soundPlayer: SoundPlayer?,
    startLevelIndex: Int,
    relaxMode: Boolean,
    onNavigateBack: () -> Unit
) {
    var currentLevelIndex by remember { mutableStateOf(startLevelIndex) }
    val currentLevel = levels.getOrElse(currentLevelIndex) { levels.first() }

    var cards by remember(currentLevelIndex) { mutableStateOf(generateCards(currentLevel.tarjetas)) }
    var movimientos by remember(currentLevelIndex) { mutableStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var isComparing by remember { mutableStateOf(false) }

    // --- CAMBIO 1: Única fuente de verdad para el tiempo ---
    var remainingTimeInSeconds by remember(currentLevelIndex) {
        mutableStateOf(currentLevel.timeLimitSeconds)
    }
    // Derivamos minutos y segundos del tiempo restante
    val minutos by remember { derivedStateOf { remainingTimeInSeconds / 60 } }
    val segundos by remember { derivedStateOf { remainingTimeInSeconds % 60 } }


    val levelComplete by remember { derivedStateOf { cards.all { it.state == CardState.MATCHED } && cards.isNotEmpty() } }
    val movesExceeded =
        !relaxMode && currentLevel.movimientosMax > 0 && movimientos >= currentLevel.movimientosMax

    // --- CAMBIO 2: 'timeUp' ahora se basa en el nuevo estado ---
    val timeUp = !relaxMode && remainingTimeInSeconds <= 0
    val isGameOver = (movesExceeded || timeUp) && !levelComplete

    // Efecto para la lógica de comparación de cartas (sin cambios)
    LaunchedEffect(cards) {
        val discoveredCards = cards.filter { it.state == CardState.DISCOVERED }

        if (discoveredCards.size == 2) {
            isComparing = true
            movimientos++
            val card1 = discoveredCards[0]
            val card2 = discoveredCards[1]

            if (card1.symbol == card2.symbol) {
                soundPlayer?.playMatchSound()
                cards =
                    cards.map { if (it.state == CardState.DISCOVERED) it.copy(state = CardState.MATCHED) else it }
            } else {
                soundPlayer?.playErrorSound()
                delay(1000L)
                cards =
                    cards.map { if (it.state == CardState.DISCOVERED) it.copy(state = CardState.HIDDEN) else it }
            }
            isComparing = false
        }
    }

    // --- CAMBIO 3: El LaunchedEffect del cronómetro es ahora mucho más simple ---
    if (!relaxMode && !LocalInspectionMode.current) {
        LaunchedEffect(currentLevelIndex, isGameOver, levelComplete) {
            // El cronómetro solo se ejecuta si el juego está activo
            if (!isGameOver && !levelComplete) {
                while (remainingTimeInSeconds > 0) {
                    delay(1000L)
                    remainingTimeInSeconds--
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Nivel ${formatNumber(currentLevelIndex + 1)}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Volver"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                "Menú"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }) {
                            levels.forEachIndexed { index, _ ->
                                DropdownMenuItem(
                                    text = { Text("Nivel ${index + 1}") },
                                    onClick = { currentLevelIndex = index; menuExpanded = false })
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.fillMaxWidth()
            ) {
                val movesText =
                    if (!relaxMode && currentLevel.movimientosMax > 0) "$movimientos / ${currentLevel.movimientosMax}" else "$movimientos"
                Text("Movimientos: $movesText", style = MaterialTheme.typography.bodyLarge)
                if (!relaxMode) Text(
                    "Tiempo: ${formatNumber(minutos)}:${formatNumber(segundos)}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(currentLevel.columns),
                modifier = Modifier.weight(1f),
                userScrollEnabled = false
            ) {
                items(cards.size) { index ->
                    val card = cards[index]
                    CardView(card = card, isClickable = !isComparing, onClick = {
                        if (card.state == CardState.HIDDEN && cards.count { it.state == CardState.DISCOVERED } < 2) {
                            soundPlayer?.playFlipSound()
                            cards = cards.toMutableList()
                                .also { it[index] = card.copy(state = CardState.DISCOVERED) }
                        }
                    })
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Box(modifier = Modifier.height(60.dp), contentAlignment = Alignment.Center) {
                when {
                    levelComplete && currentLevelIndex < levels.size - 1 -> Button(onClick = { currentLevelIndex++ }) {
                        Text(
                            "Siguiente Nivel"
                        )
                    }

                    levelComplete && currentLevelIndex >= levels.size - 1 -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("¡Has ganado!", style = MaterialTheme.typography.headlineMedium)
                        Button(onClick = onNavigateBack) { Text("Menú de niveles") }
                    }

                    isGameOver -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val reason =
                            if (movesExceeded) "Límite de movimientos" else "Se acabó el tiempo"
                        Text(
                            "¡Juego Terminado! - $reason",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(onClick = onNavigateBack) { Text("Menú de niveles") }
                    }

                    else -> Button(onClick = {
                        cards = generateCards(currentLevel.tarjetas)
                        movimientos = 0
                        // --- CAMBIO 4: Reiniciar el tiempo restante ---
                        remainingTimeInSeconds = currentLevel.timeLimitSeconds
                    }) { Text("Reiniciar Nivel") }
                }
            }
        }
    }
}
@Composable
fun CardView(card: Card, isClickable: Boolean, onClick: () -> Unit) {
    val cardColor = when (card.state) {
        CardState.ERROR -> Color.Red.copy(alpha = 0.5f)
        CardState.MATCHED -> Color.Green.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }
    Box(modifier = Modifier
        .sizeIn(minWidth = 60.dp, minHeight = 80.dp)
        .background(cardColor, shape = RoundedCornerShape(8.dp))
        .padding(4.dp)
        .clickable(enabled = card.state == CardState.HIDDEN && isClickable) { onClick() }
        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
        .padding(8.dp), contentAlignment = Alignment.Center) {
        if (card.state != CardState.HIDDEN) {
            Text(text = card.symbol, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    JuegoMemoriaTheme {
        LevelSelectScreen(onLevelSelected = {}, onBack = {})
    }
}
