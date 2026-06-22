package com.omniclaw.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omniclaw.ui.viewmodels.ConnectionState
import com.omniclaw.ui.viewmodels.ProviderConfig
import com.omniclaw.ui.viewmodels.ProvidersViewModel
import com.omniclaw.ui.theme.OmniClawAccent
import com.omniclaw.ui.theme.OmniClawAccentSecondary
import com.omniclaw.ui.theme.OmniClawGlassBorder
import com.omniclaw.ui.theme.OmniClawGlassOverlay
import com.omniclaw.ui.theme.OmniClawObsidianBase
import com.omniclaw.ui.theme.OmniClawObsidianSurface
import com.omniclaw.ui.theme.OmniClawSuccess
import com.omniclaw.ui.theme.OmniClawTextPrimary
import com.omniclaw.ui.theme.OmniClawTextSecondary
import com.omniclaw.ui.theme.OmniClawTextTertiary
import com.omniclaw.ui.theme.OmniClawWarning

@Composable
fun ProvidersScreen(
    viewModel: ProvidersViewModel = viewModel(factory = ProvidersViewModel.Factory)
) {
    val providers by viewModel.providers.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OmniClawObsidianBase)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "API Providers",
                style = MaterialTheme.typography.headlineMedium,
                color = OmniClawTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Verify connectivity and manage endpoint configurations",
                style = MaterialTheme.typography.bodyMedium,
                color = OmniClawTextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(providers, key = { it.name }) { provider ->
            ProviderHealthCard(
                provider = provider,
                onVerify = { viewModel.verifyConnection(provider.name) }
            )
        }
    }
}

@Composable
private fun ProviderHealthCard(
    provider: ProviderConfig,
    onVerify: () -> Unit
) {
    val shape = remember { RoundedCornerShape(14.dp) }

    val statusColor by animateColorAsState(
        targetValue = provider.connectionState.statusColor(),
        animationSpec = spring(dampingRatio = 0.8f),
        label = "statusColor"
    )

    val statusText = provider.connectionState.statusText()
    val isVerifying = provider.connectionState is ConnectionState.Verifying

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(OmniClawGlassOverlay)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(providerAccent(provider.name).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = providerIcon(provider.name),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = providerAccent(provider.name)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = OmniClawTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = OmniClawTextSecondary
                    )
                }
                if (!provider.apiKeyConfigured) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "No API key configured",
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniClawWarning
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onVerify,
                enabled = !isVerifying,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OmniClawObsidianSurface,
                    contentColor = OmniClawAccent
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = ButtonDefaults.TextButtonContentPadding
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = OmniClawAccent
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (isVerifying) "Testing" else "Verify",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

private fun providerAccent(name: String): Color = when (name) {
    "Claude" -> Color(0xFFCC7832)
    "OpenAI" -> Color(0xFF10A37F)
    "Gemini" -> Color(0xFF4285F4)
    "OpenRouter" -> Color(0xFFFF6B35)
    "DeepSeek" -> Color(0xFF4F6CF7)
    "Groq" -> Color(0xFFF97316)
    "Ollama" -> Color(0xFF8B5CF6)
    else -> OmniClawAccent
}

private fun providerIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector = when (name) {
    "Claude" -> Icons.Default.Send
    "OpenAI" -> Icons.Default.Lightbulb
    "Gemini" -> Icons.Default.Star
    "OpenRouter" -> Icons.Default.Share
    "DeepSeek" -> Icons.Default.Search
    "Groq" -> Icons.Default.FlashOn
    "Ollama" -> Icons.Default.Computer
    else -> Icons.Default.Cloud
}

private fun ConnectionState.statusColor(): Color = when (this) {
    is ConnectionState.Idle -> OmniClawTextTertiary
    is ConnectionState.Verifying -> OmniClawAccent
    is ConnectionState.Connected -> OmniClawSuccess
    is ConnectionState.Unauthorized -> OmniClawWarning
    is ConnectionState.Offline -> Color(0xFFEF4444)
    is ConnectionState.Error -> Color(0xFFEF4444)
}

private fun ConnectionState.statusText(): String = when (this) {
    is ConnectionState.Idle -> "Not verified"
    is ConnectionState.Verifying -> "Verifying connection..."
    is ConnectionState.Connected -> "Connected"
    is ConnectionState.Unauthorized -> this.message
    is ConnectionState.Offline -> "Offline / No connection"
    is ConnectionState.Error -> this.message
}
