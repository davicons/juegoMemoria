package com.example.juegomemoria.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad que representa un usuario registrado.
 * Almacena credenciales para autenticacion local.
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val username: String,              // Nombre de usuario unico
    val password: String,              // Contrasena en texto plano (local)
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Entidad que representa el mejor record por nivel para cada usuario.
 * Guarda el mejor tiempo y menor cantidad de movimientos para cada nivel.
 * Clave compuesta: userId + level
 */
@Entity(
    tableName = "game_records",
    primaryKeys = ["userId", "level"]
)
data class GameRecord(
    val userId: Int,                   // ID del usuario
    val level: Int,                    // Nivel (1-5)
    val bestTime: Int,                 // Mejor tiempo en segundos
    val bestMoves: Int,                // Menor cantidad de movimientos
    val timesCompleted: Int = 0,       // Veces que se completo el nivel
    val lastPlayedDate: Long = System.currentTimeMillis()
)

/**
 * Entidad que representa el historial de cada partida jugada.
 * Guarda informacion detallada de cada partida por usuario.
 */
@Entity(tableName = "game_history")
data class GameHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int,                   // ID del usuario
    val level: Int,                    // Nivel jugado
    val moves: Int,                    // Movimientos realizados
    val timeSpent: Int,                // Tiempo en segundos
    val completed: Boolean,            // Si se completo el nivel
    val relaxMode: Boolean,            // Si fue en modo relax
    val playedAt: Long = System.currentTimeMillis()
)

/**
 * Entidad que representa las estadisticas globales de un usuario.
 * Cada usuario tiene su propio registro de estadisticas.
 */
@Entity(tableName = "player_stats")
data class PlayerStats(
    @PrimaryKey
    val userId: Int,                   // ID del usuario (antes era id = 1 singleton)
    val totalGamesPlayed: Int = 0,     // Total de partidas jugadas
    val totalGamesWon: Int = 0,        // Total de partidas ganadas
    val totalTimePlayed: Int = 0,      // Tiempo total jugado en segundos
    val totalMoves: Int = 0,           // Total de movimientos realizados
    val currentStreak: Int = 0,        // Racha actual de victorias
    val bestStreak: Int = 0,           // Mejor racha de victorias
    val lastPlayedDate: Long = System.currentTimeMillis()
)
