package com.example.data

import java.io.Serializable

data class PlayerStats(
    val playerId: Long,
    val playerName: String,
    val playerNickname: String = "",
    val profilePhotoUri: String = "",
    // Batting
    val battingMatches: Int = 0,
    val battingInnings: Int = 0,
    val battingRuns: Int = 0,
    val highestScore: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val ducks: Int = 0,
    val notOuts: Int = 0,
    val ballsFaced: Int = 0,
    // Bowling
    val bowlingMatches: Int = 0,
    val bowlingOversBowledInBalls: Int = 0, // total balls / 6
    val bowlingBallsBowled: Int = 0, // remainder balls
    val bowlingWickets: Int = 0,
    val runsConceded: Int = 0,
    val bestBowlingWickets: Int = 0,
    val bestBowlingRuns: Int = 0,
    // Fielding
    val catches: Int = 0,
    val runOuts: Int = 0,
    val stumpings: Int = 0
) : Serializable {

    val battingAverage: Double
        get() {
            val completedInnings = battingInnings - notOuts
            if (completedInnings <= 0) {
                return if (battingInnings > 0) battingRuns.toDouble() else 0.0
            }
            return battingRuns.toDouble() / completedInnings
        }

    val battingStrikeRate: Double
        get() {
            if (ballsFaced <= 0) return 0.0
            return (battingRuns.toDouble() / ballsFaced) * 100.0
        }

    val bowlingOversString: String
        get() {
            val overs = bowlingBallsBowled / 6
            val remainder = bowlingBallsBowled % 6
            return "$overs.$remainder"
        }

    val bowlingEconomy: Double
        get() {
            if (bowlingBallsBowled <= 0) return 0.0
            val overs = bowlingBallsBowled.toDouble() / 6.0
            return runsConceded.toDouble() / overs
        }

    val bestBowlingString: String
        get() = "$bestBowlingWickets/$bestBowlingRuns"

    // Calculation of MVP points for this player in individual match or career
    val mvpPoints: Double
        get() {
            var points = 0.0
            // Batting: 1 pt per run, 1 pt for four, 2 pt for six, -5 for duck
            points += battingRuns * 1.0
            points += fours * 1.5
            points += sixes * 2.5
            if (battingInnings > 0 && battingRuns == 0 && (battingInnings - notOuts) > 0) {
                points -= 5.0
            }
            if (battingRuns >= 50) points += 15.0
            if (battingRuns >= 30) points += 5.0

            // Bowling: 15 pt per wicket, -0.5 pt per run conceded, bonus for low economy (if bowled at least 1 over)
            points += bowlingWickets * 15.0
            points -= runsConceded * 0.5
            if (bowlingBallsBowled >= 6) {
                val econ = bowlingEconomy
                if (econ < 5.0) points += 15.0
                else if (econ < 7.0) points += 10.0
                else if (econ < 9.0) points += 5.0
            }

            // Fielding: 8 pt per catch/stumping, 10 pt per run out
            points += catches * 8.0
            points += stumpings * 8.0
            points += runOuts * 10.0

            return maxOf(0.0, points)
        }
}
