package com.example.budgetbuddy.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: Member)

    @Query("SELECT * FROM members WHERE groupId = :groupId")
    suspend fun getMembersForGroup(groupId: String): List<Member>

    @Query("SELECT * FROM members WHERE groupId = :groupId ORDER BY memberName ASC")
    fun observeMembersForGroup(groupId: String): Flow<List<Member>>

    @Query("SELECT * FROM members ORDER BY memberName ASC")
    fun observeAllMembers(): Flow<List<Member>>

    @Delete
    suspend fun deleteMember(member: Member)
}