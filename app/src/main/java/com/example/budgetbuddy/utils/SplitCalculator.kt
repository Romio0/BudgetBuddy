package com.example.budgetbuddy.utils

import com.example.budgetbuddy.data.ExpenseItem

object SplitCalculator {

    fun calculateGroupBalances(members: List<String>, expenses: List<ExpenseItem>): Map<String, Double> {
        val balances = members.associateWith { 0.0 }.toMutableMap()

        for (expense in expenses) {
            val splitMembers = expense.getSplitList()
            if (splitMembers.isEmpty()) continue

            val share = expense.amount / splitMembers.size
            balances[expense.paidBy] = (balances[expense.paidBy] ?: 0.0) + expense.amount

            for (member in splitMembers) {
                balances[member] = (balances[member] ?: 0.0) - share
            }
        }
        return balances
    }
}