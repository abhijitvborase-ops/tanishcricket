package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CricketsRepository(private val context: Context) {
    private val database = CricketsDatabase.getDatabase(context)
    private val dao = database.dao
    private val mutex = Mutex()

    // --- PLAYERS ---
    val allPlayers: Flow<List<Player>> = dao.getAllPlayers()

    fun searchPlayers(query: String): Flow<List<Player>> {
        return if (query.isBlank()) allPlayers else dao.searchPlayers(query)
    }

    suspend fun insertPlayer(player: Player): Long = dao.insertPlayer(player)
    suspend fun updatePlayer(player: Player) = dao.updatePlayer(player)
    suspend fun deletePlayerById(id: Long) = dao.deletePlayerById(id)
    suspend fun getPlayerById(id: Long): Player? = dao.getPlayerById(id)

    // --- MATCHES ---
    val allMatches: Flow<List<Match>> = dao.getAllMatches()

    fun getMatchByIdFlow(matchId: Long): Flow<Match?> = dao.getMatchByIdFlow(matchId)
    suspend fun getMatchById(matchId: Long): Match? = dao.getMatchById(matchId)

    suspend fun insertMatch(match: Match): Long = dao.insertMatch(match)
    suspend fun updateMatch(match: Match) = dao.updateMatch(match)
    suspend fun deleteMatchById(id: Long) {
        dao.deleteMatchById(id)
        dao.deleteInningsForMatch(id)
        dao.deleteBallsForMatch(id)
    }

    suspend fun deleteInningsForMatch(matchId: Long) = dao.deleteInningsForMatch(matchId)
    suspend fun deleteBallsForMatch(matchId: Long) = dao.deleteBallsForMatch(matchId)

    // --- INNINGS ---
    fun getInningsForMatchFlow(matchId: Long): Flow<List<Innings>> = dao.getInningsForMatchFlow(matchId)
    suspend fun getInningsForMatch(matchId: Long): List<Innings> = dao.getInningsForMatch(matchId)
    suspend fun insertInnings(innings: Innings): Long = dao.insertInnings(innings)
    suspend fun updateInnings(innings: Innings) = dao.updateInnings(innings)
    suspend fun deleteInningsByNumber(matchId: Long, inningsNum: Int) = dao.deleteInningsByNumber(matchId, inningsNum)

    // --- BALLS ---
    fun getBallsForMatchFlow(matchId: Long): Flow<List<Ball>> = dao.getBallsForMatchFlow(matchId)
    suspend fun getBallsForMatch(matchId: Long): List<Ball> = dao.getBallsForMatch(matchId)
    suspend fun insertBall(ball: Ball): Long = dao.insertBall(ball)
    suspend fun deleteBallById(id: Long) = dao.deleteBallById(id)
    suspend fun getLastBallOfInnings(matchId: Long, inningsNum: Int): Ball? = dao.getLastBallOfInnings(matchId, inningsNum)

    // --- STATS FLOWS ---
    fun getCareerStatsFlow(): Flow<List<PlayerStats>> {
        return kotlinx.coroutines.flow.flowOf(emptyList())
    }


}
