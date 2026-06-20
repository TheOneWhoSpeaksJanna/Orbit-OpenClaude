package com.omniclaw.presentation.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniclaw.domain.model.TermuxLog
import com.omniclaw.ui.theme.OmniClawAccent
import com.omniclaw.ui.theme.OmniClawError
import com.omniclaw.ui.theme.OmniClawSuccess
import com.omniclaw.ui.theme.OmniClawSurfaceDark
import com.omniclaw.ui.theme.OmniClawTextPrimary
import com.omniclaw.ui.theme.OmniClawTextSecondary

@Composable
fun TerminalBlock(
    log: TermuxLog,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isError = log.exitCode != 0
    val preview = log.output.take(80).replace("\n", " ")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OmniClawSurfaceDark)
            .clickable { expanded = !expanded }
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isError) OmniClawError else OmniClawSuccess,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = "${log.exitCode}",
                color = if (isError) OmniClawError else OmniClawSuccess,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "\$ ${log.command}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = OmniClawAccent,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = preview.ifEmpty { if (isError) "Command failed" else "No output" },
            style = MaterialTheme.typography.bodySmall,
            color = OmniClawTextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 2
        )
        AnimatedVisibility(visible = expanded) {
            Text(
                text = log.output.ifEmpty { "(empty)" },
                style = MaterialTheme.typography.bodySmall,
                color = OmniClawTextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
