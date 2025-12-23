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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.juegomemoria.CardState.MATCHED
import com.example.juegomemoria.data.AppDatabase
import com.example.juegomemoria.data.GameDao
import com.example.juegomemoria.data.GameHistory
import com.example.juegomemoria.data.GameRecord
import com.example.juegomemoria.data.PlayerStats
import com.example.juegomemoria.data.User
import com.example.juegomemoria.ui.theme.JuegoMemoriaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================================================
// MODELOS DE DATOS Y ESTADO DE PANTALLA
// ============================================================================
// Utilizamos Sealed Classes para definir las diferentes pantallas del juego.
// Esto nos permite tener un tipo seguro de navegación y facilita la gestión
// del estado de la aplicación.
// ============================================================================


sealed class GameScreen {
    object Login : GameScreen()
    object Register : GameScreen()
    data class Welcome(val userId: Int, val username: String) : GameScreen()
    data class LevelSelect(val userId: Int, val username: String, val relaxMode: Boolean) : GameScreen()
    data class Playing(val userId: Int, val username: String, val startLevelIndex: Int, val relaxMode: Boolean) : GameScreen()
    data class Stats(val userId: Int, val username: String) : GameScreen()
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
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inicializa el reproductor de sonidos
        soundPlayer = SoundPlayer(this)
        // Inicializa la base de datos
        database = AppDatabase.getDatabase(this)

        setContent {
            // Aplica el tema de Material Design 3 con soporte para modo claro/oscuro
            JuegoMemoriaTheme {
                ElectricBackground {
                    // Estado que controla en qué pantalla nos encontramos
                    // Usamos remember para mantener el estado durante recomposiciones
                    // Ahora inicia en Login
                    var screen by remember { mutableStateOf<GameScreen>(GameScreen.Login) }

                    // Sistema de navegación basado en Sealed Classes
                    // Cada pantalla tiene callbacks para navegar a otras pantallas
                    when (val currentScreen = screen) {
                        is GameScreen.Login -> LoginScreen(
                            gameDao = database.gameDao(),
                            onLoginSuccess = { userId, username ->
                                screen = GameScreen.Welcome(userId = userId, username = username)
                            },
                            onRegisterClick = { screen = GameScreen.Register }
                        )
                        is GameScreen.Register -> RegisterScreen(
                            gameDao = database.gameDao(),
                            onRegisterSuccess = { screen = GameScreen.Login },
                            onBackToLogin = { screen = GameScreen.Login }
                        )
                        is GameScreen.Welcome -> WelcomeScreen(
                            username = currentScreen.username,
                            onNormalClick = { screen = GameScreen.LevelSelect(userId = currentScreen.userId, username = currentScreen.username, relaxMode = false) },
                            onRelaxClick = { screen = GameScreen.LevelSelect(userId = currentScreen.userId, username = currentScreen.username, relaxMode = true) },
                            onStatsClick = { screen = GameScreen.Stats(userId = currentScreen.userId, username = currentScreen.username) },
                            onLogout = { screen = GameScreen.Login }
                        )
                        is GameScreen.LevelSelect -> LevelSelectScreen(
                            onLevelSelected = { index ->
                                screen = GameScreen.Playing(userId = currentScreen.userId, username = currentScreen.username, startLevelIndex = index, relaxMode = currentScreen.relaxMode)
                            },
                            onBack = { screen = GameScreen.Welcome(userId = currentScreen.userId, username = currentScreen.username) }
                        )
                        is GameScreen.Playing -> GameMemoryApp(
                            soundPlayer = soundPlayer,
                            gameDao = database.gameDao(),
                            userId = currentScreen.userId,
                            startLevelIndex = currentScreen.startLevelIndex,
                            relaxMode = currentScreen.relaxMode,
                            onNavigateBack = { screen = GameScreen.LevelSelect(userId = currentScreen.userId, username = currentScreen.username, relaxMode = currentScreen.relaxMode) }
                        )
                        is GameScreen.Stats -> StatsScreen(
                            gameDao = database.gameDao(),
                            userId = currentScreen.userId,
                            onBack = { screen = GameScreen.Welcome(userId = currentScreen.userId, username = currentScreen.username) }
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
 * Pantalla de inicio de sesion.
 * Permite al usuario iniciar sesion con su nombre de usuario y contrasena.
 */
@Composable
fun LoginScreen(
    gameDao: GameDao,
    onLoginSuccess: (userId: Int, username: String) -> Unit,
    onRegisterClick: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                text = "Juego de Memoria",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Iniciar Sesion",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it; errorMessage = null },
                label = { Text("Usuario") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("Contrasena") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        errorMessage = "Por favor completa todos los campos"
                        return@Button
                    }
                    isLoading = true
                    scope.launch {
                        val user = gameDao.validateUser(username.trim(), password)
                        isLoading = false
                        if (user != null) {
                            onLoginSuccess(user.id, user.username)
                        } else {
                            errorMessage = "Usuario o contrasena incorrectos"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Iniciar Sesion")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onRegisterClick) {
                Text("¿No tienes cuenta? Registrate")
            }
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
 * Pantalla de registro de nuevo usuario.
 */
@Composable
fun RegisterScreen(
    gameDao: GameDao,
    onRegisterSuccess: () -> Unit,
    onBackToLogin: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                text = "Crear Cuenta",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it; errorMessage = null },
                label = { Text("Usuario") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("Contrasena") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; errorMessage = null },
                label = { Text("Confirmar Contrasena") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    when {
                        username.isBlank() || password.isBlank() || confirmPassword.isBlank() -> {
                            errorMessage = "Por favor completa todos los campos"
                        }
                        username.trim().length < 3 -> {
                            errorMessage = "El usuario debe tener al menos 3 caracteres"
                        }
                        password.length < 4 -> {
                            errorMessage = "La contrasena debe tener al menos 4 caracteres"
                        }
                        password != confirmPassword -> {
                            errorMessage = "Las contrasenas no coinciden"
                        }
                        else -> {
                            isLoading = true
                            scope.launch {
                                val exists = gameDao.usernameExists(username.trim())
                                if (exists > 0) {
                                    errorMessage = "El nombre de usuario ya existe"
                                    isLoading = false
                                } else {
                                    gameDao.insertUser(User(
                                        username = username.trim(),
                                        password = password
                                    ))
                                    isLoading = false
                                    onRegisterSuccess()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Crear Cuenta")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onBackToLogin) {
                Text("Ya tengo cuenta, iniciar sesion")
            }
        }
    }
}

/**
 * Pantalla de bienvenida del juego.
 * Permite al usuario elegir entre modo normal, modo relax o ver estadisticas.
 *
 * @param username Nombre del usuario que inicio sesion
 * @param onNormalClick Callback para iniciar el juego en modo normal
 * @param onRelaxClick Callback para iniciar el juego en modo relax
 * @param onStatsClick Callback para ver las estadisticas
 * @param onLogout Callback para cerrar sesion
 */
@Composable
fun WelcomeScreen(
    username: String,
    onNormalClick: () -> Unit,
    onRelaxClick: () -> Unit,
    onStatsClick: () -> Unit,
    onLogout: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Boton de logout en la esquina superior derecha
        TextButton(
            onClick = onLogout,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Text("Cerrar Sesion")
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Hola, $username",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onNormalClick, modifier = Modifier.fillMaxWidth()) { Text("Quiero jugar!") }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRelaxClick, modifier = Modifier.fillMaxWidth()) { Text("Modo relax") }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onStatsClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Estadisticas")
            }
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
 * - Guardado de resultados en base de datos
 *
 * @param soundPlayer Reproductor de sonidos del juego
 * @param gameDao DAO para acceder a la base de datos
 * @param userId ID del usuario actual
 * @param startLevelIndex Índice del nivel en el que comenzar
 * @param relaxMode Si es true, desactiva límites de tiempo y movimientos
 * @param onNavigateBack Callback para volver al menú de niveles
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameMemoryApp(soundPlayer: SoundPlayer?, gameDao: GameDao?, userId: Int, startLevelIndex: Int, relaxMode: Boolean, onNavigateBack: () -> Unit) {
    // Estado del nivel actual (puede cambiar durante el juego)
    var currentLevelIndex by remember { mutableStateOf(startLevelIndex) }
    val currentLevel = levels.getOrElse(currentLevelIndex) { levels.first() }
    
    // ========================================================================
    // CONTADOR DE REINICIO PARA SINCRONIZAR TODOS LOS ESTADOS
    // ========================================================================
    // Este contador se incrementa cada vez que se reinicia el nivel.
    // Se usa como key en los remember() para forzar la reinicialización
    // de todas las variables de estado relacionadas con el nivel.
    var restartCounter by remember { mutableStateOf(0) }
    
    // Key compuesta que incluye el nivel y el contador de reinicio
    // Esto asegura que todo se reinicie cuando cambia el nivel o se presiona reiniciar
    val levelKey = currentLevelIndex to restartCounter

    // ========================================================================
    // ESTADO QUE SE REINICIA AL CAMBIAR DE NIVEL O REINICIAR
    // ========================================================================
    // Estas variables se reinician automáticamente cuando 'levelKey' cambia.
    // Esto asegura que tanto el cambio de nivel como el reinicio funcionen correctamente.
    
    var cards by remember(levelKey) { 
        mutableStateOf(generateCards(currentLevel.tarjetas)) 
    }
    var movimientos by remember(levelKey) { mutableStateOf(0) }
    var segundos by remember(levelKey) { 
        mutableStateOf(currentLevel.timeLimitSeconds % 60) 
    }
    var minutos by remember(levelKey) { 
        mutableStateOf(currentLevel.timeLimitSeconds / 60) 
    }

    // ========================================================================
    // ESTADO TRANSITORIO DENTRO DE UN NIVEL
    // ========================================================================
    // Estas variables no se reinician al cambiar de nivel porque no dependen
    // del levelKey en el remember()

    var menuExpanded by remember { mutableStateOf(false) }  // Control del menú desplegable
    var isComparing by remember { mutableStateOf(false) }    // Indica si se están comparando tarjetas
    var elapsedTime by remember(levelKey) { mutableStateOf(0) }  // Tiempo transcurrido en segundos
    var hasSavedResult by remember(levelKey) { mutableStateOf(false) }  // Evita guardar múltiples veces

    // ========================================================================
    // ESTADOS DERIVADOS (calculados automáticamente)
    // ========================================================================
    
    // Verifica si todas las tarjetas están emparejadas (nivel completado)
    val levelComplete by remember(cards) { 
        derivedStateOf { 
            cards.all { it.state == MATCHED } && cards.isNotEmpty()
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
                        it.copy(state = MATCHED)
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
     * - Se reinicia el nivel (restartCounter cambia)
     * - El juego termina (isGameOver)
     * - Se completa el nivel (levelComplete)
     * 
     * El cronómetro cuenta regresivamente desde el tiempo límite del nivel.
     * IMPORTANTE: Usa levelKey para reiniciarse correctamente cuando se presiona "Reiniciar Nivel"
     */
    if (!relaxMode && !LocalInspectionMode.current) {
        LaunchedEffect(levelKey, isGameOver, levelComplete) {
            val level = levels.getOrElse(currentLevelIndex) { levels.first() }
            var totalSeconds = level.timeLimitSeconds
            elapsedTime = 0

            // Inicializa el tiempo en formato minutos:segundos
            minutos = totalSeconds / 60
            segundos = totalSeconds % 60

            // Bucle que decrementa el tiempo cada segundo
            // Se detiene si se acaba el tiempo, el juego termina, o se completa el nivel
            while (totalSeconds > 0 && !isGameOver && !levelComplete) {
                delay(1000L)  // Espera 1 segundo
                totalSeconds--
                elapsedTime++  // Incrementa el tiempo transcurrido
                // Actualiza los minutos y segundos en el estado
                minutos = totalSeconds / 60
                segundos = totalSeconds % 60
            }
        }
    }

    // Cronometro para modo relax (cuenta hacia arriba)
    if (relaxMode && !LocalInspectionMode.current) {
        LaunchedEffect(levelKey, levelComplete) {
            elapsedTime = 0
            while (!levelComplete) {
                delay(1000L)
                elapsedTime++
            }
        }
    }

    // ========================================================================
    // GUARDADO DE RESULTADOS EN BASE DE DATOS
    // ========================================================================
    LaunchedEffect(levelComplete, isGameOver) {
        if ((levelComplete || isGameOver) && !hasSavedResult && gameDao != null) {
            hasSavedResult = true
            val timeSpent = if (relaxMode) elapsedTime else currentLevel.timeLimitSeconds - (minutos * 60 + segundos)

            // Guarda en historial
            gameDao.insertHistory(
                GameHistory(
                    userId = userId,
                    level = currentLevelIndex + 1,
                    moves = movimientos,
                    timeSpent = timeSpent,
                    completed = levelComplete,
                    relaxMode = relaxMode
                )
            )

            // Actualiza estadisticas del jugador
            gameDao.updateStatsAfterGame(userId, timeSpent, movimientos, levelComplete)

            // Si completo el nivel en modo normal, intenta actualizar el record
            if (levelComplete && !relaxMode) {
                gameDao.updateRecordIfBetter(userId, currentLevelIndex + 1, timeSpent, movimientos)
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
                                        restartCounter = 0  // Reinicia el contador al cambiar de nivel
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
                                    restartCounter = 0  // Reinicia el contador para el nuevo nivel
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
                        // Incrementa el contador de reinicio para forzar la reinicialización
                        // de todas las variables que usan levelKey como dependencia.
                        // Esto reinicia: tarjetas, movimientos, tiempo y cronómetro.
                        restartCounter++
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
        targetValue = if (card.state == MATCHED) 0f else 1f,
        animationSpec = tween(durationMillis = 500)  // Animación de 500ms
    )

    // Color de fondo según el estado de la tarjeta
    val cardColor = when (card.state) {
        CardState.ERROR -> Color.Red.copy(alpha = 0.5f)        // Error (no usado actualmente)
        MATCHED -> Color.Green.copy(alpha = 0.5f)    // Emparejada correctamente
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
// PANTALLA DE ESTADISTICAS
// ============================================================================
/**
 * Pantalla que muestra las estadisticas del jugador, records y historial.
 *
 * @param gameDao DAO para acceder a la base de datos
 * @param userId ID del usuario actual
 * @param onBack Callback para volver al menu principal
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(gameDao: GameDao, userId: Int, onBack: () -> Unit) {
    // Recolecta los datos de la base de datos para el usuario actual
    val playerStats by gameDao.getPlayerStatsFlow(userId).collectAsState(initial = null)
    val records by gameDao.getAllRecords(userId).collectAsState(initial = emptyList())
    val recentHistory by gameDao.getRecentHistory(userId, 10).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estadisticas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Seccion: Estadisticas Generales
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Estadisticas Generales",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (playerStats != null) {
                            val stats = playerStats!!
                            StatRow("Partidas jugadas", "${stats.totalGamesPlayed}")
                            StatRow("Partidas ganadas", "${stats.totalGamesWon}")
                            StatRow("Porcentaje de exito",
                                if (stats.totalGamesPlayed > 0)
                                    "${(stats.totalGamesWon * 100 / stats.totalGamesPlayed)}%"
                                else "0%"
                            )
                            StatRow("Tiempo total jugado", formatTime(stats.totalTimePlayed))
                            StatRow("Movimientos totales", "${stats.totalMoves}")
                            StatRow("Racha actual", "${stats.currentStreak}")
                            StatRow("Mejor racha", "${stats.bestStreak}")
                        } else {
                            Text(
                                text = "Aun no hay estadisticas. ¡Juega una partida!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Seccion: Records por Nivel
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Records por Nivel",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (records.isNotEmpty()) {
                            records.forEach { record ->
                                RecordRow(record)
                                if (record != records.last()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }
                        } else {
                            Text(
                                text = "Aun no hay records. ¡Completa un nivel en modo normal!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Seccion: Historial Reciente
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Historial Reciente",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (recentHistory.isNotEmpty()) {
                            recentHistory.forEach { history ->
                                HistoryRow(history)
                                if (history != recentHistory.last()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }
                        } else {
                            Text(
                                text = "Aun no hay historial de partidas.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecordRow(record: GameRecord) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Nivel ${record.level}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Completado ${record.timesCompleted} veces",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "Mejor tiempo: ${formatTime(record.bestTime)}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Menos movimientos: ${record.bestMoves}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistoryRow(history: GameHistory) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Nivel ${history.level}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (history.completed) "Ganada" else "Perdida",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (history.completed) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
                if (history.relaxMode) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(Relax)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "${history.moves} mov.", style = MaterialTheme.typography.bodySmall)
            Text(text = formatTime(history.timeSpent), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "${formatNumber(mins)}:${formatNumber(secs)}"
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
        WelcomeScreen(
            username = "Usuario",
            onNormalClick = {},
            onRelaxClick = {},
            onStatsClick = {},
            onLogout = {}
        )
    }
}
