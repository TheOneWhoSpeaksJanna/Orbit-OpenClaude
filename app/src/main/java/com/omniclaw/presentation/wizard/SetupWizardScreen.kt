package com.omniclaw.presentation.wizard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.omniclaw.presentation.components.AnimatedGlassCard
import com.omniclaw.ui.theme.OmniClawThemeMode

@Composable
fun ThemeSelectionStep(
    currentSelectedMode: OmniClawThemeMode,
    onThemeSelected: (OmniClawThemeMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Choose Visual Interface",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select your preferred layout tint core behavior",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        ThemeOptionRow(
            title = "Light Appearance",
            subtitle = "Clean high-contrast workspace theme",
            isSelected = currentSelectedMode == OmniClawThemeMode.LIGHT,
            onClick = { onThemeSelected(OmniClawThemeMode.LIGHT) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        ThemeOptionRow(
            title = "Obsidian Dark",
            subtitle = "Sleek low-light computing atmosphere",
            isSelected = currentSelectedMode == OmniClawThemeMode.DARK,
            onClick = { onThemeSelected(OmniClawThemeMode.DARK) }
        )
    }
}

@Composable
fun ThemeOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val targetBorderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val animatedBorderColor by animateColorAsState(targetValue = targetBorderColor, label = "BorderGlow")

    AnimatedGlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
