package com.example.budgetbuddy.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.budgetbuddy.R
import com.example.budgetbuddy.data.AppDatabase
import com.example.budgetbuddy.data.ExpenseItem
import com.example.budgetbuddy.data.ExpenseRepository
import com.example.budgetbuddy.data.SharedGroup
import com.example.budgetbuddy.utils.PrefsManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var repository: ExpenseRepository
    private lateinit var prefs: PrefsManager

    private lateinit var tvGreeting: TextView
    private lateinit var tvCurrentDate: TextView
    private lateinit var tvProfileAvatar: TextView
    private lateinit var tvTotalOwed: TextView
    private lateinit var tvTotalOwe: TextView
    private lateinit var lvGroups: ListView
    private lateinit var tvEmptyGroups: TextView

    private var groupDisplayList: List<GroupUiModel> = emptyList()

    data class GroupUiModel(
        val group: SharedGroup,
        val netBalance: Double // موجب = ليك فلوس، سالب = عليك فلوس، صفر = خالصين
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        prefs = PrefsManager(this)
        repository = ExpenseRepository(AppDatabase.getInstance(this))

        // ربط عناصر الواجهة
        tvGreeting = findViewById(R.id.tvGreeting)
        tvCurrentDate = findViewById(R.id.tvCurrentDate)
        tvProfileAvatar = findViewById(R.id.tvProfileAvatar)
        tvTotalOwed = findViewById(R.id.tvTotalOwed)
        tvTotalOwe = findViewById(R.id.tvTotalOwe)
        lvGroups = findViewById(R.id.lvGroups)
        tvEmptyGroups = findViewById(R.id.tvEmptyGroups)

        val btnCreateGroup = findViewById<Button>(R.id.btnCreateGroup)
        val btnJoinGroup = findViewById<Button>(R.id.btnJoinGroup)
        val btnSettleUp = findViewById<Button>(R.id.btnSettleUp)
        val navAddExpense = findViewById<LinearLayout>(R.id.navAddExpense)
        val navActivity = findViewById<LinearLayout>(R.id.navActivity)

        setupHeaderInfo()

        // التنقل لشاشة البروفايل عند الضغط على الصورة أو التحية
        tvProfileAvatar.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        tvGreeting.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // أزرار إنشاء جروب والانضمام بكود
        btnCreateGroup.setOnClickListener { showCreateGroupDialog() }
        btnJoinGroup.setOnClickListener { showJoinGroupDialog() }

        // زرار Settle Up يفتح سجل المصاريف والتسويات
        btnSettleUp.setOnClickListener {
            startActivity(Intent(this, AllExpensesActivity::class.java))
        }

        // شريط التنقل السفلي
        navAddExpense.setOnClickListener {
            startActivity(Intent(this, AddEditExpenseActivity::class.java))
        }

        navActivity.setOnClickListener {
            startActivity(Intent(this, AllExpensesActivity::class.java))
        }

        // فتح تفاصيل الجروب عند الضغط على أي صف
        lvGroups.setOnItemClickListener { _, _, position, _ ->
            val selected = groupDisplayList[position].group
            val intent = Intent(this, GroupDetailsActivity::class.java).apply {
                putExtra("GROUP_ID", selected.id)
                putExtra("GROUP_CODE", selected.groupCode)
                putExtra("GROUP_NAME", selected.groupName)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        setupHeaderInfo()
        loadDashboardData()
    }

    private fun setupHeaderInfo() {
        val userName = prefs.getUserName().ifBlank { "Friend" }
        val firstName = userName.split(" ").firstOrNull() ?: userName
        tvGreeting.text = "Good Morning, $firstName"
        tvProfileAvatar.text = firstName.firstOrNull()?.uppercase() ?: "U"

        val formatter = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
        tvCurrentDate.text = formatter.format(Date())

        val ivProfileAvatar = findViewById<android.widget.ImageView>(R.id.ivProfileAvatar)
        val photoUriStr = prefs.getProfileImage()
        if (photoUriStr.isNotEmpty()) {
            ivProfileAvatar.visibility = View.VISIBLE
            ivProfileAvatar.clipToOutline = true
            ivProfileAvatar.setImageURI(android.net.Uri.parse(photoUriStr))
            tvProfileAvatar.visibility = View.GONE
        } else {
            ivProfileAvatar.visibility = View.GONE
            tvProfileAvatar.visibility = View.VISIBLE
        }

    }

    private fun loadDashboardData() {
        val currentUserName = prefs.getUserName().trim()

        lifecycleScope.launch {
            val groups = repository.getAllGroups()
            val allExpenses = repository.getAllExpenses()

            var totalOwedToMe = 0.0
            var totalIOwe = 0.0

            val uiModels = groups.map { group ->
                val groupExpenses = allExpenses.filter { it.groupId == group.id }
                val net = calculateUserNetInGroup(currentUserName, groupExpenses)

                if (net > 0.01) totalOwedToMe += net
                else if (net < -0.01) totalIOwe += abs(net)

                GroupUiModel(group = group, netBalance = net)
            }

            groupDisplayList = uiModels
            tvTotalOwed.text = "${totalOwedToMe.toInt()} EGP"
            tvTotalOwe.text = "${totalIOwe.toInt()} EGP"

            if (uiModels.isEmpty()) {
                tvEmptyGroups.visibility = View.VISIBLE
                lvGroups.visibility = View.GONE
            } else {
                tvEmptyGroups.visibility = View.GONE
                lvGroups.visibility = View.VISIBLE
                lvGroups.adapter = GroupsAdapter(uiModels)
            }
        }
    }

    private fun calculateUserNetInGroup(userName: String, expenses: List<ExpenseItem>): Double {
        if (userName.isBlank()) return 0.0
        var net = 0.0
        for (expense in expenses) {
            val splitList = expense.getSplitList()
            if (splitList.isEmpty()) continue
            val share = expense.amount / splitList.size

            val isPayer = expense.paidBy.equals(userName, ignoreCase = true)
            val isInSplit = splitList.any { it.equals(userName, ignoreCase = true) }

            if (isPayer) {
                net += if (isInSplit) (expense.amount - share) else expense.amount
            } else if (isInSplit) {
                net -= share
            }
        }
        return net
    }

    private fun showCreateGroupDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 10)
        }

        val currentUser = prefs.getUserName()
        val etGroupName = EditText(this).apply {
            hint = "Group Name (e.g., LA Trip)"
        }
        val etMembers = EditText(this).apply {
            hint = "Members separated by comma"
            if (currentUser.isNotBlank()) {
                setText("$currentUser, ")
                setSelection(text.length)
            }
        }

        layout.addView(etGroupName)
        layout.addView(etMembers)

        AlertDialog.Builder(this)
            .setTitle("Create New Group")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val name = etGroupName.text.toString().trim()
                val membersRaw = etMembers.text.toString()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Please enter a group name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val membersList = membersRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                lifecycleScope.launch {
                    val createdGroup = repository.createGroupWithMembers(name, membersList)
                    repository.linkGroupToUser(prefs.getUserId(), createdGroup.groupCode)
                    Toast.makeText(
                        this@MainActivity,
                        "Created! Code: ${createdGroup.groupCode}",
                        Toast.LENGTH_LONG
                    ).show()
                    loadDashboardData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showJoinGroupDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 10)
        }

        val etCode = EditText(this).apply {
            hint = "Enter Group Code (e.g., BUD-369)"
            inputType = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        container.addView(etCode)

        AlertDialog.Builder(this)
            .setTitle("Join Group by Code")
            .setView(container)
            .setPositiveButton("Join") { _, _ ->
                val code = etCode.text.toString().trim()
                if (code.isEmpty()) {
                    Toast.makeText(this, "Please enter a code", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                Toast.makeText(this, "Searching cloud...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    val result = repository.joinGroupByCode(code)
                    result.onSuccess { group ->
                        repository.linkGroupToUser(prefs.getUserId(), group.groupCode)
                        Toast.makeText(this@MainActivity, "Joined ${group.groupName}!", Toast.LENGTH_SHORT).show()
                        loadDashboardData()
                    }.onFailure { error ->
                        Toast.makeText(this@MainActivity, error.message ?: "Failed to join", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Adapter مخصص لعرض كروت الجروبات بنفس التصميم
    inner class GroupsAdapter(private val items: List<GroupUiModel>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.item_group, parent, false)

            val item = items[position]
            val tvInitials = view.findViewById<TextView>(R.id.tvGroupInitials)
            val tvName = view.findViewById<TextView>(R.id.tvItemGroupName)
            val tvStatus = view.findViewById<TextView>(R.id.tvItemGroupStatus)

            val words = item.group.groupName.trim().split(" ").filter { it.isNotEmpty() }
            val initials = when {
                words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
                words.size == 1 -> words[0].take(2).uppercase()
                else -> "GR"
            }

            tvInitials.text = initials
            tvName.text = item.group.groupName

            when {
                item.netBalance > 0.01 -> {
                    tvStatus.text = "You're Owed ${item.netBalance.toInt()} EGP  •  ${item.group.groupCode}"
                    tvStatus.setTextColor(Color.parseColor("#16A34A")) // أخضر
                }
                item.netBalance < -0.01 -> {
                    tvStatus.text = "You Owe ${abs(item.netBalance).toInt()} EGP  •  ${item.group.groupCode}"
                    tvStatus.setTextColor(Color.parseColor("#DC2626")) // أحمر
                }
                else -> {
                    tvStatus.text = "Settled Up  •  Code: ${item.group.groupCode}"
                    tvStatus.setTextColor(Color.parseColor("#6B7280")) // رمادي
                }
            }

            return view
        }
    }
}