package com.example.kitatrack.util

import com.example.kitatrack.R

object CategoryIconMapper {
    fun expenseIconFor(categoryName: String?): Int {
        val normalized = normalize(categoryName)

        return when {
            normalized.isBlank() -> R.drawable.ic_expense_other
            normalized.contains("food") || normalized.contains("drink") -> R.drawable.ic_expense_food_and_drinks
            normalized.contains("transport") || normalized.contains("gas") || normalized.contains("commute") -> R.drawable.ic_expense_transportation
            normalized.contains("shopping") -> R.drawable.ic_expense_shopping
            normalized.contains("health") || normalized.contains("medical") -> R.drawable.ic_expense_health
            normalized.contains("debt") || normalized.contains("loan") -> R.drawable.ic_expense_loan
            normalized.contains("donation") || normalized.contains("donate") -> R.drawable.ic_expense_donation
            normalized.contains("gaming") || normalized.contains("game") -> R.drawable.ic_expense_gaming
            normalized.contains("rent") || normalized.contains("housing") || normalized.contains("house") -> R.drawable.ic_expense_bill
            normalized.contains("education") || normalized.contains("school") -> R.drawable.ic_expense_school
            normalized.contains("internet") || normalized.contains("load") || normalized.contains("data") -> R.drawable.ic_expense_internet
            normalized.contains("personal") || normalized.contains("care") -> R.drawable.ic_expense_personal_care
            normalized.contains("saving") -> R.drawable.ic_expense_savings
            normalized.contains("family") -> R.drawable.ic_expense_family
            normalized.contains("emergency") -> R.drawable.ic_expense_emergency
            normalized.contains("transfer") || normalized.contains("cash") -> R.drawable.ic_expense_cash_transfer
            normalized.contains("bill") || normalized.contains("subscription") || normalized.contains("utility") -> R.drawable.ic_expense_bill
            normalized.contains("entertainment") -> R.drawable.ic_expense_gaming
            else -> R.drawable.ic_expense_other
        }
    }

    fun incomeIconFor(sourceName: String?): Int {
        val normalized = normalize(sourceName)

        return when {
            normalized.contains("salary") || normalized.contains("payroll") || normalized.contains("wage") -> R.drawable.ic_income_salary
            normalized.contains("allowance") || normalized.contains("baon") -> R.drawable.ic_income_allowance
            normalized.contains("freelance") || normalized.contains("business") || normalized.contains("client") || normalized.contains("commission") -> R.drawable.ic_income_freelance
            normalized.contains("cash on hand") || normalized == "cash" || normalized.contains("cash in hand") -> R.drawable.ic_income_cash_on_hand
            normalized.contains("bank") || normalized.contains("transfer") || normalized.contains("deposit") -> R.drawable.ic_income_bank_transfer
            normalized.contains("gift") || normalized.contains("bonus") || normalized.contains("reward") -> R.drawable.ic_income_gift
            normalized.contains("refund") || normalized.contains("reimbursement") || normalized.contains("rebate") -> R.drawable.ic_income_refund
            normalized.contains("investment") || normalized.contains("dividend") || normalized.contains("interest") -> R.drawable.ic_income_bank_transfer
            else -> R.drawable.ic_income_cash_on_hand
        }
    }

    private fun normalize(name: String?): String =
        name
            ?.lowercase()
            ?.replace("&", "and")
            ?.replace("/", " ")
            ?.replace("-", " ")
            ?.replace("_", " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
}
