package com.example.budgetbuddy.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
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
import com.example.budgetbuddy.utils.PrefsManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class GroupDetailsActivity : AppCompatActivity() {

    private lateinit var repository: ExpenseRepository
    private lateinit var prefs: PrefsManager

    private var groupId: String = ""
    private var groupCode: String = ""
    private var groupName: String = ""

    private lateinit var layoutExpensesTab: LinearLayout
    private lateinit var layoutBalancesTab: LinearLayout

    private lateinit var tvTabExpenses: TextView
    private lateinit var tvTabBalances: TextView
    private lateinit var indExpenses: View
    private lateinit var indBalances: View

    private lateinit var lvGroupExpenses: ListView
    private lateinit var tvEmptyGroupExpenses: TextView
    private lateinit var lvMemberBalances: ListView
    private lateinit var tvBannerBalanceTitle: TextView
    private lateinit var tvBannerBalanceSub: TextView

    data class MemberBalanceUi(
        val name: String,
        val netBalance: Double,
        val isCurrentUser: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_group_details)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainGroupDetails)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        prefs = PrefsManager(this)
        repository = ExpenseRepository(AppDatabase.getInstance(this))

        groupId = intent.getStringExtra("GROUP_ID") ?: ""
        groupCode = intent.getStringExtra("GROUP_CODE") ?: ""
        groupName = intent.getStringExtra("GROUP_NAME") ?: "Group"

        val btnBack = findViewById<TextView>(R.id.btnBackGroup)
        val btnSettings = findViewById<TextView>(R.id.btnGroupSettings)
        val tvAvatar = findViewById<TextView>(R.id.tvGroupDetailAvatar)
        val tvName = findViewById<TextView>(R.id.tvGroupDetailName)
        val tvCode = findViewById<TextView>(R.id.tvGroupDetailCode)

        tvName.text = groupName
        tvCode.text = "Code: $groupCode"
        val words = groupName.trim().split(" ").filter { it.isNotEmpty() }
        tvAvatar.text = when {
            words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
            words.size == 1 -> words[0].take(2).uppercase()
            else -> "GR"
        }

        btnBack.setOnClickListener { finish() }
        btnSettings.setOnClickListener { showGroupSettingsDialog() }

        layoutExpensesTab = findViewById(R.id.layoutExpensesTab)
        layoutBalancesTab = findViewById(R.id.layoutBalancesTab)

        tvTabExpenses = findViewById(R.id.tvTabExpenses)
        tvTabBalances = findViewById(R.id.tvTabBalances)
        indExpenses = findViewById(R.id.indExpenses)
        indBalances = findViewById(R.id.indBalances)

        findViewById<LinearLayout>(R.id.tabExpenses).setOnClickListener { switchTab(0) }
        findViewById<LinearLayout>(R.id.tabBalances).setOnClickListener { switchTab(1) }

        lvGroupExpenses = findViewById(R.id.lvGroupExpenses)
        tvEmptyGroupExpenses = findViewById(R.id.tvEmptyGroupExpenses)
        lvMemberBalances = findViewById(R.id.lvMemberBalances)
        tvBannerBalanceTitle = findViewById(R.id.tvBannerBalanceTitle)
        tvBannerBalanceSub = findViewById(R.id.tvBannerBalanceSub)

        findViewById<Button>(R.id.btnGroupAddExpense).setOnClickListener {
            val intent = Intent(this, AddEditExpenseActivity::class.java).apply {
                putExtra(com.example.budgetbuddy.utils.IntentKeys.EXTRA_GROUP_ID, groupId)
                putExtra("GROUP_NAME", groupName)
            }
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnBannerSettleUp).setOnClickListener {
            Toast.makeText(this, "All balances are up to date with cloud!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        loadGroupData()
        if (groupCode.isNotEmpty() && groupId.isNotEmpty()) {
            repository.startGroupSync(groupCode, groupId) {
                runOnUiThread { loadGroupData() }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        repository.stopGroupSync()
    }

    private fun switchTab(index: Int) {
        layoutExpensesTab.visibility = if (index == 0) View.VISIBLE else View.GONE
        layoutBalancesTab.visibility = if (index == 1) View.VISIBLE else View.GONE

        indExpenses.visibility = if (index == 0) View.VISIBLE else View.INVISIBLE
        indBalances.visibility = if (index == 1) View.VISIBLE else View.INVISIBLE

        tvTabExpenses.setTextColor(Color.parseColor(if (index == 0) "#111827" else "#6B7280"))
        tvTabBalances.setTextColor(Color.parseColor(if (index == 1) "#111827" else "#6B7280"))

        tvTabExpenses.setTypeface(null, if (index == 0) Typeface.BOLD else Typeface.NORMAL)
        tvTabBalances.setTypeface(null, if (index == 1) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun loadGroupData() {
        if (groupId.isEmpty()) return
        val currentUserName = prefs.getUserName().trim()

        lifecycleScope.launch {
            val allExpenses = repository.getExpensesForGroup(groupId)
            val members = repository.getMembersForGroup(groupId)

            // 1. تحديث تبويب Expenses
            if (allExpenses.isEmpty()) {
                tvEmptyGroupExpenses.visibility = View.VISIBLE
                lvGroupExpenses.visibility = View.GONE
            } else {
                tvEmptyGroupExpenses.visibility = View.GONE
                lvGroupExpenses.visibility = View.VISIBLE
                lvGroupExpenses.adapter = GroupExpensesAdapter(allExpenses)
            }


            val balancesMap = mutableMapOf<String, Double>()
            members.forEach { balancesMap[it.memberName] = 0.0 }

            for (expense in allExpenses) {
                val splitList = expense.splitAmong.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (splitList.isEmpty()) continue
                val share = expense.amount / splitList.size

                balancesMap[expense.paidBy] = (balancesMap[expense.paidBy] ?: 0.0) + expense.amount
                for (person in splitList) {
                    balancesMap[person] = (balancesMap[person] ?: 0.0) - share
                }
            }

            val balanceList = balancesMap.map { (name, net) ->
                MemberBalanceUi(
                    name = name,
                    netBalance = net,
                    isCurrentUser = name.equals(currentUserName, ignoreCase = true)
                )
            }
            lvMemberBalances.adapter = MemberBalancesAdapter(balanceList)


            val myBalance = balanceList.firstOrNull { it.isCurrentUser }?.netBalance ?: 0.0
            val topCreditor = balanceList.filter { !it.isCurrentUser && it.netBalance > 0.01 }
                .maxByOrNull { it.netBalance }

            when {
                myBalance < -0.01 -> {
                    tvBannerBalanceTitle.text = "You Owe ${abs(myBalance).toInt()}EGP In Total"
                    tvBannerBalanceSub.text = if (topCreditor != null) "To ${topCreditor.name}" else "Pending settlement"
                }
                myBalance > 0.01 -> {
                    tvBannerBalanceTitle.text = "You Are Owed ${myBalance.toInt()}EGP In Total"
                    tvBannerBalanceSub.text = "From group members"
                }
                else -> {
                    tvBannerBalanceTitle.text = "You Are Settled Up"
                    tvBannerBalanceSub.text = "No pending balances"
                }
            }
        }
    }

    private fun showGroupSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("$groupName ($groupCode)")
            .setItems(arrayOf("Share Group Code", "Add New Member")) { _, which ->
                if (which == 0) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Join my group '$groupName' on BudgetBuddy using code: $groupCode"
                        )
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Group Code"))
                } else {
                    showAddMemberDialog()
                }
            }
            .show()
    }

    private fun showAddMemberDialog() {
        val etMember = EditText(this).apply { hint = "Member Name" }
        val container = LinearLayout(this).apply {
            setPadding(50, 30, 50, 10)
            addView(etMember)
        }
        AlertDialog.Builder(this)
            .setTitle("Add Member")
            .setView(container)
            .setPositiveButton("Add") { _, _ ->
                val name = etMember.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        repository.addMemberToGroup(groupId, name)
                        loadGroupData()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class GroupExpensesAdapter(private val items: List<ExpenseItem>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@GroupDetailsActivity)
                .inflate(R.layout.item_group_expense, parent, false)

            val expense = items[position]
            val tvDateHeader = view.findViewById<TextView>(R.id.tvExpenseDateHeader)
            val tvIcon = view.findViewById<TextView>(R.id.tvCategoryIcon)
            val tvTitle = view.findViewById<TextView>(R.id.tvExpenseTitle)
            val tvPaidBy = view.findViewById<TextView>(R.id.tvExpensePaidBy)
            val tvAmount = view.findViewById<TextView>(R.id.tvExpenseAmount)

            val currentLabel = formatDateLabel(expense.date)
            val prevLabel = if (position > 0) formatDateLabel(items[position - 1].date) else ""

            if (position == 0 || currentLabel != prevLabel) {
                tvDateHeader.visibility = View.VISIBLE
                tvDateHeader.text = currentLabel
            } else {
                tvDateHeader.visibility = View.GONE
            }

            tvIcon.text = getCategoryEmoji(expense.category, expense.title)
            tvTitle.text = expense.title
            tvPaidBy.text = "Paid By ${expense.paidBy}"
            tvAmount.text = "${expense.amount.toInt()}EGP"

            return view
        }

        private fun formatDateLabel(rawDate: String): String {
            if (rawDate.isBlank()) return "Today"
            val todayStr = SimpleDateFormat("dd/M/yyyy", Locale.ENGLISH).format(Date())
            val todayPadded = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(Date())
            val yestCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val yestStr = SimpleDateFormat("dd/M/yyyy", Locale.ENGLISH).format(yestCal.time)
            val yestPadded = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(yestCal.time)

            return when (rawDate.trim()) {
                todayStr, todayPadded -> "Today"
                yestStr, yestPadded -> "Yesterday"
                else -> rawDate
            }
        }

        private fun getCategoryEmoji(category: String, title: String): String {
            val text = "$category $title".lowercase()
            return when {
                text.contains("rent") || text.contains("bill") || text.contains("water") || text.contains("home") -> "🏠"
                text.contains("shop") -> "🛍️"
                text.contains("transport") || text.contains("uber") || text.contains("car") -> "🚗"
                text.contains("food") || text.contains("drink") || text.contains("dinner") -> "🍽️"
                text.contains("grocer") || text.contains("market") -> "🛒"
                else -> "💳"
            }
        }
    }

    inner class MemberBalancesAdapter(private val items: List<MemberBalanceUi>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@GroupDetailsActivity)
                .inflate(R.layout.item_member_balance, parent, false)

            val member = items[position]
            val tvAvatar = view.findViewById<TextView>(R.id.tvMemberAvatar)
            val tvName = view.findViewById<TextView>(R.id.tvMemberName)
            val tvMeTag = view.findViewById<TextView>(R.id.tvMemberMeTag)
            val tvNet = view.findViewById<TextView>(R.id.tvMemberNetBalance)

            tvAvatar.text = member.name.firstOrNull()?.uppercase() ?: "M"
            tvName.text = member.name
            tvMeTag.visibility = if (member.isCurrentUser) View.VISIBLE else View.GONE

            val amountInt = member.netBalance.toInt()
            when {
                amountInt > 0 -> {
                    tvNet.text = "+${amountInt}EGP"
                    tvNet.setTextColor(Color.parseColor("#16A34A"))
                }
                amountInt < 0 -> {
                    tvNet.text = "${amountInt}EGP"
                    tvNet.setTextColor(Color.parseColor("#EA580C"))
                }
                else -> {
                    tvNet.text = "0EGP"
                    tvNet.setTextColor(Color.parseColor("#6B7280"))
                }
            }

            return view
        }
    }
}