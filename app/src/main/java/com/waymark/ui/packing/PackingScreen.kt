package com.waymark.ui.packing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.domain.model.PackingCategory
import com.waymark.domain.model.PackingItem
import com.waymark.ui.charts.Meter
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.Footnote
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.ScreenScaffold
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.WaymarkIcon
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkShapes
import com.waymark.ui.theme.WaymarkSpacing
import com.waymark.ui.trip.TripViewModel

/**
 * The bag, per traveler, with a first draft the app can write itself: counts
 * scaled to the number of nights, an adaptor only where the sockets actually
 * differ, a swimsuit only where something on the trip involves water.
 */
@Composable
fun PackingScreen(
    viewModel: TripViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = Waymark.colors
    val party = state.dossier?.party?.travelers.orEmpty()

    // Null is the shared list; it always exists, and is the default view.
    var selected by remember(party) { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    val visible = state.packing.filter { it.travelerId == selected }

    ScreenScaffold(
        title = "The bag",
        onBack = onBack,
        spacing = WaymarkSpacing.small,
    ) {

        // Whose list
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
        ) {
            OptionChip(
                text = "Shared",
                selected = selected == null,
                onClick = { selected = null },
            )
            party.forEach { traveler ->
                OptionChip(
                    text = traveler.displayName,
                    selected = selected == traveler.id,
                    onClick = { selected = traveler.id },
                    leading = {
                        PartyMark(
                            initials = traveler.initials,
                            active = selected == traveler.id,
                            size = 18.dp,
                        )
                    },
                )
            }
        }

        // Everyone's progress at a glance — one meter each, sage, because
        // no chart here compares them against one another.
        Panel(modifier = Modifier.fillMaxWidth()) {
            state.packingProgress.forEach { entry ->
                Meter(
                    fraction = entry.fraction,
                    label = entry.name +
                        if (entry.essentialOutstanding > 0) {
                            " · ${entry.essentialOutstanding} essential left"
                        } else {
                            ""
                        },
                    value = "${entry.packed}/${entry.total}",
                    tint = if (entry.essentialOutstanding > 0) {
                        colors.accentAmber
                    } else {
                        colors.accentSage
                    },
                )
                Spacer(Modifier.height(WaymarkSpacing.snug))
            }
            if (state.packingProgress.all { it.total == 0 }) {
                Text(
                    text = "Nothing on any list yet.",
                    style = Waymark.type.hint,
                    color = colors.textDim,
                )
            }
        }

        if (visible.isEmpty()) {
            Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "This list is empty.",
                    style = Waymark.type.hint,
                    color = colors.textDim,
                )
                Spacer(Modifier.height(WaymarkSpacing.snug))
                Text(
                    text = "Waymark can draft one from the itinerary — the nights, the " +
                        "climate, the sockets, and whether anything involves water.",
                    style = Waymark.type.bodySmall,
                    color = colors.textMuted,
                )
            }
        }

        PackingCategory.entries.forEach { category ->
            val items = visible.filter { it.category == category }
            if (items.isEmpty()) return@forEach

            SectionHeader(category.label)
            items.sortedBy { it.packed }.forEach { item ->
                PackingRow(
                    item = item,
                    onToggle = { viewModel.setPacked(item.id, !item.packed) },
                    onDelete = { viewModel.deletePackingItem(item.id) },
                )
            }
        }

        Spacer(Modifier.height(WaymarkSpacing.small))
        PrimaryButton(
            text = "Draft from the itinerary",
            icon = WaymarkIcons.Check,
            onClick = { viewModel.suggestPacking(selected) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            MutedButton(
                text = "Add item",
                icon = WaymarkIcons.Plus,
                onClick = { adding = true },
                modifier = Modifier.weight(1f),
            )
            MutedButton(
                text = "Untick all",
                onClick = viewModel::unpackEverything,
                modifier = Modifier.weight(1f),
            )
        }

        Footnote(
            "The shared list is for what one of you carries for both. Nothing is " +
                "added twice, and every suggested line can be deleted."
        )
    }

    if (adding) {
        AddPackingItemModal(
            listName = party.firstOrNull { it.id == selected }?.displayName ?: "the shared list",
            onDismiss = { adding = false },
            onAdd = { title, category, essential ->
                viewModel.addPackingItem(selected, title, category, essential)
                adding = false
            },
        )
    }
}

@Composable
private fun PackingRow(
    item: PackingItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Waymark.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WaymarkShapes.control)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(vertical = WaymarkSpacing.snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
    ) {
        // The tick box: a two-pixel square, filled when packed.
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(WaymarkShapes.chip)
                .background(if (item.packed) colors.accentAmber else colors.input)
                .border(
                    1.dp,
                    if (item.packed) colors.accentAmber else colors.border,
                    WaymarkShapes.chip,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (item.packed) {
                WaymarkIcon(WaymarkIcons.Check, tint = colors.onAccent, size = 13.dp)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (item.quantity > 1) "${item.title} ×${item.quantity}" else item.title,
                style = Waymark.type.bodySmall.copy(
                    textDecoration = if (item.packed) TextDecoration.LineThrough else null,
                ),
                color = if (item.packed) colors.textFaint else colors.textBody,
            )
            item.note?.let {
                Text(text = it, style = Waymark.type.hint, color = colors.textDim)
            }
        }

        if (item.essential && !item.packed) {
            FieldLabel("Essential", color = colors.accentAmber)
        }
        GhostIconButton(
            icon = WaymarkIcons.Close,
            contentDescription = "Remove ${item.title}",
            onClick = onDelete,
        )
    }
}

@Composable
private fun AddPackingItemModal(
    listName: String,
    onDismiss: () -> Unit,
    onAdd: (String, PackingCategory, Boolean) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(PackingCategory.CLOTHING) }
    var essential by remember { mutableStateOf(false) }

    WaymarkModal(title = "Add to $listName", eyebrow = "Packing", onDismiss = onDismiss) {
        WaymarkTextField(
            value = title,
            onValueChange = { title = it },
            label = "Item",
            placeholder = "Swimming goggles",
            modifier = Modifier.fillMaxWidth(),
        )

        FieldLabel("Category")
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            listOf(
                PackingCategory.CLOTHING,
                PackingCategory.ELECTRONICS,
                PackingCategory.TOILETRIES,
            ).forEach { option ->
                OptionChip(
                    text = option.label,
                    selected = category == option,
                    onClick = { category = option },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            listOf(
                PackingCategory.DOCUMENTS,
                PackingCategory.HEALTH,
                PackingCategory.GEAR,
            ).forEach { option ->
                OptionChip(
                    text = option.label,
                    selected = category == option,
                    onClick = { category = option },
                )
            }
        }

        OptionChip(
            text = "Cannot leave without it",
            selected = essential,
            onClick = { essential = !essential },
        )

        PrimaryButton(
            text = "Add",
            onClick = { onAdd(title, category, essential) },
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
