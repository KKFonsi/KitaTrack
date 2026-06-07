package com.example.kitatrack.ui.history

import android.os.Bundle
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.navigation.fragment.findNavController
import com.example.kitatrack.KitaTrackApplication
import com.example.kitatrack.R
import com.example.kitatrack.data.local.model.TransactionWithCategory
import com.example.kitatrack.util.Formatters
import com.example.kitatrack.util.ThemePreferences
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

class HistoryFragment : Fragment(R.layout.fragment_history) {
    private val app by lazy { requireActivity().application as KitaTrackApplication }
    private val viewModel by viewModels<HistoryViewModel> { HistoryViewModel.Factory(app.transactionRepository) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindThemeToggle(view)
        val adapter = GroupedHistoryAdapter(
            onClick = { row -> showTransactionSheet(view, row) },
            onLongClick = { row -> confirmDelete(view, row, null) }
        )
        view.findViewById<RecyclerView>(R.id.history_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        var latestItems = emptyList<TransactionWithCategory>()
        val filterGroup = view.findViewById<ChipGroup>(R.id.history_filters)
        fun renderFiltered() {
            val week = com.example.kitatrack.util.DateRanges.currentWeek()
            val month = com.example.kitatrack.util.DateRanges.currentMonth()
            val filtered = latestItems.filter {
                when (filterGroup.checkedChipId) {
                    R.id.filter_income -> it.transaction.type == "INCOME"
                    R.id.filter_expense -> it.transaction.type == "EXPENSE"
                    R.id.filter_this_week -> it.transaction.occurredAt in week
                    R.id.filter_this_month -> it.transaction.occurredAt in month
                    else -> true
                }
            }
            renderSummary(view, filtered)
            view.findViewById<TextView>(R.id.history_empty_state).visibility =
                if (filtered.isEmpty()) View.VISIBLE else View.GONE
            adapter.submitRows(viewModel.withRunningBalances(filtered))
        }
        filterGroup.setOnCheckedStateChangeListener { _, _ -> renderFiltered() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.transactions.collect { items ->
                    latestItems = items
                    renderFiltered()
                }
            }
        }
    }

    private fun bindThemeToggle(view: View) {
        view.findViewById<MaterialButton>(R.id.history_theme_toggle_button).apply {
            updateThemeToggleIcon(this)
            setOnClickListener {
                ThemePreferences.setDarkMode(requireContext(), !ThemePreferences.isDarkModeActive(requireContext()))
            }
        }
    }

    private fun updateThemeToggleIcon(button: MaterialButton) {
        val isDark = ThemePreferences.isDarkModeActive(requireContext())
        button.setIconResource(if (isDark) R.drawable.ic_theme_sun else R.drawable.ic_theme_moon)
        button.contentDescription = if (isDark) "Switch to day mode" else "Switch to dark mode"
    }

    private fun renderSummary(view: View, items: List<TransactionWithCategory>) {
        val income = items.filter { it.transaction.type == "INCOME" }.sumOf { it.transaction.amount }
        val expense = items.filter { it.transaction.type == "EXPENSE" }.sumOf { it.transaction.amount }
        val net = income - expense
        view.findViewById<TextView>(R.id.history_income_value).text = "+${Formatters.peso(income)}"
        view.findViewById<TextView>(R.id.history_expense_value).text = "-${Formatters.peso(expense)}"
        view.findViewById<TextView>(R.id.history_net_value).apply {
            text = "${if (net >= 0) "+" else "-"}${Formatters.peso(kotlin.math.abs(net))}"
            setTextColor(ContextCompat.getColor(requireContext(), if (net >= 0) R.color.kitatrack_primary_green else R.color.kitatrack_expense_red))
        }
    }

    private fun showTransactionSheet(parentView: View, row: HistoryRow) {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = layoutInflater.inflate(R.layout.bottom_sheet_transaction_detail, null)
        val tx = row.item.transaction
        val isIncome = tx.type == "INCOME"
        val accent = ContextCompat.getColor(requireContext(), if (isIncome) R.color.kitatrack_primary_green else R.color.kitatrack_expense_red)
        val badgeBg = ContextCompat.getColor(requireContext(), if (isIncome) R.color.kitatrack_chip_green_background else R.color.kitatrack_chip_red_background)

        sheet.findViewById<TextView>(R.id.detail_amount).apply {
            text = "${if (isIncome) "+" else "-"}${Formatters.peso(tx.amount)}"
            setTextColor(accent)
        }
        sheet.findViewById<TextView>(R.id.detail_type_badge).apply {
            text = if (isIncome) "Income" else "Expense"
            setTextColor(accent)
            backgroundTintList = ColorStateList.valueOf(badgeBg)
        }
        bindDetailRow(sheet.findViewById(R.id.detail_category_row), if (isIncome) "Source" else "Category", row.item.categoryName ?: "Uncategorized")
        bindDetailRow(sheet.findViewById(R.id.detail_date_row), "Date", detailDate(tx.occurredAt))
        bindDetailRow(sheet.findViewById(R.id.detail_account_row), "Account", "Main Balance")
        bindDetailRow(sheet.findViewById(R.id.detail_note_row), "Note", tx.note?.takeIf { it.isNotBlank() } ?: tx.description.ifBlank { "No note" })
        bindDetailRow(sheet.findViewById(R.id.detail_balance_row), "Balance After", Formatters.peso(row.balanceAfter))
        sheet.findViewById<MaterialButton>(R.id.detail_edit_button).setOnClickListener {
            dialog.dismiss()
            findNavController().navigate(
                R.id.action_historyFragment_to_addTransactionFragment,
                Bundle().apply {
                    putLong("transactionId", tx.id)
                    putString("initialType", tx.type)
                }
            )
        }
        sheet.findViewById<MaterialButton>(R.id.detail_delete_button).setOnClickListener {
            confirmDelete(parentView, row, dialog)
        }

        dialog.setContentView(sheet)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundResource(R.drawable.kt_bottom_sheet_background)
        }
        dialog.show()
    }

    private fun bindDetailRow(row: View, label: String, value: String) {
        row.findViewById<TextView>(R.id.detail_row_label).text = label
        row.findViewById<TextView>(R.id.detail_row_value).text = value
    }

    private fun confirmDelete(parentView: View, row: HistoryRow, sheetDialog: BottomSheetDialog?) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete transaction?")
            .setMessage("This will remove the transaction from your local history.")
            .setPositiveButton("Delete") { _, _ ->
                sheetDialog?.dismiss()
                viewModel.delete(row.item.transaction)
                Snackbar.make(parentView, "Transaction deleted.", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun detailDate(millis: Long): String =
        SimpleDateFormat("MMM d, yyyy - h:mm a", Locale.US).format(java.util.Date(millis))
}
