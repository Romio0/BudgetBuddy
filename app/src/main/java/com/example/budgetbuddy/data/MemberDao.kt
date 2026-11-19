package com.example.budgetbuddy.data

import androidx.room.*

@Dao
interface MemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: Member)

    @Query("SELECT * FROM members WHERE groupId = :groupId")
    suspend fun getMembersForGroup(groupId: String): List<Member>

    @Delete
    suspend fun deleteMember(member: Member)
}