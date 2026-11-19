package com.example.budgetbuddy.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "members",
    foreignKeys = [
        ForeignKey(entity = SharedGroup::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["groupId"])]
)
data class Member(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val memberName: String
)