package com.carvoice.agent.knowledge

import com.carvoice.agent.agent.CarVoiceAgent
import kotlinx.coroutines.flow.Flow

class AgentKnowledgeAnswerService(private val agent: CarVoiceAgent) : KnowledgeAnswerService {
    override fun answer(question: String, passages: List<KnowledgePassage>): Flow<String> =
        agent.streamKnowledgeAnswer(question, passages)
}
