package com.example.antminerconnectiontest.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antminerconnectiontest.theme.GroupedSheetsSpec
import com.example.antminerconnectiontest.theme.LocalGlassSettings

/**
 * iOS-style controls for the Grouped Sheets design, v0.4.0 —
 * drawn from [GroupedSheetsSpec] exactly as the approved V3 mockup
 * draws them (.switch / .seg in the mockup CSS).
 */

/**
 * The iOS pill switch: 51×31 track, 27 white knob. ON fills with the
 * accent (Apple blue in this style); OFF is the flat grey track —
 * the two states are unmistakable at a glance, which was the whole
 * point of the redesign (the old glass switches were near-identical
 * pale tints in light mode).
 */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isDark = LocalGlassSettings.current.isDark
    val trackColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            GroupedSheetsSpec.switchOff(isDark)
        },
        label = "iosSwitchTrack"
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) {
            GroupedSheetsSpec.SwitchTrackWidth - GroupedSheetsSpec.SwitchKnobSize -
                GroupedSheetsSpec.SwitchKnobInset
        } else {
            GroupedSheetsSpec.SwitchKnobInset
        },
        label = "iosSwitchKnob"
    )
    Box(
        modifier = modifier
            .width(GroupedSheetsSpec.SwitchTrackWidth)
            .height(GroupedSheetsSpec.SwitchTrackHeight)
            .clip(CircleShape)
            .background(if (enabled) trackColor else trackColor.copy(alpha = 0.5f))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .align(Alignment.CenterStart)
                .size(GroupedSheetsSpec.SwitchKnobSize)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

/**
 * The iOS segmented control: a grey track with a raised white (dark:
 * grey) pill under the selected option. Options share the track
 * equally; text is 13 sp, medium, semibold when selected.
 */
@Composable
fun IosSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalGlassSettings.current.isDark
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(GroupedSheetsSpec.SegmentedTrackRadius))
            .background(GroupedSheetsSpec.segmentedTrack(isDark))
            .padding(GroupedSheetsSpec.SegmentedTrackPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (selected) {
                            Modifier.shadow(
                                2.dp,
                                RoundedCornerShape(GroupedSheetsSpec.SegmentedPillRadius)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .background(
                        if (selected) GroupedSheetsSpec.segmentedSelected(isDark) else Color.Transparent,
                        RoundedCornerShape(GroupedSheetsSpec.SegmentedPillRadius)
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}
