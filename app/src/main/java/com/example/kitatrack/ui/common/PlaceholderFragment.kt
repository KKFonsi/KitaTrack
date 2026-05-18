package com.example.kitatrack.ui.common

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.kitatrack.R
import com.google.android.material.button.MaterialButton

abstract class PlaceholderFragment(
    private val screenTitle: String,
    private val screenDescription: String,
    private val emptyTitle: String,
    private val emptyMessage: String,
    private val futureTitle: String,
    private val futureMessage: String,
    private val actionLabel: String? = null,
    @param:IdRes private val actionDestination: Int? = null
) : Fragment(R.layout.fragment_placeholder) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.screen_title).text = screenTitle
        view.findViewById<TextView>(R.id.screen_description).text = screenDescription
        view.findViewById<TextView>(R.id.empty_state_title).text = emptyTitle
        view.findViewById<TextView>(R.id.empty_state_message).text = emptyMessage
        view.findViewById<TextView>(R.id.future_structure_title).text = futureTitle
        view.findViewById<TextView>(R.id.future_structure_message).text = futureMessage

        val button = view.findViewById<MaterialButton>(R.id.primary_action)
        if (actionLabel != null && actionDestination != null) {
            button.visibility = View.VISIBLE
            button.text = actionLabel
            button.setOnClickListener { findNavController().navigate(actionDestination) }
        }
    }
}
