package com.example.budgetbuddy.data

class ExpenseRepository(private val db: AppDatabase) {

    suspend fun createGroupWithMembers(groupName: String, members: List<String>): SharedGroup {
        val code = "BUD-" + (100..999).random()
        val group = SharedGroup(groupName = groupName, groupCode = code)
        db.groupDao().insertGroup(group)

        for (name in members) {
            if (name.isNotBlank()) {
                db.memberDao().insertMember(Member(groupId = group.id, memberName = name.trim()))
            }
        }
        return group
    }

    suspend fun getAllGroups(): List<SharedGroup> = db.groupDao().getAllGroups()

    suspend fun getGroupById(groupId: String): SharedGroup? = db.groupDao().getGroupById(groupId)

    suspend fun updateGroup(group: SharedGroup) = db.groupDao().updateGroup(group)

    suspend fun deleteGroup(group: SharedGroup) = db.groupDao().deleteGroup(group)

    suspend fun getMembersForGroup(groupId: String): List<Member> = db.memberDao().getMembersForGroup(groupId)

    suspend fun addMemberToGroup(groupId: String, memberName: String)
    {
        if (memberName.isNotBlank()) {
            db.memberDao().insertMember(Member(groupId = groupId, memberName = memberName.trim()))
        }
    }

    suspend fun deleteMember(member: Member) = db.memberDao().deleteMember(member)

    suspend fun addExpense(expense: ExpenseItem) {
        db.expenseDao().insertExpense(expense)
    }

    suspend fun getExpensesForGroup(groupId: String): List<ExpenseItem> = db.expenseDao().getExpensesForGroup(groupId)

    suspend fun getAllExpenses(): List<ExpenseItem> = db.expenseDao().getAllExpenses()

    suspend fun updateExpense(expense: ExpenseItem) = db.expenseDao().updateExpense(expense)

    suspend fun deleteExpense(expense: ExpenseItem) = db.expenseDao().deleteExpense(expense)
}