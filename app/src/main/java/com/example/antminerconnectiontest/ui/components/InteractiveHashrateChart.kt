package com.example.antminerconnectiontest.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.domain.model.ChartTimePoint
import com.example.antminerconnectiontest.theme.*
import java.util.Locale

enum class ChartViewMode {
    TOTAL,
    CHAINS
}

@Composable
fun InteractiveHashrateChart(
    points: List<ChartTimePoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Awaiting chart telemetry...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var viewMode by remember { mutableStateOf(ChartViewMode.TOTAL) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val chainColors = listOf(
        AntminerEmerald, // Chain 1
        CyberCyan,       // Chain 2
        ElectricBlue,    // Chain 3
        WarningAmber     // Chain 4 if any
    )

    // Calculate Y-scale bounds
    val allValues = remember(points, viewMode) {
        if (viewMode == ChartViewMode.TOTAL) {
            points.map { it.totalHashrateTh }
        } else {
            points.flatMap { it.chainHashratesTh }
        }
    }

    val maxRate = remember(allValues) { ((allValues.maxOrNull() ?: 100.0) * 1.12).coerceAtLeast(1.0) }
    val minRate = remember(allValues) { ((allValues.minOrNull() ?: 0.0) * 0.88).coerceAtLeast(0.0) }
    val range = (maxRate - minRate).coerceAtLeast(1.0)

    Column(modifier = modifier.fillMaxWidth()) {
        // Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Toggle
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    color = if (viewMode == ChartViewMode.TOTAL) AntminerEmerald else Color.Transparent,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { viewMode = ChartViewMode.TOTAL }
                ) {
                    Text(
                        text = "Total Hashrate",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (viewMode == ChartViewMode.TOTAL) Color(0xFF003919) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    color = if (viewMode == ChartViewMode.CHAINS) AntminerEmerald else Color.Transparent,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { viewMode = ChartViewMode.CHAINS }
                ) {
                    Text(
                        text = "3 Chains",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (viewMode == ChartViewMode.CHAINS) Color(0xFF003919) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Legend / Tooltip Hint
            if (selectedIndex != null) {
                val point = points[selectedIndex!!]
                Text(
                    text = "${point.timeLabel}: ${String.format(Locale.US, "%.2f TH/s", point.totalHashrateTh)}",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = AntminerEmerald
                )
            } else {
                Text(
                    text = "Touch to inspect",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Canvas Chart
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .pointerInput(points) {
                        detectTapGestures(
                            onPress = { offset ->
                                val spacing = size.width / (points.size - 1).coerceAtLeast(1)
                                val idx = (offset.x / spacing).toInt().coerceIn(0, points.size - 1)
                                if (selectedIndex != idx) {
                                    selectedIndex = idx
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                }
                            }
                        )
                    }
                    .pointerInput(points) {
                        detectDragGestures(
                            onDrag = { change, _ ->
                                val spacing = size.width / (points.size - 1).coerceAtLeast(1)
                                val idx = (change.position.x / spacing).toInt().coerceIn(0, points.size - 1)
                                if (selectedIndex != idx) {
                                    selectedIndex = idx
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = { /* keep last or clear */ }
                        )
                    }
            ) {
                val width = size.width
                val height = size.height
                val spacing = width / (points.size - 1).coerceAtLeast(1)

                if (viewMode == ChartViewMode.TOTAL) {
                    // Render Total Curve
                    val path = Path()
                    val fillPath = Path()

                    points.forEachIndexed { index, point ->
                        val x = index * spacing
                        val normalizedY = ((point.totalHashrateTh - minRate) / range).toFloat()
                        val y = height - (normalizedY * height)

                        if (index == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, height)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }

                        if (index == points.size - 1) {
                            fillPath.lineTo(x, height)
                            fillPath.close()
                        }
                    }

                    // Gradient fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(AntminerEmerald.copy(alpha = 0.35f), Color.Transparent),
                            startY = 0f,
                            endY = height
                        )
                    )

                    // Line
                    drawPath(
                        path = path,
                        color = AntminerEmerald,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Dots
                    points.forEachIndexed { index, point ->
                        val x = index * spacing
                        val normalizedY = ((point.totalHashrateTh - minRate) / range).toFloat()
                        val y = height - (normalizedY * height)
                        val isSelected = selectedIndex == index

                        drawCircle(
                            color = if (isSelected) Color.White else AntminerEmerald,
                            radius = if (isSelected) 5.dp.toPx() else 3.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                } else {
                    // Render Chain Breakdown (Chains 0..2)
                    val chainCount = points.firstOrNull()?.chainHashratesTh?.size ?: 0
                    for (c in 0 until chainCount) {
                        val chainColor = chainColors.getOrElse(c) { AntminerEmerald }
                        val path = Path()

                        points.forEachIndexed { index, point ->
                            val chainTh = point.chainHashratesTh.getOrElse(c) { 0.0 }
                            val x = index * spacing
                            val normalizedY = ((chainTh - minRate) / range).toFloat()
                            val y = height - (normalizedY * height)

                            if (index == 0) {
                                path.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }
                        }

                        drawPath(
                            path = path,
                            color = chainColor,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Draw vertical inspection cursor if selected
                if (selectedIndex != null) {
                    val x = selectedIndex!! * spacing
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Time labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val firstLabel = points.firstOrNull()?.timeLabel ?: ""
            val midLabel = points.getOrNull(points.size / 2)?.timeLabel ?: ""
            val lastLabel = points.lastOrNull()?.timeLabel ?: ""

            Text(text = firstLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            if (midLabel.isNotBlank()) Text(text = midLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Text(text = lastLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}
