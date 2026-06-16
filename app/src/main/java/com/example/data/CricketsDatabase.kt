package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Player::class, Match::class, Innings::class, Ball::class],
    version = 1,
    exportSchema = false
)
abstract class CricketsDatabase : RoomDatabase() {
    abstract val dao: CricketsDao

    companion object {
        @Volatile
        private var INSTANCE: CricketsDatabase? = null

        fun getDatabase(context: Context): CricketsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CricketsDatabase::class.java,
                    "society_cricket_scorer_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
