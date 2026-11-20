package com.example.budgetbuddy.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ExpenseRepository(private val db: AppDatabase) {

    private val firestore = FirebaseFirestore.getInstance()
    private val groupsCollection = firestore.collection("groups")


    private val usersCollection = firestore.collection("users")

    // =========================================================================
    //  (Sign Up, Log In & Cloud Group Recovery)
    // =========================================================================

    data class UserAccount(
        val id: String,
        val fullName: String,
        val phone: String
    )

    suspend fun registerUser(fullName: String, password: String, phone: String): Result<UserAccount> {
        val cleanName = fullName.trim()
        val cleanPhone = phone.trim()

        if (cleanName.isEmpty() || password.isEmpty() || cleanPhone.isEmpty()) {
            return Result.failure(Exception("Please fill in all fields"))
        }

        val existingSnap = awaitTask(usersCollection.whereEqualTo("fullName", cleanName).get())
            ?: return Result.failure(Exception("Network error, check your internet connection"))

        if (!existingSnap.isEmpty) {
            return Result.failure(Exception("This name is already registered, please Log In"))
        }

        val userId = java.util.UUID.randomUUID().toString()
        val userMap = mapOf(
            "id" to userId,
            "fullName" to cleanName,
            "password" to password,
            "phone" to cleanPhone,
            "groupCodes" to emptyList<String>()
        )

        awaitTask(usersCollection.document(userId).set(userMap))
            ?: return Result.failure(Exception("Failed to create account"))

        return Result.success(UserAccount(id = userId, fullName = cleanName, phone = cleanPhone))
    }

    suspend fun loginUser(fullName: String, password: String): Result<UserAccount> {
        val cleanName = fullName.trim()
        if (cleanName.isEmpty() || password.isEmpty()) {
            return Result.failure(Exception("Please enter your name and password"))
        }

        val querySnap = awaitTask(
            usersCollection
                .whereEqualTo("fullName", cleanName)
                .whereEqualTo("password", password)
                .get()
        ) ?: return Result.failure(Exception("Network error, check your internet connection"))

        if (querySnap.isEmpty) {
            return Result.failure(Exception("Invalid name or password"))
        }

        val doc = querySnap.documents.first()
        val userId = doc.getString("id") ?: doc.id
        val name = doc.getString("fullName") ?: cleanName
        val phone = doc.getString("phone") ?: ""

        val savedCodes = doc.get("groupCodes") as? List<*>
        savedCodes?.forEach { code ->
            if (code is String && code.isNotBlank()) {
                joinGroupByCode(code)
            }
        }

        return Result.success(UserAccount(id = userId, fullName = name, phone = phone))
    }

    fun linkGroupToUser(userId: String, groupCode: String) {
        if (userId.isBlank() || groupCode.isBlank()) return
        usersCollection.document(userId).update(
            "groupCodes",
            com.google.firebase.firestore.FieldValue.arrayUnion(groupCode)
        )
    }

    private var membersListener: ListenerRegistration? = null
    private var expensesListener: ListenerRegistration? = null



    suspend fun createGroupWithMembers(groupName: String, members: List<String>): SharedGroup {
        val code = "BUD-" + (100..999).random()
        val group = SharedGroup(groupName = groupName, groupCode = code)
        db.groupDao().insertGroup(group)

        val groupRef = groupsCollection.document(group.groupCode)
        groupRef.set(groupToMap(group))

        for (name in members) {
            if (name.isNotBlank()) {
                val member = Member(groupId = group.id, memberName = name.trim())
                db.memberDao().insertMember(member)
                groupRef.collection("members").document(member.id).set(memberToMap(member))
            }
        }
        return group
    }

    suspend fun getAllGroups(): List<SharedGroup> = db.groupDao().getAllGroups()

    suspend fun getGroupById(groupId: String): SharedGroup? = db.groupDao().getGroupById(groupId)

    suspend fun updateGroup(group: SharedGroup) {
        db.groupDao().updateGroup(group)
        groupsCollection.document(group.groupCode).update("groupName", group.groupName)
    }

    suspend fun deleteGroup(group: SharedGroup) {
        db.groupDao().deleteGroup(group)
        groupsCollection.document(group.groupCode).delete()
    }



    suspend fun getMembersForGroup(groupId: String): List<Member> = db.memberDao().getMembersForGroup(groupId)

    suspend fun addMemberToGroup(groupId: String, memberName: String) {
        if (memberName.isNotBlank()) {
            val member = Member(groupId = groupId, memberName = memberName.trim())
            db.memberDao().insertMember(member)

            val group = db.groupDao().getGroupById(groupId)
            if (group != null) {
                groupsCollection.document(group.groupCode)
                    .collection("members")
                    .document(member.id)
                    .set(memberToMap(member))
            }
        }
    }

    suspend fun deleteMember(member: Member) {
        db.memberDao().deleteMember(member)
        val group = db.groupDao().getGroupById(member.groupId)
        if (group != null) {
            groupsCollection.document(group.groupCode)
                .collection("members")
                .document(member.id)
                .delete()
        }
    }



    suspend fun addExpense(expense: ExpenseItem) {
        db.expenseDao().insertExpense(expense)
        syncExpenseToCloud(expense)
    }

    suspend fun getExpensesForGroup(groupId: String): List<ExpenseItem> = db.expenseDao().getExpensesForGroup(groupId)

    suspend fun getAllExpenses(): List<ExpenseItem> = db.expenseDao().getAllExpenses()

    suspend fun updateExpense(expense: ExpenseItem) {
        db.expenseDao().updateExpense(expense)
        syncExpenseToCloud(expense)
    }

    suspend fun deleteExpense(expense: ExpenseItem) {
        db.expenseDao().deleteExpense(expense)
        val group = db.groupDao().getGroupById(expense.groupId)
        if (group != null) {
            groupsCollection.document(group.groupCode)
                .collection("expenses")
                .document(expense.id)
                .delete()
        }
    }

    private suspend fun syncExpenseToCloud(expense: ExpenseItem) {
        val group = db.groupDao().getGroupById(expense.groupId)
        if (group != null) {
            groupsCollection.document(group.groupCode)
                .collection("expenses")
                .document(expense.id)
                .set(expenseToMap(expense))
        }
    }



    suspend fun joinGroupByCode(rawCode: String): Result<SharedGroup> {
        val code = rawCode.trim().uppercase()
        if (code.isBlank()) return Result.failure(Exception("برجاء إدخال كود الجروب"))

        val groupRef = groupsCollection.document(code)

        val groupSnap = awaitTask(groupRef.get())
            ?: return Result.failure(Exception("تعذر الاتصال بالسيرفر، تأكد من الإنترنت"))

        if (!groupSnap.exists()) {
            return Result.failure(Exception("كود الجروب غير صحيح أو غير موجود"))
        }

        val groupId = groupSnap.getString("id") ?: return Result.failure(Exception("بيانات الجروب غير مكتملة"))
        val groupName = groupSnap.getString("groupName") ?: "Shared Group"
        val groupCode = groupSnap.getString("groupCode") ?: code
        val sharedGroup = SharedGroup(id = groupId, groupName = groupName, groupCode = groupCode)

        if (db.groupDao().getGroupById(groupId) == null) {
            db.groupDao().insertGroup(sharedGroup)
        } else {
            db.groupDao().updateGroup(sharedGroup)
        }

        val membersSnap = awaitTask(groupRef.collection("members").get())
        val existingMemberIds = db.memberDao().getMembersForGroup(groupId).map { it.id }.toSet()
        membersSnap?.documents?.forEach { doc ->
            val mId = doc.getString("id") ?: doc.id
            val mName = doc.getString("memberName") ?: return@forEach
            if (mId !in existingMemberIds) {
                db.memberDao().insertMember(Member(id = mId, groupId = groupId, memberName = mName))
            }
        }

        val expensesSnap = awaitTask(groupRef.collection("expenses").get())
        val existingExpenseIds = db.expenseDao().getExpensesForGroup(groupId).map { it.id }.toSet()
        expensesSnap?.documents?.forEach { doc ->
            val expense = mapToExpense(doc.data, doc.id, groupId) ?: return@forEach
            if (expense.id !in existingExpenseIds) {
                db.expenseDao().insertExpense(expense)
            } else {
                db.expenseDao().updateExpense(expense)
            }
        }

        return Result.success(sharedGroup)
    }

  

    fun startGroupSync(groupCode: String, groupId: String, onDataChanged: () -> Unit) {
        stopGroupSync() 
        val groupRef = groupsCollection.document(groupCode)

              membersListener = groupRef.collection("members").addSnapshotListener { snapshots, error ->
            if (error != null || snapshots == null) {
                Log.e("FirestoreSync", "Members sync error: ${error?.message}")
                return@addSnapshotListener
            }
            CoroutineScope(Dispatchers.IO).launch {
                val localMembers = db.memberDao().getMembersForGroup(groupId)
                val localIds = localMembers.map { it.id }.toSet()
                val remoteIds = snapshots.documents.map { it.id }.toSet()

                for (doc in snapshots.documents) {
                    val mId = doc.getString("id") ?: doc.id
                    val mName = doc.getString("memberName") ?: continue
                    if (mId !in localIds) {
                        db.memberDao().insertMember(Member(id = mId, groupId = groupId, memberName = mName))
                    }
                }
                for (local in localMembers) {
                    if (local.id !in remoteIds) {
                        db.memberDao().deleteMember(local)
                    }
                }
                onDataChanged()
            }
        }

        expensesListener = groupRef.collection("expenses").addSnapshotListener { snapshots, error ->
            if (error != null || snapshots == null) {
                Log.e("FirestoreSync", "Expenses sync error: ${error?.message}")
                return@addSnapshotListener
            }
            CoroutineScope(Dispatchers.IO).launch {
                val localExpenses = db.expenseDao().getExpensesForGroup(groupId)
                val localIds = localExpenses.map { it.id }.toSet()
                val remoteIds = snapshots.documents.map { it.id }.toSet()

                for (doc in snapshots.documents) {
                    val remoteExpense = mapToExpense(doc.data, doc.id, groupId) ?: continue
                    if (remoteExpense.id !in localIds) {
                        db.expenseDao().insertExpense(remoteExpense)
                    } else {
                        db.expenseDao().updateExpense(remoteExpense)
                    }
                }
                for (local in localExpenses) {
                    if (local.id !in remoteIds) {
                        db.expenseDao().deleteExpense(local)
                    }
                }
                onDataChanged()
            }
        }
    }

    fun stopGroupSync() {
        membersListener?.remove()
        expensesListener?.remove()
        membersListener = null
        expensesListener = null
    }



    private fun groupToMap(group: SharedGroup): Map<String, Any> = mapOf(
        "id" to group.id,
        "groupName" to group.groupName,
        "groupCode" to group.groupCode
    )

    private fun memberToMap(member: Member): Map<String, Any> = mapOf(
        "id" to member.id,
        "groupId" to member.groupId,
        "memberName" to member.memberName
    )

    private fun expenseToMap(expense: ExpenseItem): Map<String, Any> = mapOf(
        "id" to expense.id,
        "groupId" to expense.groupId,
        "title" to expense.title,
        "category" to expense.category,
        "amount" to expense.amount,
        "paidBy" to expense.paidBy,
        "splitAmong" to expense.splitAmong,
        "date" to expense.date
    )

    private fun mapToExpense(data: Map<String, Any>?, fallbackId: String, groupId: String): ExpenseItem? {
        if (data == null) return null
        return ExpenseItem(
            id = data["id"] as? String ?: fallbackId,
            groupId = groupId,
            title = data["title"] as? String ?: "",
            category = data["category"] as? String ?: "Other",
            amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
            paidBy = data["paidBy"] as? String ?: "",
            splitAmong = data["splitAmong"] as? String ?: "",
            date = data["date"] as? String ?: ""
        )
    }

    private suspend fun <T> awaitTask(task: com.google.android.gms.tasks.Task<T>): T? = suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { result -> cont.resume(result) }
                .addOnFailureListener { cont.resume(null) }
        }
}
