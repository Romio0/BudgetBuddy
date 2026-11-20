package com.example.budgetbuddy.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
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
import java.util.Locale

class AllExpensesActivity : AppCompatActivity() {

    private lateinit var repository: ExpenseRepository
    private lateinit var prefs: PrefsManager
    private lateinit var lvAllActivity: ListView
    private lateinit var tvEmptyActivity: TextView

    data class ActivityRowModel(
        val expense: ExpenseItem,
        val groupName: String,
        val groupCode: String,
        val dateLabel: String,
        val showHeader: Boolean
    )

    private var rowsList: List<ActivityRowModel> = emptyList()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_all_expenses)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        prefs = PrefsManager(this)
        repository = ExpenseRepository(AppDatabase.getInstance(this))

        lvAllActivity = findViewById(R.id.lvAllActivity)
        tvEmptyActivity = findViewById(R.id.tvEmptyActivity)

        val btnBack = findViewById<TextView>(R.id.btnBackActivity)
        val navHome = findViewById<LinearLayout>(R.id.navHomeFromActivity)
        val navAddExpense = findViewById<TextView>(R.id.navAddExpenseFromActivity)

        btnBack.setOnClickListener {
            finish()
        }

        navHome.setOnClickListener {
            finish()
        }

        navAddExpense.setOnClickListener {
            startActivity(Intent(this, AddEditExpenseActivity::class.java))
        }

        lvAllActivity.setOnItemClickListener { _, _, position, _ ->
            val item = rowsList[position]
            val intent = Intent(this, GroupDetailsActivity::class.java).apply {
                putExtra("GROUP_ID", item.expense.groupId)
                putExtra("GROUP_CODE", item.groupCode)
                putExtra("GROUP_NAME", item.groupName)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadActivityFeed()
    }

    private fun loadActivityFeed() {
        lifecycleScope.launch {
            val groups = repository.getAllGroups()
            val groupMap = groups.associateBy { it.id }

            // ترتيب المصاريف من الأحدث للأقدم
            val expenses = repository.getAllExpenses().reversed()

            if (expenses.isEmpty()) {
                tvEmptyActivity.visibility = View.VISIBLE
                lvAllActivity.visibility = View.GONE
                return@launch
            }

            tvEmptyActivity.visibility = View.GONE
            lvAllActivity.visibility = View.VISIBLE

            val builtRows = mutableListOf<ActivityRowModel>()
            var lastDateLabel = ""

            for (expense in expenses) {
                val group = groupMap[expense.groupId]
                val groupName = group?.groupName ?: "Shared Group"
                val groupCode = group?.groupCode ?: ""
                val friendlyDate = formatFriendlyDate(expense.date)

                val shouldShowHeader = friendlyDate != lastDateLabel
                lastDateLabel = friendlyDate

                builtRows.add(
                    ActivityRowModel(
                        expense = expense,
                        groupName = groupName,
                        groupCode = groupCode,
                        dateLabel = friendlyDate,
                        showHeader = shouldShowHeader
                    )
                )
            }

            rowsList = builtRows
            lvAllActivity.adapter = ActivityAdapter(builtRows)
        }
    }

    private fun formatFriendlyDate(rawDate: String): String {
        val clean = rawDate.trim()
        if (clean.isEmpty()) return "Today"

        val sdfShort = SimpleDateFormat("d/M/yyyy", Locale.US)
        val sdfPadded = SimpleDateFormat("dd/MM/yyyy", Locale.US)

        val calToday = Calendar.getInstance()
        val todayShort = sdfShort.format(calToday.time)
        val todayPadded = sdfPadded.format(calToday.time)

        val calYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayShort = sdfShort.format(calYesterday.time)
        val yesterdayPadded = sdfPadded.format(calYesterday.time)

        return when (clean) {
            todayShort, todayPadded -> "Today"
            yesterdayShort, yesterdayPadded -> "Yesterday"
            else -> clean
        }
    }

    inner class ActivityAdapter(private val items: List<ActivityRowModel>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@AllExpensesActivity)
                .inflate(R.layout.item_activity_expense, parent, false)

            val row = items[position]
            val currentUser = prefs.getUserName().trim()

            val tvDateHeader = view.findViewById<TextView>(R.id.tvDateHeader)
            val tvBadge = view.findViewById<TextView>(R.id.tvActivityGroupBadge)
            val tvGroupName = view.findViewById<TextView>(R.id.tvActivityGroupName)
            val tvDesc = view.findViewById<TextView>(R.id.tvActivityDescription)
            val tvAmount = view.findViewById<TextView>(R.id.tvActivityAmount)

            if (row.showHeader) {
                tvDateHeader.visibility = View.VISIBLE
                tvDateHeader.text = row.dateLabel
            } else {
                tvDateHeader.visibility = View.GONE
            }

            val words = row.groupName.trim().split(" ").filter { it.isNotEmpty() }
            tvBadge.text = when {
                words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
                words.size == 1 -> words[0].take(2).uppercase()
                else -> "GR"
            }

            tvGroupName.text = row.groupName

            val payerLabel = if (row.expense.paidBy.equals(currentUser, ignoreCase = true)) {
                "You"
            } else {
                row.expense.paidBy
            }
            tvDesc.text = "$payerLabel Paid For ${row.expense.title}"

            val amountFormatted = if (row.expense.amount % 1.0 == 0.0) {
                row.expense.amount.toInt().toString()
            } else {
                String.format(Locale.US, "%.2f", row.expense.amount)
            }
            tvAmount.text = "$amountFormatted EGP"

            return view
        }
    }
}