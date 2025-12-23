package com.example.juegomemoria.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    // ========================================================================
    // USUARIOS (Autenticacion local)
    // ========================================================================

    @Insert
    suspend fun insertUser(user: User): Long

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE username = :username AND password = :password LIMIT 1")
    suspend fun validateUser(username: String, password: String): User?

    @Query("SELECT COUNT(*) FROM users WHERE username = :username")
    suspend fun usernameExists(username: String): Int

    // ========================================================================
    // GAME RECORDS (Mejores puntuaciones por nivel y usuario)
    // ========================================================================

    @Query("SELECT * FROM game_records WHERE userId = :userId AND level = :level")
    suspend fun getRecordByLevel(userId: Int, level: Int): GameRecord?

    @Query("SELECT * FROM game_records WHERE userId = :userId ORDER BY level ASC")
    fun getAllRecords(userId: Int): Flow<List<GameRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: GameRecord)

    @Update
    suspend fun updateRecord(record: GameRecord)

    /**
     * Actualiza el record si el nuevo tiempo/movimientos es mejor.
     * Retorna true si se actualizo el record.
     */
    suspend fun updateRecordIfBetter(userId: Int, level: Int, time: Int, moves: Int): Boolean {
        val existing = getRecordByLevel(userId, level)
        if (existing == null) {
            insertRecord(GameRecord(
                userId = userId,
                level = level,
                bestTime = time,
                bestMoves = moves,
                timesCompleted = 1
            ))
            return true
        }

        val isBetterTime = time < existing.bestTime
        val isBetterMoves = moves < existing.bestMoves

        if (isBetterTime || isBetterMoves) {
            updateRecord(existing.copy(
                bestTime = if (isBetterTime) time else existing.bestTime,
                bestMoves = if (isBetterMoves) moves else existing.bestMoves,
                timesCompleted = existing.timesCompleted + 1,
                lastPlayedDate = System.currentTimeMillis()
            ))
            return true
        } else {
            updateRecord(existing.copy(
                timesCompleted = existing.timesCompleted + 1,
                lastPlayedDate = System.currentTimeMillis()
            ))
            return false
        }
    }

    // ========================================================================
    // GAME HISTORY (Historial de partidas por usuario)
    // ========================================================================

    @Query("SELECT * FROM game_history WHERE userId = :userId ORDER BY playedAt DESC")
    fun getAllHistory(userId: Int): Flow<List<GameHistory>>

    @Query("SELECT * FROM game_history WHERE userId = :userId ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentHistory(userId: Int, limit: Int): Flow<List<GameHistory>>

    @Query("SELECT * FROM game_history WHERE userId = :userId AND level = :level ORDER BY playedAt DESC")
    fun getHistoryByLevel(userId: Int, level: Int): Flow<List<GameHistory>>

    @Insert
    suspend fun insertHistory(history: GameHistory)

    @Query("DELETE FROM game_history WHERE userId = :userId")
    suspend fun clearHistory(userId: Int)

    @Query("SELECT COUNT(*) FROM game_history WHERE userId = :userId")
    suspend fun getHistoryCount(userId: Int): Int

    // ========================================================================
    // PLAYER STATS (Estadisticas por usuario)
    // ========================================================================

    @Query("SELECT * FROM player_stats WHERE userId = :userId")
    suspend fun getPlayerStats(userId: Int): PlayerStats?

    @Query("SELECT * FROM player_stats WHERE userId = :userId")
    fun getPlayerStatsFlow(userId: Int): Flow<PlayerStats?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayerStats(stats: PlayerStats)

    @Update
    suspend fun updatePlayerStats(stats: PlayerStats)

    /**
     * Actualiza las estadisticas despues de una partida.
     */
    suspend fun updateStatsAfterGame(userId: Int, timeSpent: Int, moves: Int, won: Boolean) {
        val existing = getPlayerStats(userId)
        if (existing == null) {
            insertPlayerStats(PlayerStats(
                userId = userId,
                totalGamesPlayed = 1,
                totalGamesWon = if (won) 1 else 0,
                totalTimePlayed = timeSpent,
                totalMoves = moves,
                currentStreak = if (won) 1 else 0,
                bestStreak = if (won) 1 else 0
            ))
        } else {
            val newStreak = if (won) existing.currentStreak + 1 else 0
            updatePlayerStats(existing.copy(
                totalGamesPlayed = existing.totalGamesPlayed + 1,
                totalGamesWon = existing.totalGamesWon + (if (won) 1 else 0),
                totalTimePlayed = existing.totalTimePlayed + timeSpent,
                totalMoves = existing.totalMoves + moves,
                currentStreak = newStreak,
                bestStreak = maxOf(existing.bestStreak, newStreak),
                lastPlayedDate = System.currentTimeMillis()
            ))
        }
    }

    // ========================================================================
    // ESTADISTICAS CALCULADAS (por usuario)
    // ========================================================================

    @Query("SELECT COUNT(*) FROM game_history WHERE userId = :userId AND completed = 1")
    suspend fun getTotalWins(userId: Int): Int

    @Query("SELECT COUNT(*) FROM game_history WHERE userId = :userId AND completed = 0")
    suspend fun getTotalLosses(userId: Int): Int

    @Query("SELECT AVG(moves) FROM game_history WHERE userId = :userId AND completed = 1 AND level = :level")
    suspend fun getAverageMovesForLevel(userId: Int, level: Int): Float?

    @Query("SELECT AVG(timeSpent) FROM game_history WHERE userId = :userId AND completed = 1 AND level = :level")
    suspend fun getAverageTimeForLevel(userId: Int, level: Int): Float?

    @Query("SELECT COUNT(*) FROM game_history WHERE userId = :userId AND level = :level AND completed = 1")
    suspend fun getWinsForLevel(userId: Int, level: Int): Int
}
