package com.omniclaw.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniclaw.domain.models.Agent

private const val GRID_SPACING_DP = 12
private const val CARD_PADDING_DP = 12
private const val SPACER_HEIGHT_DP = 4
private const val BORDER_WIDTH_DP = 1
private const val INITIALS_LENGTH = 2
private const val ACTIVE_BACKGROUND_ALPHA = 0.15f
private const val ACTIVE_BORDER_ALPHA = 0.5f

@Composable
fun AgentCardsGrid(
    agents: List<Agent>,
    activeAgentName: String?,
    onAgentSelected: (Agent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_SPACING_DP.dp)
    ) {
        agents.take(3).forEach { agent ->
            val isActive = agent.name == activeAgentName
            val accent = MaterialTheme.colorScheme.secondary
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onAgentSelected(agent) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) accent.copy(alpha = ACTIVE_BACKGROUND_ALPHA) else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (isActive) BorderStroke(
                    BORDER_WIDTH_DP.dp, accent.copy(alpha = ACTIVE_BORDER_ALPHA)
                ) else null
            ) {
                Column(
                    modifier = Modifier.padding(CARD_PADDING_DP.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = agent.name.take(INITIALS_LENGTH).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(SPACER_HEIGHT_DP.dp))
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
