package com.omniclaw.presentation.screens.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniclaw.ui.theme.OmniClawAccent
import com.omniclaw.ui.theme.OmniClawSuccess
import com.omniclaw.ui.theme.OmniClawTextSecondary

@Composable
fun DiffChip(
    diffLine: String,
    modifier: Modifier = Modifier
) {
    val parts = diffLine.split("|")
    val filePath = parts[0].trim()
    val stats = if (parts.size > 1) parts[1].trim() else ""

    val insertions = Regex("(\\d+) \\+").find(stats)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val deletions = Regex("(\\d+) -").find(stats)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OmniClawTextSecondary.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = filePath,
            style = MaterialTheme.typography.bodySmall,
            color = OmniClawAccent,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (insertions > 0 || deletions == 0) {
                Text(
                    text = "+$insertions",
                    color = OmniClawSuccess,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (deletions > 0) {
                Text(
                    text = "-$deletions",
                    color = com.omniclaw.ui.theme.OmniClawError,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
