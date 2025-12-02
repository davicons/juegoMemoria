package com.example.juegomemoria

/**
 * JUEGO DE MEMORIA - MainActivity.kt
 * 
 * Este es el archivo principal de la aplicación de juego de memoria.
 * Desarrollado con Jetpack Compose para Android, implementa un juego clásico
 * de memoria con múltiples niveles de dificultad.
 * 
 * Características principales:
 * - Navegación entre pantallas usando Sealed Classes
 * - Sistema de sonidos para feedback del usuario
 * - Soporte para modo claro y oscuro
 * - Cronómetro y límite de movimientos por nivel
 * - Modo relax sin restricciones de tiempo
 * 
 * @author Ronald D. Condori
 */

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.juegomemoria.ui.theme.JuegoMemoriaTheme
import kotlinx.coroutines.delay

// ============================================================================
// MODELOS DE DATOS Y ESTADO DE PANTALLA
// ============================================================================
// Utilizamos Sealed Classes para definir las diferentes pantallas del juego.
// Esto nos permite tener un tipo seguro de navegación y facilita la gestión
// del estado de la aplicación.
// ============================================================================
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

/**
 * Data class que define la configuración de un nivel del juego.
 * @param tarjetas Lista de símbolos únicos que se usarán (cada uno aparece duplicado)
 * @param movimientosMax Límite máximo de movimientos permitidos (0 = ilimitado)
 * @param timeLimitSeconds Tiempo límite en segundos para completar el nivel
 * @param columns Número de columnas en la cuadrícula del juego
 */
data class LevelData(
    val tarjetas: List<String>,
    val movimientosMax: Int,
    val timeLimitSeconds: Int,
    val columns: Int
)

/**
 * Definición de los 5 niveles del juego con dificultad progresiva.
 * La curva de dificultad aumenta gradualmente en:
 * - Cantidad de tarjetas (de 4 a 16)
 * - Límite de movimientos (de 5 a 30)
 * - Tiempo disponible (de 15 a 90 segundos)
 * - Columnas del grid (de 2x2 a 4x4)
 */
private val levels = listOf(
    LevelData(tarjetas = allSymbols.take(2), movimientosMax = 5, timeLimitSeconds = 15, columns = 2),   // Nivel 1: 2x2 (4 tarjetas)
    LevelData(tarjetas = allSymbols.take(3), movimientosMax = 8, timeLimitSeconds = 25, columns = 2),   // Nivel 2: 2x3 (6 tarjetas)
    LevelData(tarjetas = allSymbols.take(4), movimientosMax = 12, timeLimitSeconds = 40, columns = 2),  // Nivel 3: 2x4 (8 tarjetas)
    LevelData(tarjetas = allSymbols.take(6), movimientosMax = 20, timeLimitSeconds = 60, columns = 3),  // Nivel 4: 3x4 (12 tarjetas)
    LevelData(tarjetas = allSymbols.take(8), movimientosMax = 30, timeLimitSeconds = 90, columns = 4)   // Nivel 5: 4x4 (16 tarjetas)
)

/**
 * Enum que define los estados posibles de una tarjeta:
 * - HIDDEN: Ocultas (cara boca abajo)
 * - DISCOVERED: Descubierta temporalmente (cara visible)
 * - MATCHED: Emparejada correctamente (ya no es clickeable)
 * - ERROR: Estado de error (no se usa actualmente, reservado para futuras mejoras)
 */
enum class CardState { HIDDEN, DISCOVERED, MATCHED, ERROR }

/**
 * Data class que representa una tarjeta individual del juego.
 * @param symbol El emoji o símbolo que muestra la tarjeta
 * @param state El estado actual de la tarjeta
 * @param id Identificador único para distinguir tarjetas con el mismo símbolo
 */
data class Card(val symbol: String, val state: CardState = CardState.HIDDEN, val id: Int = 0)

// ============================================================================
// SISTEMA DE SONIDO
// ============================================================================
/**
 * Clase responsable de gestionar todos los efectos de sonido del juego.
 * Utiliza SoundPool para una reproducción eficiente de sonidos cortos.
 * 
 * Sonidos implementados:
 * - flip: Se reproduce al voltear una tarjeta
 * - match: Se reproduce cuando dos tarjetas coinciden
 * - error: Se reproduce cuando dos tarjetas no coinciden
 */
class SoundPlayer(context: Context) {
    // SoundPool permite reproducir múltiples sonidos simultáneamente
    private val soundPool: SoundPool = SoundPool.Builder().setMaxStreams(3).build()
    private var areSoundsLoaded = false
    private val soundIds = mutableMapOf<String, Int>()

    init {
        // Mapeo de nombres de sonidos a recursos de audio
        val soundsToLoad: Map<String, Int> = mapOf(
            "flip" to R.raw.carta, 
            "match" to R.raw.acierto, 
            "error" to R.raw.error
        )
        var loadedCount = 0
        
        // Listener que verifica cuando todos los sonidos han sido cargados
        soundPool.setOnLoadCompleteListener { _, _, status -> 
            if (status == 0) { 
                loadedCount++
                if (loadedCount == soundsToLoad.size) areSoundsLoaded = true 
            } 
        }
        
        // Carga todos los sonidos de forma asíncrona
        soundsToLoad.forEach { (name, resId) -> 
            soundIds[name] = soundPool.load(context, resId, 1) 
        }
    }

    /**
     * Reproduce un sonido por su nombre si ya está cargado.
     * Parámetros del play: id, volumen izquierdo, volumen derecho, prioridad, loop, velocidad
     */
    private fun playSound(name: String) {
        if (areSoundsLoaded) soundIds[name]?.let { 
            soundPool.play(it, 1f, 1f, 0, 0, 1f) 
        }
    }

    // Funciones públicas para reproducir sonidos específicos
    fun playFlipSound() = playSound("flip")
    fun playMatchSound() = playSound("match")
    fun playErrorSound() = playSound("error")
    
    /**
     * Libera los recursos del SoundPool cuando ya no se necesita.
     * IMPORTANTE: Debe llamarse en onDestroy() para evitar memory leaks.
     */
    fun release() = soundPool.release()
}

// ============================================================================
// ACTIVITY PRINCIPAL Y NAVEGACIÓN
// ============================================================================
/**
 * Activity principal de la aplicación.
 * Gestiona el ciclo de vida de la app y coordina la navegación entre pantallas.
 */
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var soundPlayer: SoundPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inicializa el reproductor de sonidos
        soundPlayer = SoundPlayer(this)

        setContent {
            // Aplica el tema de Material Design 3 con soporte para modo claro/oscuro
            JuegoMemoriaTheme {
                ElectricBackground {
                    // Estado que controla en qué pantalla nos encontramos
                    // Usamos remember para mantener el estado durante recomposiciones
                    var screen by remember { mutableStateOf<GameScreen>(GameScreen.Welcome) }

                    // Sistema de navegación basado en Sealed Classes
                    // Cada pantalla tiene callbacks para navegar a otras pantallas
                    when (val currentScreen = screen) {
                        is GameScreen.Welcome -> WelcomeScreen(
                            onNormalClick = { screen = GameScreen.LevelSelect(relaxMode = false) },
                            onRelaxClick = { screen = GameScreen.LevelSelect(relaxMode = true) }
                        )
                        is GameScreen.LevelSelect -> LevelSelectScreen(
                            onLevelSelected = { index -> 
                                screen = GameScreen.Playing(startLevelIndex = index, relaxMode = currentScreen.relaxMode) 
                            },
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
    }

    /**
     * Limpia los recursos cuando la Activity se destruye.
     * IMPORTANTE: Libera el SoundPool para evitar memory leaks.
     */
    override fun onDestroy() {
        super.onDestroy()
        soundPlayer.release()
    }
}

/**
 * Fondo decorativo con gradiente que se adapta al tema del sistema.
 * 
 * Características:
 * - Detecta automáticamente si el sistema está en modo oscuro
 * - Aplica diferentes gradientes según el tema
 * - Proporciona un fondo visualmente atractivo para toda la aplicación
 */
@Composable
fun ElectricBackground(content: @Composable () -> Unit) {
    // Detecta si el sistema está configurado en modo oscuro
    val isDark = isSystemInDarkTheme()
    
    // Colores adaptativos según el tema detectado
    val electricColors = if (isDark) {
        // Modo oscuro: gradiente azul oscuro para proteger la vista
        listOf(
            Color(0xFF1A1A2E), // Azul noche
            Color(0xFF16213E), // Azul oscuro
            Color(0xFF0F3460)  // Azul profundo
        )
    } else {
        // Modo claro: gradiente azul claro y suave
        listOf(
            Color(0xFFE3F2FD), // Azul muy claro
            Color(0xFFBBDEFB), // Azul claro
            Color(0xFF90CAF9)  // Azul medio claro
        )
    }
    
    // Contenedor con gradiente vertical que ocupa toda la pantalla
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(electricColors))
    ) {
        // Contenido que se renderiza sobre el fondo
        content()
    }
}

// ============================================================================
// PANTALLAS DE LA APLICACIÓN
// ============================================================================
/**
 * Pantalla de bienvenida inicial del juego.
 * Permite al usuario elegir entre modo normal o modo relax.
 * 
 * @param onNormalClick Callback para iniciar el juego en modo normal
 * @param onRelaxClick Callback para iniciar el juego en modo relax
 */
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
            Text(
                text = "👋 ¡Bienvenido! 👋",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
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

/**
 * Pantalla de selección de nivel.
 * Muestra todos los niveles disponibles en una lista scrollable.
 * Utiliza LazyColumn para un rendimiento óptimo con muchos elementos.
 * 
 * @param onLevelSelected Callback cuando el usuario selecciona un nivel
 * @param onBack Callback para volver a la pantalla anterior
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelSelectScreen(onLevelSelected: (Int) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Selecciona un Nivel") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }
            )
        },
        containerColor = Color.Transparent
    ) {
        LazyColumn(modifier = Modifier.padding(it).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(levels.size) { index ->
                Button(onClick = { onLevelSelected(index) }, modifier = Modifier.fillMaxWidth()) { Text("Nivel ${index + 1}") }
            }
        }
    }
}

// ============================================================================
// LÓGICA PRINCIPAL DEL JUEGO
// ============================================================================
/**
 * Genera la lista de tarjetas para el juego.
 * Duplica cada símbolo (porque cada tarjeta necesita una pareja) y las baraja.
 * 
 * @param symbols Lista de símbolos únicos a usar
 * @return Lista de tarjetas barajadas con estados iniciales
 */
fun generateCards(symbols: List<String>): List<Card> {
    val allCards = symbols + symbols  // Duplica cada símbolo para crear parejas
    return allCards.shuffled().mapIndexed { index, symbol -> 
        Card(symbol = symbol, id = index)  // Cada tarjeta tiene un ID único
    }
}

/**
 * Formatea números para mostrarlos siempre con dos dígitos.
 * Útil para mostrar el tiempo en formato MM:SS o números de nivel.
 * 
 * @param num Número a formatear
 * @return String con el número formateado (ej: 5 -> "05", 15 -> "15")
 */
fun formatNumber(num: Int): String = if (num < 10) "0$num" else "$num"

/**
 * Componente principal del juego de memoria.
 * 
 * Esta es la pantalla más compleja y contiene:
 * - Gestión del estado del juego (tarjetas, movimientos, tiempo)
 * - Lógica de comparación de tarjetas
 * - Sistema de cronómetro
 * - Detección de condiciones de victoria/derrota
 * - Navegación automática entre niveles
 * 
 * @param soundPlayer Reproductor de sonidos del juego
 * @param startLevelIndex Índice del nivel en el que comenzar
 * @param relaxMode Si es true, desactiva límites de tiempo y movimientos
 * @param onNavigateBack Callback para volver al menú de niveles
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameMemoryApp(soundPlayer: SoundPlayer?, startLevelIndex: Int, relaxMode: Boolean, onNavigateBack: () -> Unit) {
    // Estado del nivel actual (puede cambiar durante el juego)
    var currentLevelIndex by remember { mutableStateOf(startLevelIndex) }
    val currentLevel = levels.getOrElse(currentLevelIndex) { levels.first() }

    // ========================================================================
    // ESTADO QUE SE REINICIA AL CAMBIAR DE NIVEL
    // ========================================================================
    // Estas variables se reinician automáticamente cuando 'currentLevelIndex' cambia
    // gracias a la key del remember(). Esto es crucial para la transición entre niveles.
    
    var cards by remember(currentLevelIndex) { 
        mutableStateOf(generateCards(currentLevel.tarjetas)) 
    }
    var movimientos by remember(currentLevelIndex) { mutableStateOf(0) }
    var segundos by remember(currentLevelIndex) { 
        mutableStateOf(currentLevel.timeLimitSeconds % 60) 
    }
    var minutos by remember(currentLevelIndex) { 
        mutableStateOf(currentLevel.timeLimitSeconds / 60) 
    }

    // ========================================================================
    // ESTADO TRANSITORIO DENTRO DE UN NIVEL
    // ========================================================================
    // Estas variables no se reinician al cambiar de nivel porque no dependen
    // del currentLevelIndex en el remember()
    
    var menuExpanded by remember { mutableStateOf(false) }  // Control del menú desplegable
    var isComparing by remember { mutableStateOf(false) }    // Indica si se están comparando tarjetas

    // ========================================================================
    // ESTADOS DERIVADOS (calculados automáticamente)
    // ========================================================================
    
    // Verifica si todas las tarjetas están emparejadas (nivel completado)
    val levelComplete by remember(cards) { 
        derivedStateOf { 
            cards.all { it.state == CardState.MATCHED } && cards.isNotEmpty() 
        } 
    }
    
    // Verifica si se excedió el límite de movimientos
    val movesExceeded = !relaxMode && currentLevel.movimientosMax > 0 && 
                        movimientos >= currentLevel.movimientosMax
    
    // Verifica si se acabó el tiempo
    val timeUp = !relaxMode && minutos == 0 && segundos == 0
    
    // El juego termina si se excede límite de movimientos o tiempo, pero solo si no está completado
    val isGameOver = (movesExceeded || timeUp) && !levelComplete

    // ========================================================================
    // LÓGICA DE COMPARACIÓN DE TARJETAS
    // ========================================================================
    /**
     * LaunchedEffect se ejecuta cada vez que cambia el estado de 'cards'.
     * Detecta cuando hay exactamente 2 tarjetas descubiertas y las compara.
     * 
     * Flujo:
     * 1. Filtra las tarjetas en estado DISCOVERED
     * 2. Si hay 2, las compara
     * 3. Si coinciden: las marca como MATCHED y reproduce sonido de acierto
     * 4. Si no coinciden: espera 1 segundo y las vuelve a ocultar
     */
    LaunchedEffect(cards) {
        val discoveredCards = cards.filter { it.state == CardState.DISCOVERED }

        if (discoveredCards.size == 2) {
            isComparing = true  // Bloquea nuevos clicks mientras compara
            movimientos++       // Incrementa el contador de movimientos
            
            val card1 = discoveredCards[0]
            val card2 = discoveredCards[1]

            if (card1.symbol == card2.symbol) {
                // ¡Emparejamiento exitoso!
                soundPlayer?.playMatchSound()
                // Marca ambas tarjetas como emparejadas (ya no serán clickeables)
                cards = cards.map { 
                    if (it.state == CardState.DISCOVERED) 
                        it.copy(state = CardState.MATCHED) 
                    else 
                        it 
                }
            } else {
                // No coinciden: vuelve a ocultar las tarjetas después de 1 segundo
                soundPlayer?.playErrorSound()
                delay(1000L)  // Espera para que el usuario vea las tarjetas
                cards = cards.map { 
                    if (it.state == CardState.DISCOVERED) 
                        it.copy(state = CardState.HIDDEN) 
                    else 
                        it 
                }
            }
            isComparing = false  // Permite nuevos clicks
        }
    }

    // ========================================================================
    // SISTEMA DE CRONÓMETRO
    // ========================================================================
    /**
     * LaunchedEffect que gestiona el cronómetro del juego.
     * Solo funciona en modo normal (no en relax) y cuando no estamos en modo preview.
     * 
     * Se reinicia cuando:
     * - Cambia el nivel (currentLevelIndex)
     * - El juego termina (isGameOver)
     * - Se completa el nivel (levelComplete)
     * 
     * El cronómetro cuenta regresivamente desde el tiempo límite del nivel.
     */
    if (!relaxMode && !LocalInspectionMode.current) {
        LaunchedEffect(currentLevelIndex, isGameOver, levelComplete) {
            val level = levels.getOrElse(currentLevelIndex) { levels.first() }
            var totalSeconds = level.timeLimitSeconds
            
            // Inicializa el tiempo en formato minutos:segundos
            minutos = totalSeconds / 60
            segundos = totalSeconds % 60
            
            // Bucle que decrementa el tiempo cada segundo
            // Se detiene si se acaba el tiempo, el juego termina, o se completa el nivel
            while (totalSeconds > 0 && !isGameOver && !levelComplete) {
                delay(1000L)  // Espera 1 segundo
                totalSeconds--
                // Actualiza los minutos y segundos en el estado
                minutos = totalSeconds / 60
                segundos = totalSeconds % 60
            }
        }
    }

    // ========================================================================
    // INTERFAZ DE USUARIO (UI)
    // ========================================================================
    /**
     * Scaffold proporciona una estructura básica de Material Design con:
     * - TopAppBar: Barra superior con título, navegación y menú
     * - Contenido principal: Grid de tarjetas y controles
     */
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Nivel ${formatNumber(currentLevelIndex + 1)}") },
                navigationIcon = { 
                    IconButton(onClick = onNavigateBack) { 
                        Icon(Icons.Default.ArrowBack, "Volver") 
                    } 
                },
                actions = {
                    // Menú desplegable para cambiar de nivel rápidamente
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { 
                            Icon(Icons.Default.MoreVert, "Menú") 
                        }
                        DropdownMenu(
                            expanded = menuExpanded, 
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            levels.forEachIndexed { index, _ ->
                                DropdownMenuItem(
                                    text = { Text("Nivel ${index + 1}") }, 
                                    onClick = { 
                                        currentLevelIndex = index
                                        menuExpanded = false 
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        containerColor = Color.Transparent  // Permite ver el fondo con gradiente
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp), 
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ================================================================
            // BARRA DE INFORMACIÓN (Movimientos y Tiempo)
            // ================================================================
            Row(
                horizontalArrangement = Arrangement.SpaceAround, 
                modifier = Modifier.fillMaxWidth()
            ) {
                // Muestra movimientos con límite si aplica
                val movesText = if (!relaxMode && currentLevel.movimientosMax > 0) 
                    "$movimientos / ${currentLevel.movimientosMax}" 
                else 
                    "$movimientos"
                
                Text(
                    text = "Movimientos: $movesText",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                // Muestra el cronómetro solo en modo normal
                if (!relaxMode) Text(
                    text = "Tiempo: ${formatNumber(minutos)}:${formatNumber(segundos)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // ================================================================
            // GRID DE TARJETAS
            // ================================================================
            /**
             * LazyVerticalGrid renderiza solo las tarjetas visibles en pantalla,
             * mejorando el rendimiento con muchas tarjetas.
             * 
             * Características:
             * - Número de columnas dinámico según el nivel
             * - Scroll deshabilitado (las tarjetas deben caber en pantalla)
             * - Espaciado uniforme entre tarjetas
             */
            LazyVerticalGrid(
                columns = GridCells.Fixed(currentLevel.columns),  // Columnas según el nivel
                modifier = Modifier.weight(1f),  // Ocupa el espacio disponible
                userScrollEnabled = false,  // No permite scroll (diseño fijo)
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cards.size) { index ->
                    val card = cards[index]
                    CardView(
                        card = card, 
                        isClickable = !isComparing,  // Bloquea clicks durante comparación
                        onClick = {
                            // Solo permite voltear tarjetas ocultas si hay menos de 2 descubiertas
                            if (card.state == CardState.HIDDEN && 
                                cards.count { it.state == CardState.DISCOVERED } < 2) {
                                soundPlayer?.playFlipSound()  // Sonido de voltear
                                // Crea una nueva lista con la tarjeta actualizada
                                cards = cards.toMutableList().also { 
                                    it[index] = card.copy(state = CardState.DISCOVERED) 
                                }
                            }
                        }
                    )
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
                // ============================================================
                // LÓGICA DE CONDICIONES DE FINALIZACIÓN
                // ============================================================
                when {
                    levelComplete -> {
                        if (currentLevelIndex < levels.size - 1) {
                            // Nivel intermedio superado: avanza automáticamente al siguiente
                            Text(
                                text = "¡Nivel superado!",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            // Efecto que espera 2 segundos antes de avanzar al siguiente nivel
                            // Esto da tiempo al usuario para ver el mensaje de éxito
                            LaunchedEffect(levelComplete) {
                                if (levelComplete) {
                                    delay(2000L)  // Espera 2 segundos
                                    currentLevelIndex++  // Avanza automáticamente al siguiente nivel
                                }
                            }
                        } else {
                            // Último nivel superado: el jugador ha ganado el juego completo
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "¡Has ganado!",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Button(onClick = onNavigateBack) { 
                                    Text("Menú de niveles") 
                                }
                            }
                        }
                    }
                    isGameOver -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val reason = if (movesExceeded) "Límite de movimientos" else "Se acabó el tiempo"
                        Text(
                            text = "¡Juego Terminado! - $reason",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Button(onClick = onNavigateBack) { Text("Menú de niveles") }
                    }
                    else -> Button(onClick = {
                        // Botón para reiniciar el nivel manualmente
                        // Truco: Cambiar temporalmente el índice fuerza la re-inicialización
                        // de todas las variables que dependen de currentLevelIndex
                        val currentIdx = currentLevelIndex
                        currentLevelIndex = -1  // Fuerza la re-inicialización
                        currentLevelIndex = currentIdx  // Restaura el nivel
                    }) { Text("Reiniciar Nivel") }
                }
            }
        }
    }
}

/**
 * Componente visual que representa una tarjeta individual del juego.
 * 
 * Características:
 * - Animación de desvanecimiento cuando se empareja
 * - Colores adaptativos según el estado (normal, emparejada, error)
 * - Solo clickeable cuando está oculta y no se están comparando tarjetas
 * - Soporte para modo claro/oscuro a través de MaterialTheme
 * 
 * @param card Datos de la tarjeta (símbolo, estado, ID)
 * @param isClickable Si false, bloquea los clicks (útil durante comparaciones)
 * @param onClick Callback cuando se hace click en la tarjeta
 */
@Composable
fun CardView(card: Card, isClickable: Boolean, onClick: () -> Unit) {
    // Animación de transparencia: las tarjetas emparejadas se desvanecen
    val alpha by animateFloatAsState(
        targetValue = if (card.state == CardState.MATCHED) 0f else 1f,
        animationSpec = tween(durationMillis = 500)  // Animación de 500ms
    )

    // Color de fondo según el estado de la tarjeta
    val cardColor = when (card.state) {
        CardState.ERROR -> Color.Red.copy(alpha = 0.5f)        // Error (no usado actualmente)
        CardState.MATCHED -> Color.Green.copy(alpha = 0.5f)    // Emparejada correctamente
        else -> MaterialTheme.colorScheme.surface              // Estado normal (usa color del tema)
    }
    
    Box(
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }  // Aplica la animación de desvanecimiento
            .sizeIn(minWidth = 60.dp, minHeight = 80.dp)  // Tamaño mínimo de la tarjeta
            .background(cardColor, shape = RoundedCornerShape(8.dp))  // Fondo con bordes redondeados
            .padding(4.dp)
            // Solo permite clicks si la tarjeta está oculta y el juego permite clicks
            .clickable(enabled = card.state == CardState.HIDDEN && isClickable) { 
                onClick() 
            }
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))  // Borde del color primario
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Muestra el símbolo solo si la tarjeta no está oculta
        if (card.state != CardState.HIDDEN) {
            Text(
                text = card.symbol,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface  // Color del texto adaptativo al tema
            )
        }
    }
}

// ============================================================================
// PREVIEW PARA ANDROID STUDIO
// ============================================================================
/**
 * Preview que permite visualizar la UI en Android Studio sin ejecutar la app.
 * Útil para desarrollo rápido y verificar cambios visuales.
 * 
 * Nota: El Preview usa valores de ejemplo y no incluye toda la funcionalidad
 * del juego (como sonidos o navegación completa).
 */
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    JuegoMemoriaTheme {
        LevelSelectScreen(onLevelSelected = {}, onBack = {})
    }
}
