package com.carvoice.contracts

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val requestId: String? = null,
    val code: String,
    val message: String,
    val retryable: Boolean = false,
    val details: Map<String, String> = emptyMap(),
)

@Serializable
data class ErrorDetail(
    val code: String,
    val message: String,
)
