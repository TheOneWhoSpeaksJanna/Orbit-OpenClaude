package com.omniclaw.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniclaw.ui.theme.OrbitSuccess
import com.omniclaw.ui.theme.OrbitWarning


private const val CHIP_SHAPE_RADIUS_DP = 6
private const val CHIP_HORIZONTAL_PADDING_DP = 10
private const val CHIP_VERTICAL_PADDING_DP = 6
private const val CHIP_SPACING_DP = 6
private const val CHIP_BORDER_DP = 1
private const val CHIP_FONT_SIZE_SP = 11
private const val BORDER_ALPHA = 0.2f

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
            .border(CHIP_BORDER_DP.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = BORDER_ALPHA), RoundedCornerShape(CHIP_SHAPE_RADIUS_DP.dp))
            .padding(horizontal = CHIP_HORIZONTAL_PADDING_DP.dp, vertical = CHIP_VERTICAL_PADDING_DP.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = filePath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CHIP_SPACING_DP.dp)) {
            if (insertions > 0 || deletions == 0) {
                Text(
                    text = "+$insertions",
                    color = OrbitSuccess,
                    fontSize = CHIP_FONT_SIZE_SP.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (deletions > 0) {
                Text(
                    text = "-$deletions",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = CHIP_FONT_SIZE_SP.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
