package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ScorerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CricketsRepository(application)

    // Role state: ADMIN vs VIEWERS
    private val _userRole = MutableStateFlow("ADMIN")
    val userRole: StateFlow<String> = _userRole.asStateFlow()

    fun setUserRole(role: String) {
        _userRole.value = role
    }

    // Firebase Authentication State
    private val firebaseAuth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        android.util.Log.e("ScorerViewModel", "Firebase Auth initialization failed: ${e.message}")
        null
    }
    private val _currentUser = MutableStateFlow<FirebaseUser?>(firebaseAuth?.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    fun signUp(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth == null) {
            onResult(false, "Firebase is not initialized. Running in local-only offline mode.")
            return
        }
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _currentUser.value = auth.currentUser
                    viewModelScope.launch {
                        try {
                            syncManager.saveUserProfile()
                        } catch (e: Exception) {
                            android.util.Log.e("ScorerViewModel", "Failed to save user profile: ${e.message}")
                        }
                        // Auto sync on signup success
                        triggerFirebaseSync()
                    }
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.message ?: "Sign up failed")
                }
            }
    }

    fun signIn(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth == null) {
            if (email.isNotBlank()) {
                // Allow bypass for local testing when Firebase is missing/uninitialized
                onResult(true, null)
            } else {
                onResult(false, "Firebase is not initialized. Please enter any email to start offline-first mode.")
            }
            return
        }
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _currentUser.value = auth.currentUser
                    viewModelScope.launch {
                        try {
                            syncManager.saveUserProfile()
                        } catch (e: Exception) {
                            android.util.Log.e("ScorerViewModel", "Failed to save user profile: ${e.message}")
                        }
                        // Auto sync on login success
                        triggerFirebaseSync()
                    }
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.message ?: "Sign in failed")
                }
            }
    }

    fun signInWithGoogle(idToken: String, onResult: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth == null) {
            onResult(false, "Firebase is not initialized.")
            return
        }
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _currentUser.value = auth.currentUser
                    viewModelScope.launch {
                        try {
                            syncManager.saveUserProfile()
                        } catch (e: Exception) {
                            android.util.Log.e("ScorerViewModel", "Failed to save user profile: ${e.message}")
                        }
                        // Auto sync on Google signin success
                        triggerFirebaseSync()
                    }
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.message ?: "Google Sign-In failed")
                }
            }
    }

    fun signOut() {
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            android.util.Log.e("ScorerViewModel", "Sign out failed: ${e.message}")
        }
        _currentUser.value = null
    }

    init {
        try {
            firebaseAuth?.addAuthStateListener { auth ->
                _currentUser.value = auth.currentUser
            }
        } catch (e: Exception) {
            android.util.Log.e("ScorerViewModel", "addAuthStateListener failed: ${e.message}")
        }
    }

    private val syncManager = FirebaseSyncManager(application)

    // Mock Firebase Sync state
    private val _syncState = MutableStateFlow("Synced") // "Synced", "Syncing", "Offline"
    val syncState: StateFlow<String> = _syncState.asStateFlow()

    private val _syncMessage = MutableStateFlow("Local database synchronized with Firebase Cloud.")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow<Long>(System.currentTimeMillis())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    fun autoSyncToFirebase() {
        viewModelScope.launch {
            try {
                syncManager.pushLocalToCloud()
                _lastSyncTimestamp.value = System.currentTimeMillis()
            } catch (e: Exception) {
                android.util.Log.e("ScorerViewModel", "Auto-sync push failed: ${e.message}")
            }
        }
    }

    fun triggerFirebaseSync() {
        viewModelScope.launch {
            _syncState.value = "Syncing"
            _syncMessage.value = "Connecting to Firebase Cloud..."
            try {
                _syncMessage.value = "Uploading players, matches, innings, and deliveries to Firestore..."
                syncManager.pushLocalToCloud()

                _syncMessage.value = "Pulling newest scorecards from Firebase Firestore..."
                syncManager.pullCloudToLocal()

                _syncState.value = "Synced"
                _syncMessage.value = "Successfully synchronized all match records and statistics with Firebase cloud."
                _lastSyncTimestamp.value = System.currentTimeMillis()
                reloadActiveMatchDetails()
            } catch (e: Exception) {
                android.util.Log.e("ScorerViewModel", "Sync error: ${e.message}", e)
                _syncState.value = "Offline"
                _syncMessage.value = "Firebase Sync failed: ${e.message}. Running in offline-first mode."
            }
        }
    }

    // --- RECTIVE INGEST DATA STREAMS ---
    val allPlayers: StateFlow<List<Player>> = repository.allPlayers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMatches: StateFlow<List<Match>> = repository.allMatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All balls in the database, used for career stats calculation
    private val _allBalls = MutableStateFlow<List<Ball>>(emptyList())
    val allBalls: StateFlow<List<Ball>> = _allBalls.asStateFlow()

    // Career Stats combining players, matches and balls dynamically
    val careerStats: StateFlow<List<PlayerStats>> = combine(allPlayers, allMatches, allBalls) { players, matches, balls ->
        StatsCalculator.calculateCareerStats(players, matches, balls)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- CURRENT MATCH SCORING STATE ---
    private val _activeMatchId = MutableStateFlow<Long?>(null)
    val activeMatchId: StateFlow<Long?> = _activeMatchId.asStateFlow()

    private val _activeMatch = MutableStateFlow<Match?>(null)
    val activeMatch: StateFlow<Match?> = _activeMatch.asStateFlow()

    private val _activeInnings = MutableStateFlow<List<Innings>>(emptyList())
    val activeInnings: StateFlow<List<Innings>> = _activeInnings.asStateFlow()

    private val _activeMatchBalls = MutableStateFlow<List<Ball>>(emptyList())
    val activeMatchBalls: StateFlow<List<Ball>> = _activeMatchBalls.asStateFlow()

    // Scoring helpers for UI
    private val _batsmanOnStrikeId = MutableStateFlow<Long?>(null)
    val batsmanOnStrikeId: StateFlow<Long?> = _batsmanOnStrikeId.asStateFlow()

    private val _batsmanNonStrikeId = MutableStateFlow<Long?>(null)
    val batsmanNonStrikeId: StateFlow<Long?> = _batsmanNonStrikeId.asStateFlow()

    private val _activeBowlerId = MutableStateFlow<Long?>(null)
    val activeBowlerId: StateFlow<Long?> = _activeBowlerId.asStateFlow()

    init {
        // Load all balls in database for overall statistics
        loadAllBalls()

        // Start real-time Firestore database synchronization listener
        try {
            syncManager.startRealtimeListener {
                viewModelScope.launch {
                    reloadActiveMatchDetails()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ScorerViewModel", "Failed starting Firestore snapshot listener: ${e.message}")
        }

        // Sync local data to fetch details if selection changes
        viewModelScope.launch {
            _activeMatchId.collect { id ->
                if (id != null) {
                    // Update active match details
                    val match = repository.getMatchById(id)
                    _activeMatch.value = match
                    if (match != null) {
                        val inningsList = repository.getInningsForMatch(id)
                        _activeInnings.value = inningsList
                        val ballsList = repository.getBallsForMatch(id)
                        _activeMatchBalls.value = ballsList
                        
                        // Infer striker / non-striker / bowler if they're not set yet
                        inferScoringLineup(match, inningsList, ballsList)
                    }
                } else {
                    _activeMatch.value = null
                    _activeInnings.value = emptyList()
                    _activeMatchBalls.value = emptyList()
                    _batsmanOnStrikeId.value = null
                    _batsmanNonStrikeId.value = null
                    _activeBowlerId.value = null
                }
            }
        }
    }

    private fun loadAllBalls() {
        viewModelScope.launch {
            // Collect overall balls across matches by fetching from matches list and queries
            // Since we listening to finished matches, let's pull balls for finished matches or all balls
            allMatches.collect { matches ->
                val list = mutableListOf<Ball>()
                for (match in matches) {
                    list.addAll(repository.getBallsForMatch(match.id))
                }
                _allBalls.value = list
            }
        }
    }

    fun selectActiveMatch(matchId: Long?) {
        _activeMatchId.value = matchId
    }



    // Set line up manually if required
    fun setScoringLineup(strikerId: Long?, nonStrikerId: Long?, bowlerId: Long?) {
        _batsmanOnStrikeId.value = strikerId
        _batsmanNonStrikeId.value = nonStrikerId
        _activeBowlerId.value = bowlerId
    }

    fun swapStrike() {
        val temp = _batsmanOnStrikeId.value
        _batsmanOnStrikeId.value = _batsmanNonStrikeId.value
        _batsmanNonStrikeId.value = temp
    }

    // Logic to run when a match is opened: determine who is batting, bowling, etc.
    private fun inferScoringLineup(match: Match, inningsList: List<Innings>, ballsList: List<Ball>) {
        val currentInn = inningsList.find { !it.isCompleted } ?: inningsList.lastOrNull() ?: return
        val currentInnBalls = ballsList.filter { it.inningsNumber == currentInn.inningsNumber }

        val activePlayerIds = (match.teamAPlayerIds + match.teamBPlayerIds + match.commonPlayerIds).toSet()
        if (activePlayerIds.isEmpty()) return

        // Batting team player pool
        val isTeamABatting = currentInn.battingTeam == match.teamAName
        val battingPool = (if (isTeamABatting) match.teamAPlayerIds else match.teamBPlayerIds) + match.commonPlayerIds

        val bowlingPool = (if (isTeamABatting) match.teamBPlayerIds else match.teamAPlayerIds) + match.commonPlayerIds

        if (currentInnBalls.isEmpty()) {
            // Fresh Innings, do not auto-select! (Issue 2 requirement: explicit opening selection)
            _batsmanOnStrikeId.value = null
            _batsmanNonStrikeId.value = null
            _activeBowlerId.value = null
        } else {
            // Chronologically simulate strike rotation and wickets ball-by-ball
            var openerStriker: Long? = null
            var openerNonStriker: Long? = null

            val firstBall = currentInnBalls.firstOrNull()
            if (firstBall != null) {
                openerStriker = firstBall.batsmanId
                // Find first batsman other than openerStriker faced before any wicket
                for (b in currentInnBalls) {
                    if (b.isWicket) break
                    if (b.batsmanId != openerStriker) {
                        openerNonStriker = b.batsmanId
                        break
                    }
                }
            }

            // Fallback to pool if we couldn't find openerNonStriker
            if (openerStriker == null) openerStriker = battingPool.getOrNull(0)
            if (openerNonStriker == null) {
                openerNonStriker = battingPool.filter { it != openerStriker }.getOrNull(0)
            }

            var curStriker: Long? = openerStriker
            var curNonStriker: Long? = openerNonStriker
            
            val outPlayerIds = mutableSetOf<Long>()

            for (ball in currentInnBalls) {
                // Keep track of any wicket
                if (ball.isWicket && ball.dismissedPlayerId != null) {
                    outPlayerIds.add(ball.dismissedPlayerId)
                }

                // Resolve facing batsman and strike status
                if (ball.batsmanId == curNonStriker) {
                    val temp = curStriker
                    curStriker = curNonStriker
                    curNonStriker = temp
                } else if (ball.batsmanId != curStriker) {
                    if (curStriker == null) {
                        curStriker = ball.batsmanId
                    } else if (curNonStriker == null) {
                        curNonStriker = ball.batsmanId
                    } else {
                        // Manual replacement on strike
                        curStriker = ball.batsmanId
                    }
                }

                // Remove dismissed players from active batting
                if (ball.isWicket && ball.dismissedPlayerId != null) {
                    if (ball.dismissedPlayerId == curStriker) curStriker = null
                    if (ball.dismissedPlayerId == curNonStriker) curNonStriker = null
                }

                // Calculate strike rotation for this ball
                val isValidDelivery = ball.extraType != "WIDE" && ball.extraType != "NO_BALL"
                
                var rotateStrike = false
                val runsOffBat = if (ball.extraType == null || ball.extraType == "NO_BALL") ball.runs else 0
                val runsOffExtras = if (ball.extraType == "BYE" || ball.extraType == "LEG_BYE") ball.extraRuns else 0
                val totalStrikeRotRuns = runsOffBat + runsOffExtras

                if (totalStrikeRotRuns % 2 != 0) {
                    rotateStrike = true
                }

                // Over completion toggles strike rotation (bowled from opposite end next over)
                if (isValidDelivery && ball.ballIndexInOver == 6) {
                    rotateStrike = !rotateStrike
                }

                if (rotateStrike && curStriker != null && curNonStriker != null) {
                    val temp = curStriker
                    curStriker = curNonStriker
                    curNonStriker = temp
                }
            }

            _batsmanOnStrikeId.value = curStriker
            _batsmanNonStrikeId.value = curNonStriker

            // Determine active bowler
            val lastBall = currentInnBalls.last()
            val lastBallOverCompleted = (lastBall.extraType != "WIDE" && lastBall.extraType != "NO_BALL") && lastBall.ballIndexInOver == 6
            if (lastBallOverCompleted) {
                _activeBowlerId.value = null // Over completed; prompt bowler selector
            } else {
                _activeBowlerId.value = lastBall.bowlerId
            }
        }
    }

    // --- SCORING ACTION ENGINE ---
    fun bowlBall(
        runs: Int,
        extraType: String?, // "WIDE", "NO_BALL", "BYE", "LEG_BYE"
        isWicket: Boolean,
        wicketType: String?, // "BOWLED", "CAUGHT", "LBW", "RUN_OUT", "STUMPED", "HIT_WICKET"
        dismissedPlayerId: Long?,
        fielderId: Long?
    ) {
        val match = _activeMatch.value ?: return
        val inningsList = _activeInnings.value
        val currentInnings = inningsList.find { !it.isCompleted } ?: return
        val striker = _batsmanOnStrikeId.value ?: return
        val bowler = _activeBowlerId.value ?: return

        viewModelScope.launch {
            // 1. Calculate Over indexing and ball index inside current over
            val ballsOfInnings = _activeMatchBalls.value.filter { it.inningsNumber == currentInnings.inningsNumber }
            val completedOvers = currentInnings.totalBallsBowled / 6
            val currentOverIndex = completedOvers
            val validBallsInOver = currentInnings.totalBallsBowled % 6

            // Is this ball a valid delivery? (Wides and No Balls are invalid balls in cricket, bowler must bowl again)
            val isValidDelivery = extraType != "WIDE" && extraType != "NO_BALL"
            val newBallIndexInOver = if (isValidDelivery) validBallsInOver + 1 else validBallsInOver

            // Calculate runs and extra runs
            var actualRuns = runs
            var actualExtraRuns = 0

            when (extraType) {
                "WIDE" -> {
                    actualExtraRuns = runs + 1 // 1 run for Wide + any runs run by batting team
                    actualRuns = 0 // batsman gets no runs for Wides
                }
                "NO_BALL" -> {
                    actualExtraRuns = 1
                    // Batsman gets off-bat runs, but extra adds 1 No-Ball run
                }
                "BYE", "LEG_BYE" -> {
                    actualExtraRuns = runs
                    actualRuns = 0
                }
            }

            // Create Ball Entity
            val newBall = Ball(
                matchId = match.id,
                inningsNumber = currentInnings.inningsNumber,
                overIndex = currentOverIndex,
                ballIndexInOver = newBallIndexInOver,
                batsmanId = striker,
                bowlerId = bowler,
                runs = actualRuns,
                extraRuns = actualExtraRuns,
                extraType = extraType,
                isWicket = isWicket,
                wicketType = wicketType,
                dismissedPlayerId = dismissedPlayerId,
                fielderId = fielderId
            )

            // Save Ball to Room
            repository.insertBall(newBall)

            // Update Innings Runs, Wickets, Balls Bowled
            val updatedInningsRuns = currentInnings.totalRuns + actualRuns + actualExtraRuns
            val updatedInningsWickets = currentInnings.totalWickets + if (isWicket) 1 else 0
            val updatedInningsBalls = currentInnings.totalBallsBowled + if (isValidDelivery) 1 else 0

            // Auto Strike Rotation logic:
            // Rotate on odd runs from the bat (1, 3, 5) or odd byes/leg-byes
            var rotateStrike = false
            val rotRuns = if (extraType == null || extraType == "NO_BALL") actualRuns else if (extraType == "BYE" || extraType == "LEG_BYE") runs else 0
            if (rotRuns % 2 != 0) {
                rotateStrike = true
            }

            // Check for over completion (6 valid deliveries bowled)
            var overCompleted = false
            if (isValidDelivery && newBallIndexInOver == 6) {
                overCompleted = true
                // At the end of an over, standard cricket rotates strike automatically (from the other end of the pitch)
                rotateStrike = !rotateStrike
            }

            // Check if Innings has ended
            // Criteria:
            // 1. All out (wickets == total available batsmen - 1. E.g. for team size 11, wickets == 10)
            // Since team size is flexible, let's check total team size dynamically
            val battingTeamSize = if (currentInnings.battingTeam == match.teamAName) match.teamAPlayerIds.size else match.teamBPlayerIds.size
            val isAllOut = updatedInningsWickets >= battingTeamSize - 1 && battingTeamSize > 1

            // 2. Overs completed
            val maxOvers = match.numberOfOvers
            val isOversCompleted = updatedInningsBalls >= maxOvers * 6

            // 3. Second innings chasings met (Target met!)
            var isTargetMet = false
            if (currentInnings.inningsNumber == 2) {
                val targetRuns = currentInnings.target
                if (updatedInningsRuns >= targetRuns) {
                    isTargetMet = true
                }
            }

            val inningsEnded = isAllOut || isOversCompleted || isTargetMet

            val updatedInnings = currentInnings.copy(
                totalRuns = updatedInningsRuns,
                totalWickets = updatedInningsWickets,
                totalBallsBowled = updatedInningsBalls,
                isCompleted = inningsEnded
            )

            repository.updateInnings(updatedInnings)

            // Perform strike rotation in state if flagged
            if (rotateStrike && !isWicket) { // If wicket, batsman is out and new batsman comes, so handled after chooser
                val temp = _batsmanOnStrikeId.value
                _batsmanOnStrikeId.value = _batsmanNonStrikeId.value
                _batsmanNonStrikeId.value = temp
            }

            // Reload Active State
            reloadActiveMatchDetails()

            // Handle Innings transition or Match completion
            if (inningsEnded) {
                _batsmanOnStrikeId.value = null
                _batsmanNonStrikeId.value = null
                _activeBowlerId.value = null
            } else {
                if (isWicket && dismissedPlayerId == striker) {
                    // Striker got out - need to prompt for new batsman in UI
                    _batsmanOnStrikeId.value = null
                } else if (isWicket && dismissedPlayerId == _batsmanNonStrikeId.value) {
                    // Non-striker got out - need new non-striker in UI
                    _batsmanNonStrikeId.value = null
                }

                if (overCompleted) {
                    // Over completed, reset current bowler so UI prompts for next bowler!
                    _activeBowlerId.value = null
                }
            }
            autoSyncToFirebase()
        }
    }

    private suspend fun completeMatch(match: Match, secondInningsRuns: Int, secondInningsWickets: Int) {
        val innList = repository.getInningsForMatch(match.id)
        val inn1 = innList.find { it.inningsNumber == 1 } ?: return
        val inn2 = innList.find { it.inningsNumber == 2 } ?: return

        val inn1Score = inn1.totalRuns
        val inn2Score = secondInningsRuns

        var result = ""
        if (inn1Score > inn2Score) {
            val margin = inn1Score - inn2Score
            result = "${inn1.battingTeam} won by $margin runs"
        } else if (inn2Score > inn1Score) {
            val battingTeamSize = if (inn2.battingTeam == match.teamAName) match.teamAPlayerIds.size else match.teamBPlayerIds.size
            val wicketsLeft = maxOf(0, battingTeamSize - secondInningsWickets - 1)
            result = "${inn2.battingTeam} won by $wicketsLeft wickets"
        } else {
            result = "Match Tied!"
        }

        // Calculate MVP
        val matchBallsList = repository.getBallsForMatch(match.id)
        val playersList = repository.allPlayers.first()
        val mvpStats = StatsCalculator.calculateMatchMVP(playersList, match, matchBallsList)

        val updatedMatch = match.copy(
            isCompleted = true,
            resultText = result,
            mvpPlayerId = mvpStats?.playerId
        )

        repository.updateMatch(updatedMatch)
        _activeMatchId.value = null // Clears scoring view and returns to dashboard or history!
        loadAllBalls() // Refresh career stats immediately
    }

    // --- UNDO BALL ACTIONS ---
    fun undoLastBall() {
        val match = _activeMatch.value ?: return
        val currentInn = _activeInnings.value.find { !it.isCompleted } ?: _activeInnings.value.lastOrNull() ?: return

        viewModelScope.launch {
            // Check if we are at the very start of the second innings with no balls yet
            if (currentInn.inningsNumber == 2) {
                val currentInnBalls = _activeMatchBalls.value.filter { it.inningsNumber == 2 }
                if (currentInnBalls.isEmpty()) {
                    // Delete 2nd innings and reopen 1st innings
                    repository.deleteInningsByNumber(match.id, 2)
                    val inn1 = repository.getInningsForMatch(match.id).find { it.inningsNumber == 1 }
                    if (inn1 != null) {
                        repository.updateInnings(inn1.copy(isCompleted = false))
                    }
                    _batsmanOnStrikeId.value = null
                    _batsmanNonStrikeId.value = null
                    _activeBowlerId.value = null
                    reloadActiveMatchDetails()
                    autoSyncToFirebase()
                    return@launch
                }
            }

            val lastBallOfInnings = repository.getLastBallOfInnings(match.id, currentInn.inningsNumber)
            if (lastBallOfInnings != null) {
                // Delete latest ball in DB
                repository.deleteBallById(lastBallOfInnings.id)

                // Decouple stats subtract
                val isValidOfLast = lastBallOfInnings.extraType != "WIDE" && lastBallOfInnings.extraType != "NO_BALL"
                
                var subtractRuns = lastBallOfInnings.runs + lastBallOfInnings.extraRuns
                var subtractWickets = if (lastBallOfInnings.isWicket) 1 else 0
                var subtractBalls = if (isValidOfLast) 1 else 0

                val restoredInnings = currentInn.copy(
                    totalRuns = maxOf(0, currentInn.totalRuns - subtractRuns),
                    totalWickets = maxOf(0, currentInn.totalWickets - subtractWickets),
                    totalBallsBowled = maxOf(0, currentInn.totalBallsBowled - subtractBalls),
                    isCompleted = false // reopen innings if Undo deletes the match-winner delivery
                )

                // Re-update innings and if match was completed, reopen it.
                repository.updateInnings(restoredInnings)

                if (match.isCompleted) {
                    repository.updateMatch(match.copy(isCompleted = false, resultText = "", mvpPlayerId = null))
                }

                // Clear current in-memory lineup to force a full chronological recalculation of preceding states
                _batsmanOnStrikeId.value = null
                _batsmanNonStrikeId.value = null
                _activeBowlerId.value = null

                reloadActiveMatchDetails()
            }
            autoSyncToFirebase()
        }
    }

    // --- FORCE INNINGS / MATCH SEAMLESS TRANSITIONS ---
    fun forceEndActiveInnings() {
        viewModelScope.launch {
            val match = _activeMatch.value ?: return@launch
            val currentInn = _activeInnings.value.find { !it.isCompleted } ?: return@launch
            
            val updatedInnings = currentInn.copy(isCompleted = true)
            repository.updateInnings(updatedInnings)
            
            _batsmanOnStrikeId.value = null
            _batsmanNonStrikeId.value = null
            _activeBowlerId.value = null
            
            reloadActiveMatchDetails()
            autoSyncToFirebase()
        }
    }

    fun startSecondInnings() {
        val match = _activeMatch.value ?: return
        val currentInnings = _activeInnings.value.find { it.inningsNumber == 1 } ?: return
        
        viewModelScope.launch {
            val nextBattingTeam = currentInnings.bowlingTeam
            val nextBowlingTeam = currentInnings.battingTeam
            val targetScore = currentInnings.totalRuns + 1

            // Create Second Innings
            val innings2 = Innings(
                matchId = match.id,
                inningsNumber = 2,
                battingTeam = nextBattingTeam,
                bowlingTeam = nextBowlingTeam,
                target = targetScore
            )
            repository.insertInnings(innings2)
            
            // Clear prior striker / non-striker / bowler to enforce select dialog on start of innings!
            _batsmanOnStrikeId.value = null
            _batsmanNonStrikeId.value = null
            _activeBowlerId.value = null
            
            reloadActiveMatchDetails()
            autoSyncToFirebase()
        }
    }

    fun completeActiveMatchManual() {
        val match = _activeMatch.value ?: return
        val currentInnings = _activeInnings.value.find { it.inningsNumber == 2 } ?: return
        viewModelScope.launch {
            completeMatch(match, currentInnings.totalRuns, currentInnings.totalWickets)
        }
    }

    private suspend fun reloadActiveMatchDetails() {
        val id = _activeMatchId.value ?: return
        _activeMatch.value = repository.getMatchById(id)
        val inningsList = repository.getInningsForMatch(id)
        _activeInnings.value = inningsList
        val ballsList = repository.getBallsForMatch(id)
        _activeMatchBalls.value = ballsList

        // Re-align strikers based on new balls ONLY if they are not already set!
        if (_batsmanOnStrikeId.value == null || _batsmanNonStrikeId.value == null) {
            inferScoringLineup(_activeMatch.value!!, inningsList, ballsList)
        }
    }

    // --- MANAGE PLAYERS ---
    fun addPlayer(name: String, nickname: String, mobileNumber: String, avatarUrl: String = "0") {
        viewModelScope.launch {
            val newPlayer = Player(
                name = name,
                nickname = nickname,
                mobileNumber = mobileNumber,
                profilePhotoUri = avatarUrl
            )
            repository.insertPlayer(newPlayer)
            autoSyncToFirebase()
        }
    }

    fun editPlayer(id: Long, name: String, nickname: String, mobileNumber: String, avatarUrl: String) {
        viewModelScope.launch {
            val updated = Player(
                id = id,
                name = name,
                nickname = nickname,
                mobileNumber = mobileNumber,
                profilePhotoUri = avatarUrl
            )
            repository.updatePlayer(updated)
            autoSyncToFirebase()
        }
    }

    fun deletePlayer(id: Long) {
        viewModelScope.launch {
            repository.deletePlayerById(id)
            try {
                syncManager.deletePlayerFromCloud(id)
            } catch (e: Exception) {
                android.util.Log.e("ScorerViewModel", "Failed to delete player from cloud: ${e.message}")
            }
            autoSyncToFirebase()
        }
    }

    fun deleteMatch(id: Long) {
        viewModelScope.launch {
            repository.deleteMatchById(id)
            try {
                syncManager.deleteMatchFromCloud(id)
            } catch (e: Exception) {
                android.util.Log.e("ScorerViewModel", "Failed to delete match from cloud: ${e.message}")
            }
            if (_activeMatchId.value == id) {
                _activeMatchId.value = null
            }
            loadAllBalls()
            autoSyncToFirebase()
        }
    }

    fun updateMatchFields(matchId: Long, name: String, teamAName: String, teamBName: String) {
        viewModelScope.launch {
            val match = repository.getMatchById(matchId) ?: return@launch
            val oldTeamA = match.teamAName
            val oldTeamB = match.teamBName

            val updated = match.copy(
                name = name,
                teamAName = teamAName,
                teamBName = teamBName
            )
            repository.updateMatch(updated)

            // Update innings team names
            val innings = repository.getInningsForMatch(matchId)
            for (inn in innings) {
                var updatedInning = inn
                if (inn.battingTeam == oldTeamA) {
                    updatedInning = updatedInning.copy(battingTeam = teamAName)
                } else if (inn.battingTeam == oldTeamB) {
                    updatedInning = updatedInning.copy(battingTeam = teamBName)
                }

                if (inn.bowlingTeam == oldTeamA) {
                    updatedInning = updatedInning.copy(bowlingTeam = teamAName)
                } else if (inn.bowlingTeam == oldTeamB) {
                    updatedInning = updatedInning.copy(bowlingTeam = teamBName)
                }

                if (updatedInning != inn) {
                    repository.updateInnings(updatedInning)
                }
            }

            autoSyncToFirebase()
        }
    }

    // --- MANAGE MATCHES / CREATOR ---
    fun createMatch(
        name: String,
        teamAName: String,
        teamBName: String,
        numberOfOvers: Int,
        tossWinner: String,
        tossDecision: String,
        teamAPlayerIds: List<Long>,
        teamBPlayerIds: List<Long>,
        commonPlayerIds: List<Long>
    ) {
        viewModelScope.launch {
            // 1. Create and Insert Match
            val match = Match(
                name = name,
                teamAName = teamAName,
                teamBName = teamBName,
                numberOfOvers = numberOfOvers,
                tossWinnerName = tossWinner,
                tossDecision = tossDecision,
                teamAPlayerIdsString = teamAPlayerIds.joinToString(","),
                teamBPlayerIdsString = teamBPlayerIds.joinToString(","),
                commonPlayerIdsString = commonPlayerIds.joinToString(",")
            )

            val matchId = repository.insertMatch(match)

            // 2. Create Innings 1
            val firstBattingTeam = if (tossWinner == teamAName) {
                if (tossDecision == "BAT") teamAName else teamBName
            } else {
                if (tossDecision == "BAT") teamBName else teamAName
            }
            val firstBowlingTeam = if (firstBattingTeam == teamAName) teamBName else teamAName

            val innings1 = Innings(
                matchId = matchId,
                inningsNumber = 1,
                battingTeam = firstBattingTeam,
                bowlingTeam = firstBowlingTeam
            )
            repository.insertInnings(innings1)

            // Select this match to score live
            selectActiveMatch(matchId)
            autoSyncToFirebase()
        }
    }

    // --- LATE PLAYER ADDER FEATURE (Very Important rule) ---
    fun addLatePlayerToActiveMatch(player: Player, assignTo: String) {
        val currentMatch = _activeMatch.value ?: return
        viewModelScope.launch {
            // Save or look up player ID
            val playerId = if (player.id == 0L) {
                // Insert brand new player to master database first
                repository.insertPlayer(player)
            } else {
                player.id
            }

            // Assign to list
            val updatedTeamAPlayerIds = currentMatch.teamAPlayerIds.toMutableList()
            val updatedTeamBPlayerIds = currentMatch.teamBPlayerIds.toMutableList()
            val updatedCommonPlayerIds = currentMatch.commonPlayerIds.toMutableList()

            when (assignTo) {
                "TEAM_A" -> {
                    if (playerId !in updatedTeamAPlayerIds) updatedTeamAPlayerIds.add(playerId)
                }
                "TEAM_B" -> {
                    if (playerId !in updatedTeamBPlayerIds) updatedTeamBPlayerIds.add(playerId)
                }
                "COMMON" -> {
                    if (playerId !in updatedCommonPlayerIds) updatedCommonPlayerIds.add(playerId)
                }
            }

            // Update Match record
            val updatedMatch = currentMatch.copy(
                teamAPlayerIdsString = updatedTeamAPlayerIds.joinToString(","),
                teamBPlayerIdsString = updatedTeamBPlayerIds.joinToString(","),
                commonPlayerIdsString = updatedCommonPlayerIds.joinToString(",")
            )

            repository.updateMatch(updatedMatch)
            reloadActiveMatchDetails()
            autoSyncToFirebase()
        }
    }


}
