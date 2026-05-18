package com.example.kitatrack.ui.history

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.navigation.fragment.findNavController
import com.example.kitatrack.KitaTrackApplication
import com.example.kitatrack.R
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class HistoryFragment : Fragment(R.layout.fragment_history) {
    private val app by lazy { requireActivity().application as KitaTrackApplication }
    private val viewModel by viewModels<HistoryViewModel> { HistoryViewModel.Factory(app.transactionRepository) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = GroupedHistoryAdapter(
            onClick = { row ->
                findNavController().navigate(
                    R.id.action_historyFragment_to_addTransactionFragment,
                    android.os.Bundle().apply { putLong("transactionId", row.item.transaction.id) }
                )
            },
            onLongClick = { row ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete transaction?")
                    .setMessage("This will remove the transaction from your local history.")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.delete(row.item.transaction)
                        Snackbar.make(view, "Transaction deleted.", Snackbar.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        view.findViewById<RecyclerView>(R.id.history_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        var latestItems = emptyList<com.example.kitatrack.data.local.model.TransactionWithCategory>()
        val filterGroup = view.findViewById<ChipGroup>(R.id.history_filters)
        fun renderFiltered() {
            val now = java.util.Calendar.getInstance()
            val startOfWeek = (now.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.DAY_OF_WEEK, firstDayOfWeek)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val month = com.example.kitatrack.util.DateRanges.currentMonth()
            val filtered = latestItems.filter {
                when (filterGroup.checkedChipId) {
                    R.id.filter_income -> it.transaction.type == "INCOME"
                    R.id.filter_expense -> it.transaction.type == "EXPENSE"
                    R.id.filter_this_week -> it.transaction.occurredAt >= startOfWeek
                    R.id.filter_this_month -> it.transaction.occurredAt in month
                    else -> true
                }
            }
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
}
