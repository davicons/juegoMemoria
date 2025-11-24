package com.example.juegomemoria

import android.content.Context
import android.media.SoundPool
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

// Lista única de todos los símbolos disponibles para una progresión más suave.
private val allSymbols = listOf(
    "🦄", "🍦", "🌈", "👽", "👾", "🤖", "👹", "👺",
    "🤡", "💩", "🎃", "🙀", "☠️", "😽", "😼", "🦊"
).shuffled()

data class LevelData(
    val tarjetas: List<String>,
    val movimientosMax: Int,
    val timeLimitSeconds: Int,
    val columns: Int
)

// Nueva definición de niveles con una curva de dificultad más gradual.
private val levels = listOf(
    LevelData(tarjetas = allSymbols.take(2), movimientosMax = 5, timeLimitSeconds = 15, columns = 2),   // Nivel 1: 2x2 (4 tarjetas)
    LevelData(tarjetas = allSymbols.take(3), movimientosMax = 8, timeLimitSeconds = 25, columns = 2),   // Nivel 2: 2x3 (6 tarjetas)
    LevelData(tarjetas = allSymbols.take(4), movimientosMax = 12, timeLimitSeconds = 40, columns = 2),  // Nivel 3: 2x4 (8 tarjetas)
    LevelData(tarjetas = allSymbols.take(6), movimientosMax = 20, timeLimitSeconds = 60, columns = 3),  // Nivel 4: 3x4 (12 tarjetas)
    LevelData(tarjetas = allSymbols.take(8), movimientosMax = 30, timeLimitSeconds = 90, columns = 4)   // Nivel 5: 4x4 (16 tarjetas)
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("👋 ¡Bienvenido! 👋", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onNormalClick, modifier = Modifier.fillMaxWidth()) { Text("¡Quiero jugar!") }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRelaxClick, modifier = Modifier.fillMaxWidth()) { Text("Modo relax") }
        }

        Text(
            text = "Desarrollado por: Ronald D. Condori",
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
        LazyColumn(modifier = Modifier.padding(it).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
fun GameMemoryApp(soundPlayer: SoundPlayer?, startLevelIndex: Int, relaxMode: Boolean, onNavigateBack: () -> Unit) {
    var currentLevelIndex by remember { mutableStateOf(startLevelIndex) }
    val currentLevel = levels.getOrElse(currentLevelIndex) { levels.first() }

    // Estas variables ahora se reinician AUTOMÁTICAMENTE cuando 'currentLevelIndex' cambia
    var cards by remember(currentLevelIndex) { mutableStateOf(generateCards(currentLevel.tarjetas)) }
    var movimientos by remember(currentLevelIndex) { mutableStateOf(0) }
    var segundos by remember(currentLevelIndex) { mutableStateOf(currentLevel.timeLimitSeconds % 60) }
    var minutos by remember(currentLevelIndex) { mutableStateOf(currentLevel.timeLimitSeconds / 60) }

    // Estas son variables de estado transitorias dentro de un nivel
    var menuExpanded by remember { mutableStateOf(false) }
    var isComparing by remember { mutableStateOf(false) }
    var levelTransitioning by remember(currentLevelIndex) { mutableStateOf(false) }

    val levelComplete by remember(cards, currentLevelIndex) { 
        derivedStateOf { cards.all { it.state == CardState.MATCHED } && cards.isNotEmpty() } 
    }
    val movesExceeded = !relaxMode && currentLevel.movimientosMax > 0 && movimientos >= currentLevel.movimientosMax
    val timeUp = !relaxMode && minutos == 0 && segundos == 0
    val isGameOver = (movesExceeded || timeUp) && !levelComplete

    // Efecto para la lógica de comparación de cartas
    LaunchedEffect(cards) {
        val discoveredCards = cards.filter { it.state == CardState.DISCOVERED }

        if (discoveredCards.size == 2) {
            isComparing = true
            movimientos++
            val card1 = discoveredCards[0]
            val card2 = discoveredCards[1]

            if (card1.symbol == card2.symbol) {
                soundPlayer?.playMatchSound()
                cards = cards.map { if (it.state == CardState.DISCOVERED) it.copy(state = CardState.MATCHED) else it }
            } else {
                soundPlayer?.playErrorSound()
                delay(1000L)
                cards = cards.map { if (it.state == CardState.DISCOVERED) it.copy(state = CardState.HIDDEN) else it }
            }
            isComparing = false
        }
    }

    // Efecto para el cronómetro
    if (!relaxMode && !LocalInspectionMode.current) {
        LaunchedEffect(currentLevelIndex, isGameOver, levelComplete) {
            // Reiniciar el tiempo cuando cambia el nivel
            val level = levels.getOrElse(currentLevelIndex) { levels.first() }
            var totalSeconds = level.timeLimitSeconds
            minutos = totalSeconds / 60
            segundos = totalSeconds % 60
            
            while (totalSeconds > 0 && !isGameOver && !levelComplete) {
                delay(1000L)
                totalSeconds--
                minutos = totalSeconds / 60
                segundos = totalSeconds % 60
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Nivel ${formatNumber(currentLevelIndex + 1)}") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Menú") }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            levels.forEachIndexed { index, _ ->
                                DropdownMenuItem(text = { Text("Nivel ${index + 1}") }, onClick = { currentLevelIndex = index; menuExpanded = false })
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()) {
                val movesText = if (!relaxMode && currentLevel.movimientosMax > 0) "$movimientos / ${currentLevel.movimientosMax}" else "$movimientos"
                Text("Movimientos: $movesText", style = MaterialTheme.typography.bodyLarge)
                if (!relaxMode) Text("Tiempo: ${formatNumber(minutos)}:${formatNumber(segundos)}", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(20.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(currentLevel.columns),
                modifier = Modifier.weight(1f),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cards.size) { index ->
                    val card = cards[index]
                    CardView(card = card, isClickable = !isComparing, onClick = {
                        if (card.state == CardState.HIDDEN && cards.count { it.state == CardState.DISCOVERED } < 2) {
                            soundPlayer?.playFlipSound()
                            cards = cards.toMutableList().also { it[index] = card.copy(state = CardState.DISCOVERED) }
                        }
                    })
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    levelComplete -> {
                        if (currentLevelIndex < levels.size - 1) {
                            // Nivel intermedio superado
                            Text("¡Nivel superado!", style = MaterialTheme.typography.headlineMedium)
                            // Efecto que se lanza cuando se completa un nivel
                            LaunchedEffect(levelComplete) {
                                if (levelComplete && !levelTransitioning && currentLevelIndex < levels.size - 1) {
                                    levelTransitioning = true
                                    delay(2000L) // Espera 2 segundos
                                    currentLevelIndex++
                                }
                            }
                        } else {
                            // Último nivel superado
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text("¡Has ganado!", style = MaterialTheme.typography.headlineMedium)
                                Button(onClick = onNavigateBack) { Text("Menú de niveles") }
                            }
                        }
                    }
                    isGameOver -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val reason = if (movesExceeded) "Límite de movimientos" else "Se acabó el tiempo"
                        Text("¡Juego Terminado! - $reason", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = onNavigateBack) { Text("Menú de niveles") }
                    }
                    else -> Button(onClick = {
                        // Reiniciar nivel manualmente
                        val currentIdx = currentLevelIndex
                        currentLevelIndex = -1 // Truco para forzar la re-inicialización
                        currentLevelIndex = currentIdx
                    }) { Text("Reiniciar Nivel") }
                }
            }
        }
    }
}

@Composable
fun CardView(card: Card, isClickable: Boolean, onClick: () -> Unit) {
    val alpha by animateFloatAsState(
        targetValue = if (card.state == CardState.MATCHED) 0f else 1f,
        animationSpec = tween(durationMillis = 500)
    )

    val cardColor = when (card.state) {
        CardState.ERROR -> Color.Red.copy(alpha = 0.5f)
        CardState.MATCHED -> Color.Green.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }
    Box(
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha } // Aplica la animación de desvanecimiento
            .sizeIn(minWidth = 60.dp, minHeight = 80.dp)
            .background(cardColor, shape = RoundedCornerShape(8.dp))
            .padding(4.dp)
            .clickable(enabled = card.state == CardState.HIDDEN && isClickable) { onClick() }
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
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
