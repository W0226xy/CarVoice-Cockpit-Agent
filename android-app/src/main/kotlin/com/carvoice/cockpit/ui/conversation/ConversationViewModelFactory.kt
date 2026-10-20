package com.carvoice.cockpit.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.carvoice.cockpit.data.ConversationRepository
import com.carvoice.cockpit.data.ConversationHistoryRepository

class ConversationViewModelFactory(
    private val repository: ConversationRepository,
    private val historyRepository: ConversationHistoryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ConversationViewModel::class.java))
        return ConversationViewModel(repository, historyRepository = historyRepository) as T
    }
}
