package com.omniclaw.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val BAR_SPACING_DP = 8
private const val ICON_SIZE_DP = 18
private const val LABEL_NEW_SESSION = "New Session"
private const val LABEL_TERMINAL = "Terminal"
private const val LABEL_SETTINGS = "Settings"

@Composable
fun QuickActionsBar(
    onNewSession: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.secondary
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BAR_SPACING_DP.dp)
    ) {
        AssistChip(
            onClick = onNewSession,
            label = { Text(LABEL_NEW_SESSION) },
            leadingIcon = {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SIZE_DP.dp),
                    tint = accent
                )
            }
        )
        AssistChip(
            onClick = onOpenTerminal,
            label = { Text(LABEL_TERMINAL) },
            leadingIcon = {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SIZE_DP.dp),
                    tint = accent
                )
            }
        )
        AssistChip(
            onClick = onOpenSettings,
            label = { Text(LABEL_SETTINGS) },
            leadingIcon = {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SIZE_DP.dp),
                    tint = accent
                )
            }
        )
    }
}
