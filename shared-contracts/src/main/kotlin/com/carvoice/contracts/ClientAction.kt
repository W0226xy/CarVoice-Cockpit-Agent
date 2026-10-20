package com.carvoice.contracts

/**
 * Domain boundary for future Android-side integrations. This stage never emits or executes it.
 */
sealed interface ClientAction {
    data class Navigate(val destination: String) : ClientAction

    data class PlayMedia(
        val song: String? = null,
        val artist: String? = null,
        val album: String? = null,
    ) : ClientAction
}
