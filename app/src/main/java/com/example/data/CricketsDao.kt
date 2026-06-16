package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CricketsDao {

    // --- PLAYERS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: Player): Long

    @Update
    suspend fun updatePlayer(player: Player)

    @Query("DELETE FROM players WHERE id = :id")
    suspend fun deletePlayerById(id: Long)

    @Query("SELECT * FROM players ORDER BY name ASC")
    fun getAllPlayers(): Flow<List<Player>>

    @Query("SELECT * FROM players ORDER BY name ASC")
    suspend fun getAllPlayersList(): List<Player>

    @Query("SELECT COUNT(*) FROM players")
    suspend fun getPlayersCount(): Int

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun getPlayerById(id: Long): Player?

    @Query("SELECT * FROM players WHERE name LIKE '%' || :query || '%' OR nickname LIKE '%' || :query || '%'")
    fun searchPlayers(query: String): Flow<List<Player>>


    // --- MATCHES ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: Match): Long

    @Update
    suspend fun updateMatch(match: Match)

    @Query("DELETE FROM matches WHERE id = :id")
    suspend fun deleteMatchById(id: Long)

    @Query("DELETE FROM innings WHERE matchId = :matchId")
    suspend fun deleteInningsForMatch(matchId: Long)

    @Query("DELETE FROM balls WHERE matchId = :matchId")
    suspend fun deleteBallsForMatch(matchId: Long)

    @Query("SELECT * FROM matches ORDER BY date DESC")
    fun getAllMatches(): Flow<List<Match>>

    @Query("SELECT * FROM matches WHERE id = :id")
    fun getMatchByIdFlow(id: Long): Flow<Match?>

    @Query("SELECT * FROM matches WHERE id = :id")
    suspend fun getMatchById(id: Long): Match?


    // --- INNINGS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInnings(innings: Innings): Long

    @Update
    suspend fun updateInnings(innings: Innings)

    @Query("DELETE FROM innings WHERE matchId = :matchId AND inningsNumber = :inningsNum")
    suspend fun deleteInningsByNumber(matchId: Long, inningsNum: Int)

    @Query("SELECT * FROM innings WHERE matchId = :matchId ORDER BY inningsNumber ASC")
    fun getInningsForMatchFlow(matchId: Long): Flow<List<Innings>>

    @Query("SELECT * FROM innings WHERE matchId = :matchId ORDER BY inningsNumber ASC")
    suspend fun getInningsForMatch(matchId: Long): List<Innings>


    // --- BALLS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBall(ball: Ball): Long

    @Query("DELETE FROM balls WHERE id = :id")
    suspend fun deleteBallById(id: Long)

    @Query("SELECT * FROM balls WHERE matchId = :matchId ORDER BY id ASC")
    fun getBallsForMatchFlow(matchId: Long): Flow<List<Ball>>

    @Query("SELECT * FROM balls WHERE matchId = :matchId ORDER BY id ASC")
    suspend fun getBallsForMatch(matchId: Long): List<Ball>

    @Query("SELECT * FROM balls WHERE matchId = :matchId AND inningsNumber = :inningsNum ORDER BY id DESC LIMIT 1")
    suspend fun getLastBallOfInnings(matchId: Long, inningsNum: Int): Ball?
}
