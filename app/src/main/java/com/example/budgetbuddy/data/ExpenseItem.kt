package com.example.budgetbuddy.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "expense_items",
    foreignKeys = [
        ForeignKey(
            entity = SharedGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId"])]
)
data class ExpenseItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val title: String,
    val category: String,
    val amount: Double,
    val paidBy: String,
    val splitAmong: String,
    val date: String
) {
    fun getSplitList(): List<String> = splitAmong.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}