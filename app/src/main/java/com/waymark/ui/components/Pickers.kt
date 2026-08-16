package com.waymark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.waymark.domain.logic.TimeText
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkShapes
import com.waymark.ui.theme.WaymarkSpacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Dates and times are picked, not typed.
 *
 * Everything here used to be a text field: `2026-05-14`, `16:20`, parsed on
 * every keystroke and silently ignored until it happened to parse. That asks a
 * traveler to know a format, get it right on a phone keyboard, and receive no
 * help at all — and it makes "the Thursday after we land" a mental arithmetic
 * problem rather than a thing you can see and tap.
 *
 * These are drawn in the app's own idiom rather than pulled from Material's
 * pickers, which arrive with their own palette, their own shapes and their own
 * ideas about elevation, none of which survive contact with this theme.
 */

// — Date ———————————————————————————————————————————————————————————————————

/** A tappable field that shows a date and opens the calendar. */
@Composable
fun DateField(
    value: LocalDate,
    onValueChange: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * Days outside these bounds are drawn but cannot be chosen. Expressed as
     * two nullable bounds rather than a `ClosedRange`: `LocalDate` implements
     * `Comparable<ChronoLocalDate>`, not `Comparable<LocalDate>`, so `a..b`
     * does not produce the range you would expect.
     */
    earliest: LocalDate? = null,
    latest: LocalDate? = null,
) {
    var open by remember { mutableStateOf(false) }

    PickerField(
        label = label,
        value = TimeText.dayCompact(value),
        detail = value.year.toString(),
        icon = WaymarkIcons.Calendar,
        enabled = enabled,
        modifier = modifier,
        onClick = { open = true },
    )

    if (open) {
        DatePickerModal(
            initial = value,
            earliest = earliest,
            latest = latest,
            onDismiss = { open = false },
            onPick = {
                onValueChange(it)
                open = false
            },
        )
    }
}

@Composable
fun DatePickerModal(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
    earliest: LocalDate? = null,
    latest: LocalDate? = null,
    /** Offered only where the field is genuinely optional. */
    onClear: (() -> Unit)? = null,
) {
    val colors = Waymark.colors
    var month by remember(initial) { mutableStateOf(YearMonth.from(initial)) }
    var selected by remember(initial) { mutableStateOf(initial) }

    WaymarkModal(title = MONTH_YEAR.format(month), eyebrow = "Pick a date", onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostIconButton(
                icon = WaymarkIcons.ChevronLeft,
                contentDescription = "Previous month",
                onClick = { month = month.minusMonths(1) },
            )
            Text(
                text = TimeText.dayCompact(selected),
                style = Waymark.type.data,
                color = colors.accentAmber,
            )
            GhostIconButton(
                icon = WaymarkIcons.ChevronRight,
                contentDescription = "Next month",
                onClick = { month = month.plusMonths(1) },
            )
        }

        // Weekday header. Monday-first: this is a travel app, and a week that
        // starts on Sunday splits every weekend across two rows.
        Row(modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.values().forEach { day ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    FieldLabel(day.getDisplayName(JavaTextStyle.NARROW, Locale.US))
                }
            }
        }

        // Leading blanks so the first of the month lands under its weekday.
        val lead = month.atDay(1).dayOfWeek.value - 1
        val cells = lead + month.lengthOfMonth()
        val rows = (cells + 6) / 7

        (0 until rows).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                (0 until 7).forEach { column ->
                    val index = row * 7 + column
                    val dayOfMonth = index - lead + 1
                    if (dayOfMonth < 1 || dayOfMonth > month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                        return@forEach
                    }
                    val date = month.atDay(dayOfMonth)
                    DayCell(
                        day = dayOfMonth,
                        selected = date == selected,
                        today = date == LocalDate.now(),
                        enabled = (earliest == null || !date.isBefore(earliest)) &&
                            (latest == null || !date.isAfter(latest)),
                        modifier = Modifier.weight(1f),
                        onClick = { selected = date },
                    )
                }
            }
        }

        PrimaryButton(
            text = "Choose",
            icon = WaymarkIcons.Check,
            onClick = { onPick(selected) },
            modifier = Modifier.fillMaxWidth(),
        )
        onClear?.let {
            MutedButton(text = "Clear", onClick = it, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    selected: Boolean,
    today: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = Waymark.colors
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(WaymarkShapes.chip)
            .background(if (selected) colors.accentBright else Waymark.colors.panelFaint)
            .then(
                if (today && !selected) {
                    Modifier.border(1.dp, colors.amber(0.55f), WaymarkShapes.chip)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = Waymark.type.dataSmall,
            color = when {
                selected -> colors.onAccent
                !enabled -> colors.textFaint
                else -> colors.textBody
            },
        )
    }
}

/**
 * A date that may legitimately be absent — a passport whose expiry nobody has
 * looked up yet.
 *
 * Unset shows a dash rather than today's date, because defaulting to today
 * would quietly assert that the document expired this morning. Once set it can
 * be cleared again from the same control.
 */
@Composable
fun OptionalDateField(
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    earliest: LocalDate? = null,
    latest: LocalDate? = null,
) {
    var open by remember { mutableStateOf(false) }

    PickerField(
        label = label,
        value = value?.let { TimeText.dayCompact(it) } ?: "Not set",
        detail = value?.year?.toString(),
        icon = WaymarkIcons.Calendar,
        enabled = true,
        muted = value == null,
        modifier = modifier,
        onClick = { open = true },
    )

    if (open) {
        DatePickerModal(
            initial = value ?: LocalDate.now().plusYears(5),
            earliest = earliest,
            latest = latest,
            onClear = if (value == null) null else {
                {
                    onValueChange(null)
                    open = false
                }
            },
            onDismiss = { open = false },
            onPick = {
                onValueChange(it)
                open = false
            },
        )
    }
}

// — Time ———————————————————————————————————————————————————————————————————

/** A tappable field that shows a time and opens the dial. */
@Composable
fun TimeField(
    value: LocalTime?,
    onValueChange: (LocalTime) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "--:--",
) {
    var open by remember { mutableStateOf(false) }

    PickerField(
        label = label,
        value = value?.format(CLOCK) ?: placeholder,
        detail = null,
        icon = WaymarkIcons.Clock,
        enabled = enabled,
        muted = value == null,
        modifier = modifier,
        onClick = { open = true },
    )

    if (open) {
        TimePickerModal(
            initial = value ?: LocalTime.of(9, 0),
            onDismiss = { open = false },
            onPick = {
                onValueChange(it)
                open = false
            },
        )
    }
}

/**
 * The whole clock, laid out and tapped: twenty-four hours over twelve minutes.
 *
 * Two earlier versions of this were worse. The first was a pair of dragged
 * wheels with stepper arrows, which turned "16:20" into eleven taps or a drag
 * you had to watch. The second added a row of shortcut chips at three-hour
 * intervals — 06:00, 09:00, 12:00 and so on — which is a guess about when
 * people travel dressed up as a convenience, and gets in the way of the times
 * nobody publishes on the hour.
 *
 * Every value is on screen and every value is one tap. Hours run 00–23 in the
 * same six-wide grid the calendar uses, so the two pickers feel like the same
 * control. Minutes step by five, because departures are published in fives and
 * an app that offers `:37` is offering precision it will never be asked for.
 * The running time is the modal's own title, which leaves the body to be
 * nothing but the choices.
 */
@Composable
fun TimePickerModal(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPick: (LocalTime) -> Unit,
) {
    var hour by remember(initial) { mutableStateOf(initial.hour) }
    var minute by remember(initial) { mutableStateOf(initial.minute / 5 * 5) }

    WaymarkModal(
        title = LocalTime.of(hour, minute).format(CLOCK),
        eyebrow = "Local clock",
        onDismiss = onDismiss,
    ) {
        ClockGrid(
            label = "Hour",
            values = (0..23).toList(),
            perRow = 6,
            selected = hour,
            onPick = { hour = it },
        )
        ClockGrid(
            label = "Minute",
            values = (0 until 60 step 5).toList(),
            perRow = 6,
            selected = minute,
            onPick = { minute = it },
        )

        PrimaryButton(
            text = "Choose",
            icon = WaymarkIcons.Check,
            onClick = { onPick(LocalTime.of(hour, minute)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A labelled block of clock values, [perRow] to a line, one of them lit. */
@Composable
private fun ClockGrid(
    label: String,
    values: List<Int>,
    perRow: Int,
    selected: Int,
    onPick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
    ) {
        FieldLabel(label)
        // The rows sit tight against each other — a grid, not a stack of rows.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            values.chunked(perRow).forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    line.forEach { value ->
                        ClockCell(
                            value = value,
                            selected = value == selected,
                            modifier = Modifier.weight(1f),
                            onClick = { onPick(value) },
                        )
                    }
                    // A short last line keeps its cells the width of the ones
                    // above rather than stretching to fill the gap.
                    repeat(perRow - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ClockCell(
    value: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = Waymark.colors
    Box(
        modifier = modifier
            // Roughly 45×38 on a narrow phone: a comfortable target, and the
            // grid still fits above the fold with the button.
            .height(38.dp)
            .clip(WaymarkShapes.chip)
            .background(if (selected) colors.accentBright else colors.panelFaint)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value.toString().padStart(2, '0'),
            style = Waymark.type.dataSmall,
            color = if (selected) colors.onAccent else colors.textBody,
        )
    }
}

// — Shared ————————————————————————————————————————————————————————————————

/**
 * A field that opens something instead of accepting typing.
 *
 * Shaped *exactly* like [WaymarkTextField] — same corner, same border, same
 * 11dp of vertical padding — so a date sitting beside a flight number is the
 * same height as it. It was 13dp, which is invisible on its own and obvious in
 * a row of mixed fields.
 */
@Composable
private fun PickerField(
    label: String,
    value: String,
    detail: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val colors = Waymark.colors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
    ) {
        FieldLabel(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(WaymarkShapes.control)
                .background(if (enabled) colors.input else colors.disabled)
                .border(1.dp, colors.border, WaymarkShapes.control)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = WaymarkSpacing.small, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
        ) {
            WaymarkIcon(
                icon = icon,
                tint = if (enabled) colors.textDim else colors.textFaint,
                size = 15.dp,
            )
            Text(
                text = value,
                style = Waymark.type.data,
                color = when {
                    !enabled -> colors.textFaint
                    muted -> colors.textFaint
                    else -> colors.textStrong
                },
                modifier = Modifier.weight(1f),
            )
            detail?.let {
                Text(text = it, style = Waymark.type.dataSmall, color = colors.textDim)
            }
        }
    }
}

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
