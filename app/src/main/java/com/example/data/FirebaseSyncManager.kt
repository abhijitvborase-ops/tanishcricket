package com.example.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
class FirebaseSyncManager(private val context: Context) {
    private val db: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "Firebase Firestore is not available: ${e.message}")
            null
        }
    }

    private fun getFirebaseAuth(): com.google.firebase.auth.FirebaseAuth {
        return try {
            com.google.firebase.auth.FirebaseAuth.getInstance()
        } catch (e: Exception) {
            throw IllegalStateException("Google Firebase Authentication is not available on this device.")
        }
    }

    private val repository = CricketsRepository(context)

    private var matchesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var playersListener: com.google.firebase.firestore.ListenerRegistration? = null

    // Helper extension to await Firebase Tasks nicely using coroutines
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: RuntimeException("Firebase Task failed"))
            }
        }
    }

    /**
     * Helper to retrieve shared root-level collections
     */
    private fun getCollection(name: String): com.google.firebase.firestore.CollectionReference {
        val firestore = db ?: throw IllegalStateException("Google Firestore is not available on this device.")
        val uid = getFirebaseAuth().currentUser?.uid
            ?: throw IllegalStateException("Google Firebase Sign-In required to write or sync with Firestore.")
        return firestore.collection(name)
    }

    /**
     * Saves user details inside Firestore 'users' collection
     */
    suspend fun saveUserProfile() {
        val auth = try { getFirebaseAuth() } catch (e: Exception) { return }
        val user = auth.currentUser ?: run {
            Log.w("FirebaseSyncManager", "[UPLOAD-USER] No authenticated user found. Skipping user profile save.")
            return
        }
        val firestore = db ?: run {
            Log.w("FirebaseSyncManager", "Firestore is not available. Skipping profile save.")
            return
        }
        val userMap = mapOf(
            "uid" to user.uid,
            "email" to (user.email ?: ""),
            "displayName" to (user.displayName ?: "Cricket Scorer User"),
            "profilePhotoUrl" to (user.photoUrl?.toString() ?: ""),
            "lastLogin" to System.currentTimeMillis()
        )
        val path = "users/${user.uid}"
        Log.d("FirebaseSyncManager", "[UPLOAD-USER] Initiating write to Firestore profile. Path: $path, Data: $userMap")
        try {
            firestore.collection("users").document(user.uid)
                .set(userMap, SetOptions.merge())
                .await()
            Log.d("FirebaseSyncManager", "[UPLOAD-USER] SUCCESS! Successfully updated profile in Firestore. Path: $path")
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "[UPLOAD-USER] FAILED to save user profile at Path: $path. Error: ${e.message}. (Hint: Verify Firestore security rules for collection 'users')", e)
            throw e
        }
    }

    /**
     * Migrates legacy user-specific data to shared root society collections.
     */
    suspend fun migrateUserDataToShared(uid: String) {
        val firestore = db ?: return
        Log.i("FirebaseSyncManager", "[MIGRATION] Checking for legacy user-specific data to migrate for: $uid")
        val collections = listOf("players", "matches", "innings", "balls")
        for (col in collections) {
            try {
                val legacySnap = firestore.collection("users").document(uid).collection(col).get().await()
                if (!legacySnap.isEmpty) {
                    Log.i("FirebaseSyncManager", "[MIGRATION] Found ${legacySnap.size()} legacy documents in users/$uid/$col. Migrating to root shared collection '$col'...")
                    for (doc in legacySnap.documents) {
                        try {
                            firestore.collection(col).document(doc.id)
                                .set(doc.data ?: continue, SetOptions.merge())
                                .await()
                            Log.v("FirebaseSyncManager", "[MIGRATION] Copied document ${doc.id} to root '$col'.")
                            
                            // Delete legacy document to avoid recurring migrations
                            firestore.collection("users").document(uid).collection(col).document(doc.id)
                                .delete()
                                .await()
                        } catch (docEx: Exception) {
                            Log.e("FirebaseSyncManager", "[MIGRATION-ERROR] FAILED migrating document ${doc.id} in '$col': ${docEx.message}")
                        }
                    }
                    Log.i("FirebaseSyncManager", "[MIGRATION] Successfully finished migration of legacy users/$uid/$col.")
                }
            } catch (e: Exception) {
                Log.w("FirebaseSyncManager", "[MIGRATION-INFO] No action or unable to read legacy collection '$col': ${e.message}")
            }
        }
    }

    /**
     * Pushes all local database entities to Firestore under the shared society scope
     */
    suspend fun pushLocalToCloud() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.e("FirebaseSyncManager", "[UPLOAD-ERROR] Cannot push local to cloud. No authenticated user.")
            throw IllegalStateException("Google Firebase Sign-In required to write or sync with Firestore.")
        }

        // Ensure legacy user data is migrated to shared root-level collections first
        try {
            migrateUserDataToShared(uid)
        } catch (migre: Exception) {
            Log.e("FirebaseSyncManager", "[MIGRATION-FATAL] Legacy migration fail: ${migre.message}")
        }

        Log.i("FirebaseSyncManager", "[UPLOAD-START] Sync started for user: $uid")

        try {
            // 1. Sync Players
            val playersList = repository.allPlayers.first()
            Log.d("FirebaseSyncManager", "[UPLOAD-PLAYERS] Found ${playersList.size} local players to sync.")
            for (player in playersList) {
                val playerMap = mapOf(
                    "id" to player.id,
                    "name" to player.name,
                    "nickname" to player.nickname,
                    "mobileNumber" to player.mobileNumber,
                    "profilePhotoUri" to player.profilePhotoUri,
                    "createdDate" to player.createdDate
                )
                val docPath = "players/${player.id}"
                Log.v("FirebaseSyncManager", "[UPLOAD-PLAYER] Writing document. Path: $docPath, Data: $playerMap")
                try {
                    getCollection("players").document(player.id.toString())
                        .set(playerMap, SetOptions.merge())
                        .await()
                    Log.d("FirebaseSyncManager", "[UPLOAD-PLAYER] SUCCESS. Document written to: $docPath")
                } catch (e: Exception) {
                    Log.e("FirebaseSyncManager", "[UPLOAD-PLAYER] PERMISSION DENIED or WRITE FAILED inside shared 'players' collection! Path: $docPath. Error: ${e.message}. " +
                            "(Check Firestore security rules for collection 'players')", e)
                    throw e
                }
            }

            // 2. Sync Matches
            val matchesList = repository.allMatches.first()
            Log.d("FirebaseSyncManager", "[UPLOAD-MATCHES] Found ${matchesList.size} local matches to sync.")
            for (match in matchesList) {
                val matchMap = mapOf(
                    "id" to match.id,
                    "name" to match.name,
                    "date" to match.date,
                    "teamAName" to match.teamAName,
                    "teamBName" to match.teamBName,
                    "numberOfOvers" to match.numberOfOvers,
                    "tossWinnerName" to tossWinnerNameMap(match.tossWinnerName),
                    "tossDecision" to match.tossDecision,
                    "isCompleted" to match.isCompleted,
                    "resultText" to match.resultText,
                    "mvpPlayerId" to match.mvpPlayerId,
                    "teamAPlayerIdsString" to match.teamAPlayerIdsString,
                    "teamBPlayerIdsString" to match.teamBPlayerIdsString,
                    "commonPlayerIdsString" to match.commonPlayerIdsString
                )
                val matchPath = "matches/${match.id}"
                Log.v("FirebaseSyncManager", "[UPLOAD-MATCH] Writing document. Path: $matchPath, Data: $matchMap")
                try {
                    getCollection("matches").document(match.id.toString())
                        .set(matchMap, SetOptions.merge())
                        .await()
                    Log.d("FirebaseSyncManager", "[UPLOAD-MATCH] SUCCESS. Document written to: $matchPath")
                } catch (e: Exception) {
                    Log.e("FirebaseSyncManager", "[UPLOAD-MATCH] WRITE FAILED inside shared 'matches' collection! Path: $matchPath. Error: ${e.message}. " +
                            "(Verify security rules for collection 'matches')", e)
                    throw e
                }

                // 3. Sync Innings for this Match
                val inningsList = repository.getInningsForMatch(match.id)
                Log.d("FirebaseSyncManager", "[UPLOAD-INNINGS] Found ${inningsList.size} local innings to sync for match: ${match.id}")
                for (inn in inningsList) {
                    val inningsMap = mapOf(
                        "id" to inn.id,
                        "matchId" to inn.matchId,
                        "inningsNumber" to inn.inningsNumber,
                        "battingTeam" to inn.battingTeam,
                        "bowlingTeam" to inn.bowlingTeam,
                        "totalRuns" to inn.totalRuns,
                        "totalWickets" to inn.totalWickets,
                        "totalBallsBowled" to inn.totalBallsBowled,
                        "isCompleted" to inn.isCompleted,
                        "target" to inn.target
                    )
                    val innPath = "innings/${inn.id}"
                    Log.v("FirebaseSyncManager", "[UPLOAD-INNINGS] Writing document. Path: $innPath, Data: $inningsMap")
                    try {
                        getCollection("innings").document(inn.id.toString())
                            .set(inningsMap, SetOptions.merge())
                            .await()
                        Log.d("FirebaseSyncManager", "[UPLOAD-INNINGS] SUCCESS. Document written to: $innPath")
                    } catch (e: Exception) {
                        Log.e("FirebaseSyncManager", "[UPLOAD-INNINGS] WRITE FAILED inside shared 'innings' collection! Path: $innPath. Error: ${e.message}. " +
                                "(Verify security rules for collection 'innings')", e)
                        throw e
                    }
                }

                // 4. Sync Balls for this Match
                val ballsList = repository.getBallsForMatch(match.id)
                Log.d("FirebaseSyncManager", "[UPLOAD-BALLS] Found ${ballsList.size} local balls to sync for match: ${match.id}")
                for (ball in ballsList) {
                    val ballMap = mapOf(
                        "id" to ball.id,
                        "matchId" to ball.matchId,
                        "inningsNumber" to ball.inningsNumber,
                        "overIndex" to ball.overIndex,
                        "ballIndexInOver" to ball.ballIndexInOver,
                        "batsmanId" to ball.batsmanId,
                        "bowlerId" to ball.bowlerId,
                        "runs" to ball.runs,
                        "extraRuns" to ball.extraRuns,
                        "extraType" to ball.extraType,
                        "isWicket" to ball.isWicket,
                        "wicketType" to ball.wicketType,
                        "dismissedPlayerId" to ball.dismissedPlayerId,
                        "fielderId" to ball.fielderId,
                        "timestamp" to ball.timestamp
                    )
                    val ballPath = "balls/${ball.id}"
                    Log.v("FirebaseSyncManager", "[UPLOAD-BALL] Writing document. Path: $ballPath, Data: $ballMap")
                    try {
                        getCollection("balls").document(ball.id.toString())
                            .set(ballMap, SetOptions.merge())
                            .await()
                        Log.d("FirebaseSyncManager", "[UPLOAD-BALL] SUCCESS. Document written to: $ballPath")
                    } catch (e: Exception) {
                        Log.e("FirebaseSyncManager", "[UPLOAD-BALL] WRITE FAILED inside shared 'balls' collection! Path: $ballPath. Error: ${e.message}. " +
                                "(Verify security rules for collection 'balls')", e)
                        throw e
                    }
                }
            }
            Log.i("FirebaseSyncManager", "[UPLOAD-COMPLETE] Successfully pushed local database to Cloud Firestore.")
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "[UPLOAD-FATAL-ERROR] Failed to push local database to cloud. Reason: ${e.message}", e)
            throw e
        }
    }

    /**
     * Deletes a player document from cloud Firestore
     */
    suspend fun deletePlayerFromCloud(playerId: Long) {
        val uid = try { getFirebaseAuth().currentUser?.uid } catch (e: Exception) { null } ?: return
        val firestore = db ?: return
        val docPath = "players/$playerId"
        Log.i("FirebaseSyncManager", "[DELETE-PLAYER] Initiating deletion in Firestore. Path: $docPath")
        try {
            firestore.collection("players").document(playerId.toString())
                .delete()
                .await()
            Log.i("FirebaseSyncManager", "[DELETE-PLAYER] SUCCESS. Player deleted from Cloud Firestore. Path: $docPath")
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "[DELETE-PLAYER] FAILED to delete player $playerId. Path: $docPath, Error: ${e.message}", e)
            throw e
        }
    }

    /**
     * Deletes a match record and its associated innings and balls from Firestore
     */
    suspend fun deleteMatchFromCloud(matchId: Long) {
        val uid = try { getFirebaseAuth().currentUser?.uid } catch (e: Exception) { null } ?: return
        val firestore = db ?: return
        Log.i("FirebaseSyncManager", "[DELETE-MATCH] Initiating deletion in Firestore. Match ID: $matchId")
        try {
            // Delete match document
            firestore.collection("matches").document(matchId.toString()).delete().await()
            Log.d("FirebaseSyncManager", "[DELETE-MATCH] Match document deleted from Cloud.")

            // Delete associated innings
            try {
                val inningsSnap = firestore.collection("innings").whereEqualTo("matchId", matchId).get().await()
                for (doc in inningsSnap.documents) {
                    firestore.collection("innings").document(doc.id).delete().await()
                }
                Log.d("FirebaseSyncManager", "[DELETE-MATCH] Associated innings deleted from Cloud.")
            } catch (err: Exception) {
                Log.w("FirebaseSyncManager", "[DELETE-MATCH-WARNING] FAILED to delete innings from Cloud: ${err.message}")
            }

            // Delete associated balls
            try {
                val ballsSnap = firestore.collection("balls").whereEqualTo("matchId", matchId).get().await()
                for (doc in ballsSnap.documents) {
                    firestore.collection("balls").document(doc.id).delete().await()
                }
                Log.d("FirebaseSyncManager", "[DELETE-MATCH] Associated balls deleted from Cloud.")
            } catch (err: Exception) {
                Log.w("FirebaseSyncManager", "[DELETE-MATCH-WARNING] FAILED to delete balls from Cloud: ${err.message}")
            }

            Log.i("FirebaseSyncManager", "[DELETE-MATCH] SUCCESS. Match and all its associated data deleted from Cloud Firestore.")
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "[DELETE-MATCH] FAILED to delete match $matchId from Cloud. Error: ${e.message}", e)
        }
    }

    /**
     * Pulls matches, players, innings and balls from shared Firestore scope to local database (Room)
     */
    suspend fun pullCloudToLocal() {
        val uid = getFirebaseAuth().currentUser?.uid
        if (uid == null) {
            Log.e("FirebaseSyncManager", "[DOWNLOAD-ERROR] Cannot pull from cloud. No authenticated user.")
            throw IllegalStateException("Google Firebase Sign-In required to write or sync with Firestore.")
        }

        // Migrate legacy user-specific data to shared root-level collections first so we can pull it!
        try {
            migrateUserDataToShared(uid)
        } catch (migre: Exception) {
            Log.e("FirebaseSyncManager", "[MIGRATION-FATAL] Legacy migration failed during pull: ${migre.message}")
        }

        Log.i("FirebaseSyncManager", "[DOWNLOAD-START] Pull started for user: $uid")

        try {
            // 1. Pull Players
            val playersCollPath = "players"
            Log.d("FirebaseSyncManager", "[DOWNLOAD-PLAYERS] Initiating fetch. Path: $playersCollPath")
            try {
                val playersSnap = getCollection("players").get().await()
                Log.d("FirebaseSyncManager", "[DOWNLOAD-PLAYERS] Fetch SUCCESS. Documents found: ${playersSnap.documents.size}")
                for (doc in playersSnap.documents) {
                    val pId = doc.getLong("id") ?: continue
                    val name = doc.getString("name") ?: "Player"
                    val nickname = doc.getString("nickname") ?: ""
                    val mobileNumber = doc.getString("mobileNumber") ?: ""
                    val profilePhotoUri = doc.getString("profilePhotoUri") ?: "1"
                    val createdDate = doc.getLong("createdDate") ?: System.currentTimeMillis()

                    val playerObj = Player(
                        id = pId,
                        name = name,
                        nickname = nickname,
                        mobileNumber = mobileNumber,
                        profilePhotoUri = profilePhotoUri,
                        createdDate = createdDate
                    )
                    Log.v("FirebaseSyncManager", "[DOWNLOAD-PLAYER] Ingesting player: ${playerObj.name} (ID: ${playerObj.id})")
                    repository.insertPlayer(playerObj)
                }
            } catch (e: Exception) {
                Log.e("FirebaseSyncManager", "[DOWNLOAD-PLAYERS] FAILED to fetch players from: $playersCollPath. Error: ${e.message}", e)
                throw e
            }

            // 2. Pull Matches
            val matchesCollPath = "matches"
            Log.d("FirebaseSyncManager", "[DOWNLOAD-MATCHES] Initiating fetch. Path: $matchesCollPath")
            try {
                val matchesSnap = getCollection("matches").get().await()
                Log.d("FirebaseSyncManager", "[DOWNLOAD-MATCHES] Fetch SUCCESS. Documents found: ${matchesSnap.documents.size}")
                for (doc in matchesSnap.documents) {
                    val mId = doc.getLong("id") ?: continue
                    val name = doc.getString("name") ?: "Match"
                    val date = doc.getLong("date") ?: System.currentTimeMillis()
                    val teamAName = doc.getString("teamAName") ?: "Team A"
                    val teamBName = doc.getString("teamBName") ?: "Team B"
                    val numberOfOvers = doc.getLong("numberOfOvers")?.toInt() ?: 5
                    val tossWinnerName = doc.getString("tossWinnerName") ?: teamAName
                    val tossDecision = doc.getString("tossDecision") ?: "BAT"
                    val isCompleted = doc.getBoolean("isCompleted") ?: false
                    val resultText = doc.getString("resultText") ?: ""
                    val mvpPlayerId = doc.getLong("mvpPlayerId")
                    val teamAPlayerIdsString = doc.getString("teamAPlayerIdsString") ?: ""
                    val teamBPlayerIdsString = doc.getString("teamBPlayerIdsString") ?: ""
                    val commonPlayerIdsString = doc.getString("commonPlayerIdsString") ?: ""

                    val matchObj = Match(
                        id = mId,
                        name = name,
                        date = date,
                        teamAName = teamAName,
                        teamBName = teamBName,
                        numberOfOvers = numberOfOvers,
                        tossWinnerName = tossWinnerName,
                        tossDecision = tossDecision,
                        isCompleted = isCompleted,
                        resultText = resultText,
                        mvpPlayerId = mvpPlayerId,
                        teamAPlayerIdsString = teamAPlayerIdsString,
                        teamBPlayerIdsString = teamBPlayerIdsString,
                        commonPlayerIdsString = commonPlayerIdsString
                    )
                    Log.v("FirebaseSyncManager", "[DOWNLOAD-MATCH] Ingesting match: ${matchObj.name} (ID: ${matchObj.id})")
                    repository.insertMatch(matchObj)
                }
            } catch (e: Exception) {
                Log.e("FirebaseSyncManager", "[DOWNLOAD-MATCHES] FAILED to fetch matches from: $matchesCollPath. Error: ${e.message}", e)
                throw e
            }

            // 3. Pull Innings
            val inningsCollPath = "innings"
            Log.d("FirebaseSyncManager", "[DOWNLOAD-INNINGS] Initiating fetch. Path: $inningsCollPath")
            try {
                val inningsSnap = getCollection("innings").get().await()
                Log.d("FirebaseSyncManager", "[DOWNLOAD-INNINGS] Fetch SUCCESS. Documents found: ${inningsSnap.documents.size}")
                for (doc in inningsSnap.documents) {
                    val innId = doc.getLong("id") ?: continue
                    val matchId = doc.getLong("matchId") ?: continue
                    val inningsNumber = doc.getLong("inningsNumber")?.toInt() ?: 1
                    val battingTeam = doc.getString("battingTeam") ?: "Batting Team"
                    val bowlingTeam = doc.getString("bowlingTeam") ?: "Bowling Team"
                    val totalRuns = doc.getLong("totalRuns")?.toInt() ?: 0
                    val totalWickets = doc.getLong("totalWickets")?.toInt() ?: 0
                    val totalBallsBowled = doc.getLong("totalBallsBowled")?.toInt() ?: 0
                    val isCompleted = doc.getBoolean("isCompleted") ?: false
                    val target = doc.getLong("target")?.toInt() ?: 0

                    val innObj = Innings(
                        id = innId,
                        matchId = matchId,
                        inningsNumber = inningsNumber,
                        battingTeam = battingTeam,
                        bowlingTeam = bowlingTeam,
                        totalRuns = totalRuns,
                        totalWickets = totalWickets,
                        totalBallsBowled = totalBallsBowled,
                        isCompleted = isCompleted,
                        target = target
                    )
                    Log.v("FirebaseSyncManager", "[DOWNLOAD-INNING] Ingesting innings ID: ${innObj.id} for match: ${innObj.matchId}")
                    repository.insertInnings(innObj)
                }
            } catch (e: Exception) {
                Log.e("FirebaseSyncManager", "[DOWNLOAD-INNINGS] FAILED to fetch innings from: $inningsCollPath. Error: ${e.message}", e)
                throw e
            }

            // 4. Pull Balls
            val ballsCollPath = "balls"
            Log.d("FirebaseSyncManager", "[DOWNLOAD-BALLS] Initiating fetch. Path: $ballsCollPath")
            try {
                val ballsSnap = getCollection("balls").get().await()
                Log.d("FirebaseSyncManager", "[DOWNLOAD-BALLS] Fetch SUCCESS. Documents found: ${ballsSnap.documents.size}")
                for (doc in ballsSnap.documents) {
                    val ballId = doc.getLong("id") ?: continue
                    val matchId = doc.getLong("matchId") ?: continue
                    val inningsNumber = doc.getLong("inningsNumber")?.toInt() ?: 1
                    val overIndex = doc.getLong("overIndex")?.toInt() ?: 0
                    val ballIndexInOver = doc.getLong("ballIndexInOver")?.toInt() ?: 1
                    val batsmanId = doc.getLong("batsmanId") ?: continue
                    val bowlerId = doc.getLong("bowlerId") ?: continue
                    val runs = doc.getLong("runs")?.toInt() ?: 0
                    val extraRuns = doc.getLong("extraRuns")?.toInt() ?: 0
                    val extraType = doc.getString("extraType")
                    val isWicket = doc.getBoolean("isWicket") ?: false
                    val wicketType = doc.getString("wicketType")
                    val dismissedPlayerId = doc.getLong("dismissedPlayerId")
                    val fielderId = doc.getLong("fielderId")
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                    val ballObj = Ball(
                        id = ballId,
                        matchId = matchId,
                        inningsNumber = inningsNumber,
                        overIndex = overIndex,
                        ballIndexInOver = ballIndexInOver,
                        batsmanId = batsmanId,
                        bowlerId = bowlerId,
                        runs = runs,
                        extraRuns = extraRuns,
                        extraType = extraType,
                        isWicket = isWicket,
                        wicketType = wicketType,
                        dismissedPlayerId = dismissedPlayerId,
                        fielderId = fielderId,
                        timestamp = timestamp
                    )
                    Log.v("FirebaseSyncManager", "[DOWNLOAD-BALL] Ingesting ball ID: ${ballObj.id} for match: ${ballObj.matchId}")
                    repository.insertBall(ballObj)
                }
            } catch (e: Exception) {
                Log.e("FirebaseSyncManager", "[DOWNLOAD-BALLS] FAILED to fetch balls from: $ballsCollPath. Error: ${e.message}", e)
                throw e
            }
            Log.i("FirebaseSyncManager", "[DOWNLOAD-COMPLETE] Successfully pulled Cloud Firestore data down to local Room database.")
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "[DOWNLOAD-FATAL-ERROR] Failed to complete download pull. Reason: ${e.message}", e)
            throw e
        }
    }

    private fun tossWinnerNameMap(value: Any?): String {
        return value?.toString() ?: ""
    }

    /**
     * Setup real-time listener to automatically synchronize Firestore user updates down to local database
     */
    fun startRealtimeListener(onUpdated: () -> Unit) {
        val auth = try { getFirebaseAuth() } catch (e: Exception) { return }
        val firestore = db ?: return

        auth.addAuthStateListener { state ->
            val uid = state.currentUser?.uid

            // Unsubscribe existing listeners to clean up memory/avoid access violations
            matchesListener?.remove()
            matchesListener = null
            playersListener?.remove()
            playersListener = null

            if (uid != null) {
                Log.d("FirebaseSyncManager", "User Logged In ($uid). Activating Firestore real-time snapshot synchronization...")
                
                // 1. Listen to matches
                matchesListener = firestore.collection("matches")
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Log.w("FirebaseSyncManager", "Listen matches failed.", e)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            for (change in snapshot.documentChanges) {
                                val doc = change.document
                                val mId = doc.getLong("id") ?: continue
                                val name = doc.getString("name") ?: "Match"
                                val date = doc.getLong("date") ?: System.currentTimeMillis()
                                val teamAName = doc.getString("teamAName") ?: "Team A"
                                val teamBName = doc.getString("teamBName") ?: "Team B"
                                val numberOfOvers = doc.getLong("numberOfOvers")?.toInt() ?: 5
                                val tossWinnerName = doc.getString("tossWinnerName") ?: teamAName
                                val tossDecision = doc.getString("tossDecision") ?: "BAT"
                                val isCompleted = doc.getBoolean("isCompleted") ?: false
                                val resultText = doc.getString("resultText") ?: ""
                                val mvpPlayerId = doc.getLong("mvpPlayerId")
                                val teamAPlayerIdsString = doc.getString("teamAPlayerIdsString") ?: ""
                                val teamBPlayerIdsString = doc.getString("teamBPlayerIdsString") ?: ""
                                val commonPlayerIdsString = doc.getString("commonPlayerIdsString") ?: ""

                                val matchObj = Match(
                                    id = mId,
                                    name = name,
                                    date = date,
                                    teamAName = teamAName,
                                    teamBName = teamBName,
                                    numberOfOvers = numberOfOvers,
                                    tossWinnerName = tossWinnerName,
                                    tossDecision = tossDecision,
                                    isCompleted = isCompleted,
                                    resultText = resultText,
                                    mvpPlayerId = mvpPlayerId,
                                    teamAPlayerIdsString = teamAPlayerIdsString,
                                    teamBPlayerIdsString = teamBPlayerIdsString,
                                    commonPlayerIdsString = commonPlayerIdsString
                                )
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                            Log.d("FirebaseSyncManager", "[REALTIME-SYNC] Match removed from Cloud. Deleting locally: $mId")
                                            repository.deleteMatchById(mId)
                                        } else {
                                            repository.insertMatch(matchObj)
                                            pullInningsForMatchBackground(uid, mId)
                                            pullBallsForMatchBackground(uid, mId)
                                        }
                                        onUpdated()
                                    } catch (err: Exception) {
                                        Log.e("FirebaseSyncManager", "Background match sync change failed: ${err.message}")
                                    }
                                }
                            }
                        }
                    }

                // 2. Listen to players
                playersListener = firestore.collection("players")
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Log.w("FirebaseSyncManager", "Listen players failed.", e)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            for (change in snapshot.documentChanges) {
                                val doc = change.document
                                val pId = doc.getLong("id") ?: continue
                                if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            Log.d("FirebaseSyncManager", "[REALTIME-SYNC] Player removed from Cloud. Deleting locally: $pId")
                                            repository.deletePlayerById(pId)
                                            onUpdated()
                                        } catch (err: Exception) {
                                            Log.e("FirebaseSyncManager", "Background player delete failed: ${err.message}")
                                        }
                                    }
                                } else {
                                    val name = doc.getString("name") ?: "Player"
                                    val nickname = doc.getString("nickname") ?: ""
                                    val mobileNumber = doc.getString("mobileNumber") ?: ""
                                    val profilePhotoUri = doc.getString("profilePhotoUri") ?: "1"
                                    val createdDate = doc.getLong("createdDate") ?: System.currentTimeMillis()

                                    val playerObj = Player(
                                        id = pId,
                                        name = name,
                                        nickname = nickname,
                                        mobileNumber = mobileNumber,
                                        profilePhotoUri = profilePhotoUri,
                                        createdDate = createdDate
                                    )
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            Log.v("FirebaseSyncManager", "[REALTIME-SYNC] Player added/modified in Cloud. Updating locally: $pId")
                                            repository.insertPlayer(playerObj)
                                            onUpdated()
                                        } catch (err: Exception) {
                                            Log.e("FirebaseSyncManager", "Background player insert failed: ${err.message}")
                                        }
                                    }
                                }
                            }
                        }
                    }
            } else {
                Log.d("FirebaseSyncManager", "No user logged in. Subscriptions cleared.")
            }
        }
    }

    private suspend fun pullInningsForMatchBackground(uid: String, matchId: Long) {
        val firestore = db ?: return
        try {
            val snap = firestore.collection("innings")
                .whereEqualTo("matchId", matchId).get().await()
            for (doc in snap.documents) {
                val innId = doc.getLong("id") ?: continue
                val inningsNumber = doc.getLong("inningsNumber")?.toInt() ?: 1
                val battingTeam = doc.getString("battingTeam") ?: "Batting"
                val bowlingTeam = doc.getString("bowlingTeam") ?: "Bowling"
                val totalRuns = doc.getLong("totalRuns")?.toInt() ?: 0
                val totalWickets = doc.getLong("totalWickets")?.toInt() ?: 0
                val totalBallsBowled = doc.getLong("totalBallsBowled")?.toInt() ?: 0
                val isCompleted = doc.getBoolean("isCompleted") ?: false
                val target = doc.getLong("target")?.toInt() ?: 0

                val innObj = Innings(
                    id = innId,
                    matchId = matchId,
                    inningsNumber = inningsNumber,
                    battingTeam = battingTeam,
                    bowlingTeam = bowlingTeam,
                    totalRuns = totalRuns,
                    totalWickets = totalWickets,
                    totalBallsBowled = totalBallsBowled,
                    isCompleted = isCompleted,
                    target = target
                )
                repository.insertInnings(innObj)
            }
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "pullInningsForMatchBackground failed: ${e.message}")
        }
    }

    private suspend fun pullBallsForMatchBackground(uid: String, matchId: Long) {
        val firestore = db ?: return
        try {
            val snap = firestore.collection("balls")
                .whereEqualTo("matchId", matchId).get().await()
            for (doc in snap.documents) {
                val ballId = doc.getLong("id") ?: continue
                val inningsNumber = doc.getLong("inningsNumber")?.toInt() ?: 1
                val overIndex = doc.getLong("overIndex")?.toInt() ?: 0
                val ballIndexInOver = doc.getLong("ballIndexInOver")?.toInt() ?: 1
                val batsmanId = doc.getLong("batsmanId") ?: continue
                val bowlerId = doc.getLong("bowlerId") ?: continue
                val runs = doc.getLong("runs")?.toInt() ?: 0
                val extraRuns = doc.getLong("extraRuns")?.toInt() ?: 0
                val extraType = doc.getString("extraType")
                val isWicket = doc.getBoolean("isWicket") ?: false
                val wicketType = doc.getString("wicketType")
                val dismissedPlayerId = doc.getLong("dismissedPlayerId")
                val fielderId = doc.getLong("fielderId")
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                val ballObj = Ball(
                    id = ballId,
                    matchId = matchId,
                    inningsNumber = inningsNumber,
                    overIndex = overIndex,
                    ballIndexInOver = ballIndexInOver,
                    batsmanId = batsmanId,
                    bowlerId = bowlerId,
                    runs = runs,
                    extraRuns = extraRuns,
                    extraType = extraType,
                    isWicket = isWicket,
                    wicketType = wicketType,
                    dismissedPlayerId = dismissedPlayerId,
                    fielderId = fielderId,
                    timestamp = timestamp
                )
                repository.insertBall(ballObj)
            }
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "pullBallsForMatchBackground failed: ${e.message}")
        }
    }
}
