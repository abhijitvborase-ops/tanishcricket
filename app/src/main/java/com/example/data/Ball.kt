package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "balls")
data class Ball(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val inningsNumber: Int, // 1 or 2
    val overIndex: Int, // 0-based over number (e.g. 0, 1, 2)
    val ballIndexInOver: Int, // 1-based index (1 to 6) of valid balls in current over
    val batsmanId: Long,
    val bowlerId: Long,
    val runs: Int, // runs off the bat
    val extraRuns: Int = 0, // runs from extras
    val extraType: String? = null, // "WIDE", "NO_BALL", "BYE", "LEG_BYE"
    val isWicket: Boolean = false,
    val wicketType: String? = null, // "BOWLED", "CAUGHT", "LBW", "RUN_OUT", "STUMPED", "HIT_WICKET"
    val dismissedPlayerId: Long? = null,
    val fielderId: Long? = null, // fielder involved in run out, catch, stumped
    val timestamp: Long = System.currentTimeMillis()
) : Serializable {
    // Total runs added to score from this ball
    val totalRuns: Int
        get() = runs + extraRuns
}
