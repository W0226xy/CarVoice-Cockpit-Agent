package com.carvoice.cockpit.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.carvoice.contracts.NavigationExecutionStatus

fun interface NavigationExecutor {
    fun openMapSearch(destination: String): NavigationExecution
}

data class NavigationExecution(
    val status: NavigationExecutionStatus,
    val errorMessage: String? = null,
)

/**
 * Uses Android's generic geo URI contract. A successful result means only that
 * an Activity accepted the location-search intent; map route planning is outside
 * the observable scope of this app.
 */
class AndroidNavigationExecutor(
    private val context: Context,
) : NavigationExecutor {
    override fun openMapSearch(destination: String): NavigationExecution {
        val normalizedDestination = destination.trim()
        if (normalizedDestination.isEmpty()) {
            return NavigationExecution(NavigationExecutionStatus.LAUNCH_FAILED, "目的地为空")
        }
        // Uri.Builder encodes Chinese characters and reserved query characters in q.
        val geoUri = Uri.Builder()
            .scheme("geo")
            .opaquePart("0,0")
            .appendQueryParameter("q", normalizedDestination)
            .build()
        val intent = Intent(Intent.ACTION_VIEW, geoUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            return NavigationExecution(NavigationExecutionStatus.NO_MAP_APP)
        }
        return try {
            context.startActivity(intent)
            NavigationExecution(NavigationExecutionStatus.MAP_APP_LAUNCHED)
        } catch (_: ActivityNotFoundException) {
            NavigationExecution(NavigationExecutionStatus.NO_MAP_APP)
        } catch (_: SecurityException) {
            NavigationExecution(NavigationExecutionStatus.LAUNCH_FAILED, "地图应用拒绝了请求")
        } catch (_: RuntimeException) {
            NavigationExecution(NavigationExecutionStatus.LAUNCH_FAILED, "地图应用启动失败")
        }
    }
}
