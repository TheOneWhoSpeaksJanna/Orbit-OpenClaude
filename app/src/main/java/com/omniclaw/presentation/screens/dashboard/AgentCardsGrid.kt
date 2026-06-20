package com.omniclaw.presentation.screens.dashboard

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
import com.omniclaw.domain.model.Agent
import com.omniclaw.ui.theme.OmniClawAccent
import com.omniclaw.ui.theme.OmniClawSurfaceElevated
import com.omniclaw.ui.theme.OmniClawTextSecondary

@Composable
fun AgentCardsGrid(
    agents: List<Agent>,
    activeAgentName: String?,
    onAgentSelected: (Agent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        agents.take(3).forEach { agent ->
            val isActive = agent.name == activeAgentName
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onAgentSelected(agent) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) OmniClawAccent.copy(alpha = 0.15f) else OmniClawSurfaceElevated
                ),
                border = if (isActive) androidx.compose.foundation.BorderStroke(
                    1.dp, OmniClawAccent.copy(alpha = 0.5f)
                ) else null
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = agent.name.take(2).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = OmniClawAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) OmniClawAccent else OmniClawTextSecondary,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
