package com.example.antminerconnectiontest.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.theme.*

enum class AsicChipState {
    OPERATIONAL, // 'o'
    DEFECTIVE,   // 'x'
    MISSING      // '-'
}

data class AsicChipInfo(
    val chipNumber: Int,
    val state: AsicChipState
)

@Composable
fun AsicChipMatrix(
    asicStatusString: String,
    totalChips: Int = 77,
    modifier: Modifier = Modifier
) {
    // Parse individual chip characters, ignoring spaces
    val cleanChars = asicStatusString.filter { it == 'o' || it == 'O' || it == 'x' || it == 'X' || it == '-' }
    val chips = remember(cleanChars, totalChips) {
        val list = mutableListOf<AsicChipInfo>()
        for (i in 0 until totalChips) {
            val char = cleanChars.getOrNull(i) ?: 'o'
            val state = when (char) {
                'o', 'O' -> AsicChipState.OPERATIONAL
                'x', 'X' -> AsicChipState.DEFECTIVE
                else -> AsicChipState.MISSING
            }
            list.add(AsicChipInfo(chipNumber = i + 1, state = state))
        }
        list
    }

    var selectedChip by remember { mutableStateOf<AsicChipInfo?>(null) }

    val operationalCount = chips.count { it.state == AsicChipState.OPERATIONAL }
    val defectiveCount = chips.count { it.state == AsicChipState.DEFECTIVE }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MinerBackground.copy(alpha = 0.8f)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MinerBorder)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ASIC Matrix",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$operationalCount / $totalChips Healthy",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (defectiveCount > 0) WarningAmber else AntminerEmerald,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Legend
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendItem(color = AntminerEmerald, label = "OK")
                    if (defectiveCount > 0) {
                        LegendItem(color = ErrorRuby, label = "Error")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hardware-accelerated Canvas Grid for zero-overhead rendering
            val cols = 7
            val rowsCount = (chips.size + cols - 1) / cols
            val haptic = LocalHapticFeedback.current

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((rowsCount * 22).dp)
                    .pointerInput(chips) {
                        detectTapGestures { offset ->
                            val cellWidth = size.width / cols.toFloat()
                            val cellHeight = size.height / rowsCount.toFloat()
                            val col = (offset.x / cellWidth).toInt().coerceIn(0, cols - 1)
                            val row = (offset.y / cellHeight).toInt().coerceIn(0, rowsCount - 1)
                            val index = row * cols + col
                            if (index in chips.indices) {
                                selectedChip = chips[index]
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    }
            ) {
                val cellWidth = size.width / cols.toFloat()
                val cellHeight = size.height / rowsCount.toFloat()
                val outerRadius = 8.dp.toPx()
                val innerRadius = 3.dp.toPx()

                chips.forEachIndexed { index, chip ->
                    val r = index / cols
                    val c = index % cols
                    val cx = c * cellWidth + cellWidth / 2f
                    val cy = r * cellHeight + cellHeight / 2f

                    val chipColor = when (chip.state) {
                        AsicChipState.OPERATIONAL -> AntminerEmerald
                        AsicChipState.DEFECTIVE -> ErrorRuby
                        AsicChipState.MISSING -> Color(0xFF334155)
                    }

                    // Outer halo
                    drawCircle(
                        color = chipColor.copy(alpha = 0.2f),
                        radius = outerRadius,
                        center = Offset(cx, cy)
                    )
                    // Outer border ring
                    drawCircle(
                        color = chipColor,
                        radius = outerRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    // Inner solid dot
                    drawCircle(
                        color = chipColor,
                        radius = innerRadius,
                        center = Offset(cx, cy)
                    )
                }
            }

            // Selected chip detail pill
            if (selectedChip != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MinerSurfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chip #${selectedChip!!.chipNumber}: ${selectedChip!!.state.name}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tap to dismiss",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier.clickable { selectedChip = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
    }
}
