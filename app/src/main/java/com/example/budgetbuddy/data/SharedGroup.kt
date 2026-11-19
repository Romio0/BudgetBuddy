package com.example.budgetbuddy.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "shared_groups")
data class SharedGroup (

@PrimaryKey
val id: String = UUID.randomUUID().toString(),
val groupName: String,
val groupCode: String
)