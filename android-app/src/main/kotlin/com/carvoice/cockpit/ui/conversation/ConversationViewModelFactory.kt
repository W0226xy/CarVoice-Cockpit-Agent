package com.carvoice.cockpit.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.carvoice.cockpit.data.ConversationRepository

class ConversationViewModelFactory(
    private val repository: ConversationRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ConversationViewModel::class.java))
        return ConversationViewModel(repository) as T
    }
}
