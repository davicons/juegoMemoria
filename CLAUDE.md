# CLAUDE.md - JuegoMemoria

## Descripcion del Proyecto

JuegoMemoria es una aplicacion Android nativa de un juego clasico de memoria desarrollada en **Kotlin** con **Jetpack Compose** y **Room Database**. Incluye sistema de autenticacion local con login/registro y estadisticas por usuario. Proyecto educativo para el curso "Aplicaciones Moviles II".

## Compilacion

Proyecto compilado desde **Android Studio**. Usar los botones de la IDE:
- **Run** (Shift+F10): Ejecutar en dispositivo/emulador
- **Build > Make Project** (Ctrl+F9): Compilar
- **Build > Generate Signed APK**: Generar APK release
- **Build > Clean Project**: Limpiar build

## Arquitectura y Estructura

```
app/src/main/java/com/example/juegomemoria/
├── MainActivity.kt              # Archivo principal (~1400 lineas)
├── data/
│   ├── AppDatabase.kt           # Configuracion Room Database (version 2)
│   ├── GameDao.kt               # Data Access Object (consultas por usuario)
│   └── GameEntities.kt          # Entidades (User, GameRecord, GameHistory, PlayerStats)
└── ui/theme/
    ├── Color.kt                 # Paleta de colores
    ├── Theme.kt                 # Sistema de temas Material 3
    └── Type.kt                  # Tipografia

app/src/main/res/
├── raw/                         # Sonidos (acierto.mp3, carta.mp3, error.mp3)
└── values/                      # Recursos XML
```

## Stack Tecnologico

| Tecnologia | Version | Uso |
|------------|---------|-----|
| Kotlin | 2.0.21 | Lenguaje principal |
| Jetpack Compose | 2024.09.00 | UI declarativa |
| Room Database | 2.6.1 | Persistencia de datos |
| KSP | 2.0.21-1.0.27 | Procesador de anotaciones |
| Material Design 3 | - | Componentes UI |
| Android Gradle Plugin | 8.13.2 | Build system |
| Min SDK | 24 (Android 7.0) | Version minima |
| Target SDK | 36 (Android 15) | Version objetivo |

## Base de Datos (Room)

### Entidades

```kotlin
// Usuario registrado
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val password: String,
    val createdAt: Long
)

// Records por nivel y usuario (clave compuesta)
@Entity(tableName = "game_records", primaryKeys = ["userId", "level"])
data class GameRecord(
    val userId: Int,
    val level: Int,
    val bestTime: Int,
    val bestMoves: Int,
    val timesCompleted: Int,
    val lastPlayedDate: Long
)

// Historial de partidas por usuario
@Entity(tableName = "game_history")
data class GameHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val level: Int,
    val moves: Int,
    val timeSpent: Int,
    val completed: Boolean,
    val relaxMode: Boolean,
    val playedAt: Long
)

// Estadisticas por usuario
@Entity(tableName = "player_stats")
data class PlayerStats(
    @PrimaryKey val userId: Int,
    val totalGamesPlayed: Int,
    val totalGamesWon: Int,
    val totalTimePlayed: Int,
    val totalMoves: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val lastPlayedDate: Long
)
```

### DAO (Data Access Object)

**Metodos de Usuario:**
- `insertUser(user)`: Registrar nuevo usuario
- `getUserByUsername(username)`: Buscar usuario por nombre
- `validateUser(username, password)`: Validar credenciales
- `usernameExists(username)`: Verificar si existe el usuario

**Metodos de Juego (filtrados por userId):**
- `getAllRecords(userId)`: Flow de records del usuario
- `getRecordByLevel(userId, level)`: Record de un nivel especifico
- `updateRecordIfBetter(userId, level, time, moves)`: Actualiza si es mejor puntuacion
- `getAllHistory(userId)`: Flow del historial del usuario
- `getRecentHistory(userId, limit)`: Ultimas N partidas del usuario
- `getPlayerStats(userId)`: Estadisticas del usuario
- `updateStatsAfterGame(userId, timeSpent, moves, won)`: Actualiza estadisticas post-partida

## Sistema de Autenticacion

### Flujo de Navegacion

```
Login ──────────> Welcome(userId, username)
   │                    │
   ↓                    ├──> LevelSelect ──> Playing
Register                │
   │                    └──> Stats
   ↓
Login (despues de registrar)
```

### Caracteristicas

- **Autenticacion local**: Usuario y contrasena almacenados en SQLite
- **Estadisticas por usuario**: Cada usuario tiene sus propios records e historial
- **Sesion activa**: El usuario permanece logueado mientras la app este abierta
- **Logout**: Boton para cerrar sesion y volver a Login

### Validaciones de Registro

- Usuario: minimo 3 caracteres
- Contrasena: minimo 4 caracteres
- Confirmacion de contrasena
- Usuario unico (no duplicados)

## Componentes Principales

### Modelos de Datos (MainActivity.kt)

- **`sealed class GameScreen`**: Navegacion type-safe (Login, Register, Welcome, LevelSelect, Playing, Stats)
- **`data class LevelData`**: Configuracion de niveles (tarjetas, movimientos, tiempo, columnas)
- **`enum class CardState`**: Estados de tarjeta (HIDDEN, DISCOVERED, MATCHED, ERROR)
- **`data class Card`**: Modelo de tarjeta individual (symbol, state, id)

### Pantallas Composables

1. **LoginScreen**: Pantalla de inicio de sesion
2. **RegisterScreen**: Pantalla de registro de usuario
3. **WelcomeScreen**: Pantalla principal con seleccion de modo, estadisticas y logout
4. **LevelSelectScreen**: Seleccion de niveles (1-5)
5. **GameMemoryApp**: Logica principal del juego con guardado automatico por usuario
6. **StatsScreen**: Muestra estadisticas, records e historial del usuario actual
7. **CardView**: Componente visual de tarjeta

### Sistema de Sonido

Clase `SoundPlayer` usando `SoundPool`:
- `flip`: Voltear tarjeta
- `match`: Emparejamiento exitoso
- `error`: No coincidencia

## Configuracion de Niveles

| Nivel | Grid | Tarjetas | Mov. Max | Tiempo |
|-------|------|----------|----------|--------|
| 1 | 2x2 | 4 | 5 | 15s |
| 2 | 2x3 | 6 | 8 | 25s |
| 3 | 2x4 | 8 | 12 | 40s |
| 4 | 3x4 | 12 | 20 | 60s |
| 5 | 4x4 | 16 | 30 | 90s |

## Patrones de Codigo

### State Management
```kotlin
var cards by remember(levelKey) { mutableStateOf(generateCards(...)) }
```

### Side Effects Asincronos
```kotlin
LaunchedEffect(cards) {
    // Logica de comparacion de tarjetas
}
```

### Navegacion Type-Safe con Autenticacion
```kotlin
sealed class GameScreen {
    object Login : GameScreen()
    object Register : GameScreen()
    data class Welcome(val userId: Int, val username: String) : GameScreen()
    data class LevelSelect(val userId: Int, val username: String, val relaxMode: Boolean) : GameScreen()
    data class Playing(val userId: Int, val username: String, val startLevelIndex: Int, val relaxMode: Boolean) : GameScreen()
    data class Stats(val userId: Int, val username: String) : GameScreen()
}
```

### Acceso a Base de Datos con Usuario
```kotlin
// Recoleccion reactiva con Flow (filtrado por usuario)
val records by gameDao.getAllRecords(userId).collectAsState(initial = emptyList())
val playerStats by gameDao.getPlayerStatsFlow(userId).collectAsState(initial = null)

// Guardado asincrono en LaunchedEffect
LaunchedEffect(levelComplete) {
    gameDao.insertHistory(GameHistory(userId = userId, ...))
    gameDao.updateStatsAfterGame(userId, timeSpent, moves, won)
}
```

### Validacion de Login
```kotlin
scope.launch {
    val user = gameDao.validateUser(username, password)
    if (user != null) {
        onLoginSuccess(user.id, user.username)
    } else {
        errorMessage = "Usuario o contrasena incorrectos"
    }
}
```

## Modos de Juego

- **Normal**: Con limite de movimientos y tiempo, guarda records por usuario
- **Relax**: Sin restricciones, guarda solo historial por usuario

## Convenciones

- UI 100% en Jetpack Compose (sin XML layouts)
- Estado manejado con `remember` y `mutableStateOf`
- Efectos secundarios en `LaunchedEffect`
- Tema adaptable claro/oscuro automatico
- Navegacion mediante sealed classes con userId
- Persistencia con Room Database y Flow
- Operaciones de BD en coroutines
- Autenticacion local sin encriptacion (solo para desarrollo)

## Dependencias Clave (libs.versions.toml)

```toml
kotlin = "2.0.21"
ksp = "2.0.21-1.0.27"
room = "2.6.1"
coreKtx = "1.17.0"
lifecycleRuntimeKtx = "2.10.0"
activityCompose = "1.12.0"
composeBom = "2024.09.00"
```

## Notas para Desarrollo

- Codigo del juego en `MainActivity.kt`, datos en `data/`
- Los sonidos estan en `res/raw/`
- El tema soporta Material Dynamic Colors en Android 12+
- Base de datos SQLite en: `/data/data/com.example.juegomemoria/databases/juego_memoria_db`
- Version de BD: 2 (con migracion destructiva)
- Tests unitarios e instrumentados estan configurados pero vacios
- APK release se genera en `app/release/`
- La contrasena se almacena en texto plano (solo para uso educativo/local)
