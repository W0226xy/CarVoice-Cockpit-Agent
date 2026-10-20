package com.carvoice.agent.agent

import com.carvoice.agent.config.LlmConfig
fun createCarVoiceAgent(config: LlmConfig): CarVoiceAgent =
    KoogAgentAdapter(config)
