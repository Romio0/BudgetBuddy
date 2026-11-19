package com.example.budgetbuddy.data

import androidx.room.*

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseItem)

    @Query("SELECT * FROM expense_items WHERE groupId = :groupId")
    suspend fun getExpensesForGroup(groupId: String): List<ExpenseItem>

    @Query("SELECT * FROM expense_items")
    suspend fun getAllExpenses(): List<ExpenseItem>

    @Update
    suspend fun updateExpense(expense: ExpenseItem)

    @Delete
    suspend fun deleteExpense(expense: ExpenseItem)
}