package io.music_assistant.client.ui.compose.home.players

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Active UIKit reserved regions, in the Compose host's logical (point/dp) coordinates. */
data class PlayerReservedRegion(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    val division: Boolean,
)

/** Owned by one Compose host, not by a player or a session. Older platforms leave it empty. */
class PlayerWindowGeometry {
    var regions: List<PlayerReservedRegion> by mutableStateOf(emptyList())
        private set

    fun update(regions: List<PlayerReservedRegion>) {
        this.regions = regions
    }
}

internal val LocalPlayerWindowGeometry = staticCompositionLocalOf { PlayerWindowGeometry() }
