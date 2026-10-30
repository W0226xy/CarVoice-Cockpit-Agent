package com.carvoice.cockpit.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.carvoice.cockpit.data.ConversationRepository
import com.carvoice.cockpit.data.ConversationHistoryRepository
import com.carvoice.cockpit.ui.speech.NoOpSpeechRecognitionLifecycle
import com.carvoice.cockpit.ui.speech.SpeechRecognitionLifecycle
import com.carvoice.cockpit.navigation.NavigationExecutor

class ConversationViewModelFactory(
    private val repository: ConversationRepository,
    private val historyRepository: ConversationHistoryRepository,
    private val speechLifecycle: SpeechRecognitionLifecycle = NoOpSpeechRecognitionLifecycle,
    private val navigationExecutor: NavigationExecutor,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ConversationViewModel::class.java))
        return ConversationViewModel(repository, historyRepository = historyRepository, speechLifecycle = speechLifecycle, navigationExecutor = navigationExecutor) as T
    }
}
