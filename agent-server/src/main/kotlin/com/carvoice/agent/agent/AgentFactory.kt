package com.carvoice.agent.agent

import com.carvoice.agent.config.LlmConfig
import com.carvoice.agent.config.LlmProviderMode

fun createCarVoiceAgent(config: LlmConfig): CarVoiceAgent =
    when (config.providerMode) {
        LlmProviderMode.MOCK -> MockStreamingAgent()
        LlmProviderMode.OPENAI_COMPATIBLE -> KoogAgentAdapter(config)
    }
