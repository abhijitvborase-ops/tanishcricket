package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "matches")
data class Match(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: Long = System.currentTimeMillis(),
    val teamAName: String,
    val teamBName: String,
    val numberOfOvers: Int,
    val tossWinnerName: String, // "Team A Name" or "Team B Name"
    val tossDecision: String, // "BAT" or "BOWL"
    val isCompleted: Boolean = false,
    val resultText: String = "",
    val mvpPlayerId: Long? = null,
    // Comma-separated player ID lists
    val teamAPlayerIdsString: String = "",
    val teamBPlayerIdsString: String = "",
    val commonPlayerIdsString: String = ""
) : Serializable {
    val teamAPlayerIds: List<Long>
        get() = if (teamAPlayerIdsString.isBlank()) emptyList() else teamAPlayerIdsString.split(",").mapNotNull { it.toLongOrNull() }

    val teamBPlayerIds: List<Long>
        get() = if (teamBPlayerIdsString.isBlank()) emptyList() else teamBPlayerIdsString.split(",").mapNotNull { it.toLongOrNull() }

    val commonPlayerIds: List<Long>
        get() = if (commonPlayerIdsString.isBlank()) emptyList() else commonPlayerIdsString.split(",").mapNotNull { it.toLongOrNull() }
}
