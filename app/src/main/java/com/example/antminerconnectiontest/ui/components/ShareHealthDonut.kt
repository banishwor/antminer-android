package com.example.antminerconnectiontest.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.domain.model.MinerPool
import com.example.antminerconnectiontest.theme.*
import java.util.Locale

@Composable
fun ShareHealthDonut(
    pools: List<MinerPool>,
    modifier: Modifier = Modifier
) {
    val totalAccepted = pools.sumOf { it.acceptedShares }
    val totalRejected = pools.sumOf { it.rejectedShares }
    val totalStale = pools.sumOf { it.staleShares }
    val totalShares = totalAccepted + totalRejected + totalStale

    val acceptanceRate = if (totalShares > 0) {
        (totalAccepted.toDouble() / totalShares.toDouble()) * 100.0
    } else {
        100.0
    }

    Card(
        shape = panelShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Arc colours: Grouped Sheets (v0.4.0) follows the
            // mockup donut — blue accepted, #55555C rejected, pale
            // grey track.
            val acceptedColor = accentOk()
            val rejectedColor = GroupedSheetsSpec.DonutRejected
            val trackColor = GroupedSheetsSpec.DonutStale

            // Left: Donut Chart with Center Rate
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(100.dp)) {
                    val strokeWidth = 10.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                    val arcSize = Size(diameter, diameter)

                    // Background Track
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )

                    if (totalShares > 0) {
                        val acceptedSweep = (acceptanceRate / 100f * 360f).toFloat()
                        // Accepted Arc (Emerald)
                        drawArc(
                            color = acceptedColor,
                            startAngle = -90f,
                            sweepAngle = acceptedSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Rejected Arc (Ruby) if any
                        if (totalRejected > 0) {
                            val rejectedSweep = (totalRejected.toFloat() / totalShares.toFloat() * 360f)
                            drawArc(
                                color = rejectedColor,
                                startAngle = -90f + acceptedSweep,
                                sweepAngle = rejectedSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    } else {
                        // Default full green track
                        drawArc(
                            color = acceptedColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Center Label
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format(Locale.US, "%.1f%%", acceptanceRate),
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "EFFICIENCY",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = acceptedColor,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right: Stratum Share Breakdown Stats
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Stratum Share Quality",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                ShareStatRow(label = "Accepted Shares", value = "$totalAccepted", color = acceptedColor)
                ShareStatRow(label = "Rejected Shares", value = "$totalRejected", color = if (totalRejected > 0) rejectedColor else MaterialTheme.colorScheme.onSurfaceVariant)
                ShareStatRow(label = "Total Submitted", value = "$totalShares", color = accentData())
            }
        }
    }
}

@Composable
private fun ShareStatRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = color
        )
    }
}
