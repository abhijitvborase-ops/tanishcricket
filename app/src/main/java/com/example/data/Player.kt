package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nickname: String = "",
    val mobileNumber: String = "",
    val profilePhotoUri: String = "", // Can be custom icon index or image URI
    val createdDate: Long = System.currentTimeMillis()
) : Serializable
