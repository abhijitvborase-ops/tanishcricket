package com.example.data

object StatsCalculator {

    fun calculateCareerStats(
        players: List<Player>,
        matches: List<Match>,
        balls: List<Ball>
    ): List<PlayerStats> {
        val completedMatches = matches.filter { it.isCompleted }
        val completedMatchIds = completedMatches.map { it.id }.toSet()
        val ballsInCompletedMatches = balls.filter { it.matchId in completedMatchIds }

        return players.map { player ->
            val pid = player.id

            // Filter balls for this player
            val playerBattingBalls = ballsInCompletedMatches.filter { it.batsmanId == pid }
            val playerBowlingBalls = ballsInCompletedMatches.filter { it.bowlerId == pid }

            // Batting Match count (included in any team or played)
            val battingMatchesCount = completedMatches.count { match ->
                pid in match.teamAPlayerIds || pid in match.teamBPlayerIds || pid in match.commonPlayerIds
            }

            // Batting Innings runs by Match and Innings
            // Key: matchId_inningsNumber, Value: runs
            val battingRunsByInnings = mutableMapOf<String, Int>()
            val ballsFacedByInnings = mutableMapOf<String, Int>()
            val wasDismissedByInnings = mutableSetOf<String>()

            for (ball in playerBattingBalls) {
                val key = "${ball.matchId}_${ball.inningsNumber}"
                battingRunsByInnings[key] = (battingRunsByInnings[key] ?: 0) + ball.runs
                if (ball.extraType != "WIDE") {
                    ballsFacedByInnings[key] = (ballsFacedByInnings[key] ?: 0) + 1
                }
            }

            // Check if player was ever dismissed in any innings
            val dismissals = ballsInCompletedMatches.filter { it.dismissedPlayerId == pid }
            for (d in dismissals) {
                val key = "${d.matchId}_${d.inningsNumber}"
                wasDismissedByInnings.add(key)
            }

            // Also check if they faced balls or were dismissed to determine if they actually batted
            val allBattingInningsKeys = battingRunsByInnings.keys + wasDismissedByInnings
            val battingInningsCount = allBattingInningsKeys.size

            val battingRunsTotal = battingRunsByInnings.values.sum()
            val highestInningsScore = if (battingRunsByInnings.isEmpty()) 0 else battingRunsByInnings.values.maxOrNull() ?: 0
            val totalBallsFaced = ballsFacedByInnings.values.sum()

            val foursCount = playerBattingBalls.count { it.runs == 4 && it.extraType == null }
            val sixesCount = playerBattingBalls.count { it.runs == 6 && it.extraType == null }

            var ducksCount = 0
            var notOutsCount = 0

            for (key in allBattingInningsKeys) {
                val runs = battingRunsByInnings[key] ?: 0
                val gotOut = wasDismissedByInnings.contains(key)
                if (gotOut) {
                    if (runs == 0) {
                        ducksCount++
                    }
                } else {
                    notOutsCount++
                }
            }

            // Bowling Stats
            val bowlingMatchesCount = playerBowlingBalls.map { it.matchId }.distinct().size
            // Valid balls are those that are not Wides or No Balls
            val validBallsBowledList = playerBowlingBalls.filter { it.extraType != "WIDE" && it.extraType != "NO_BALL" }
            val bowlingBallsBowledTotal = validBallsBowledList.size

            // Runs Conceded (Only count bat runs + Wides + No Balls, exclude Byes and Leg Byes)
            val runsConcededTotal = playerBowlingBalls.sumOf { ball ->
                if (ball.extraType == "BYE" || ball.extraType == "LEG_BYE") {
                    0
                } else {
                    ball.runs + ball.extraRuns
                }
            }

            // Wickets (Bowler wickets: BOWLED, CAUGHT, LBW, STUMPED, HIT_WICKET)
            val bowlerWicketTypes = setOf("BOWLED", "CAUGHT", "LBW", "STUMPED", "HIT_WICKET")
            val bowlingWicketsTotal = playerBowlingBalls.count { ball ->
                ball.isWicket && ball.wicketType in bowlerWicketTypes
            }

            // Best Bowling
            // Group bowling balls by Match ID
            val bowlingByMatch = playerBowlingBalls.groupBy { it.matchId }
            var bestWickets = 0
            var bestRuns = 0
            var hasBowled = false

            for ((_, matchBalls) in bowlingByMatch) {
                hasBowled = true
                val matchWickets = matchBalls.count { it.isWicket && it.wicketType in bowlerWicketTypes }
                val matchRuns = matchBalls.sumOf { ball ->
                    if (ball.extraType == "BYE" || ball.extraType == "LEG_BYE") 0 else ball.runs + ball.extraRuns
                }
                if (matchWickets > bestWickets) {
                    bestWickets = matchWickets
                    bestRuns = matchRuns
                } else if (matchWickets == bestWickets) {
                    if (bestWickets == 0 || matchRuns < bestRuns) {
                        bestRuns = matchRuns
                    }
                }
            }

            // Fielding
            val catchesCount = ballsInCompletedMatches.count { it.isWicket && it.wicketType == "CAUGHT" && it.fielderId == pid }
            val runOutsCount = ballsInCompletedMatches.count { it.isWicket && it.wicketType == "RUN_OUT" && it.fielderId == pid }
            val stumpingsCount = ballsInCompletedMatches.count { it.isWicket && it.wicketType == "STUMPED" && it.fielderId == pid }

            PlayerStats(
                playerId = pid,
                playerName = player.name,
                playerNickname = player.nickname,
                profilePhotoUri = player.profilePhotoUri,
                battingMatches = battingMatchesCount,
                battingInnings = battingInningsCount,
                battingRuns = battingRunsTotal,
                highestScore = highestInningsScore,
                fours = foursCount,
                sixes = sixesCount,
                ducks = ducksCount,
                notOuts = notOutsCount,
                ballsFaced = totalBallsFaced,
                bowlingMatches = bowlingMatchesCount,
                bowlingBallsBowled = bowlingBallsBowledTotal,
                bowlingWickets = bowlingWicketsTotal,
                runsConceded = runsConcededTotal,
                bestBowlingWickets = if (hasBowled) bestWickets else 0,
                bestBowlingRuns = if (hasBowled) bestRuns else 0,
                catches = catchesCount,
                runOuts = runOutsCount,
                stumpings = stumpingsCount
            )
        }
    }

    // Process MVP points for a specific match and select the MVP player
    fun calculateMatchMVP(
        players: List<Player>,
        match: Match,
        matchBalls: List<Ball>
    ): PlayerStats? {
        val matchPlayerIds = (match.teamAPlayerIds + match.teamBPlayerIds + match.commonPlayerIds).toSet()
        if (matchPlayerIds.isEmpty()) return null

        val filteredPlayers = players.filter { it.id in matchPlayerIds }
        val playerStatsForMatch = calculateCareerStats(
            players = filteredPlayers,
            matches = listOf(match.copy(isCompleted = true)), // Force completed to process
            balls = matchBalls
        )

        return playerStatsForMatch.maxByOrNull { it.mvpPoints }
    }
}
