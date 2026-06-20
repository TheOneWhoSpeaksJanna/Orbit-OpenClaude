package com.omniclaw.presentation.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.omniclaw.ui.theme.OmniClawAccent

@Composable
fun QuickActionsBar(
    onNewSession: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = onNewSession,
            label = { Text("New Session") },
            leadingIcon = {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = OmniClawAccent
                )
            }
        )
        AssistChip(
            onClick = onOpenTerminal,
            label = { Text("Terminal") },
            leadingIcon = {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = OmniClawAccent
                )
            }
        )
        AssistChip(
            onClick = onOpenSettings,
            label = { Text("Settings") },
            leadingIcon = {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = OmniClawAccent
                )
            }
        )
    }
}
