package com.example.budgetbuddy.data

import androidx.room.*

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: SharedGroup)

    @Query("SELECT * FROM shared_groups")
    suspend fun getAllGroups(): List<SharedGroup>

    @Query("SELECT * FROM shared_groups WHERE id = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: String): SharedGroup?

    @Update
    suspend fun updateGroup(group: SharedGroup)

    @Delete
    suspend fun deleteGroup(group: SharedGroup)
}