package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "innings")
data class Innings(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val inningsNumber: Int, // 1 or 2
    val battingTeam: String,
    val bowlingTeam: String,
    val totalRuns: Int = 0,
    val totalWickets: Int = 0,
    val totalBallsBowled: Int = 0,
    val isCompleted: Boolean = false,
    val target: Int = 0
) : Serializable
