package com.waymark.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Reservation
import com.waymark.domain.model.SegmentKind
import com.waymark.ui.components.EditorialNote
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.WaymarkIcon
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import com.waymark.ui.vault.rememberVaultAuthenticator

/**
 * Codes, ticket numbers and passes. Everything is masked until the traveler
 * asks for it and the device agrees they are the traveler.
 */
@Composable
fun VaultTab(
    state: TripUiState,
    onOpenPass: (String) -> Unit,
    onOpenSegment: (String) -> Unit,
) {
    val colors = Waymark.colors
    val authenticator = rememberVaultAuthenticator()
    val clipboard = LocalClipboardManager.current
    var revealed by remember { mutableStateOf(setOf<String>()) }

    fun reveal(id: String) {
        if (id in revealed) {
            revealed = revealed - id
            return
        }
        authenticator.authenticate("Show a stored code") { granted ->
            if (granted) revealed = revealed + id
        }
    }

    val grouped = remember(state.reservations) { state.reservations.groupBy { it.kind } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
    ) {
        if (state.passes.isNotEmpty()) {
            SectionHeader("Boarding passes")
            state.passes.forEach { pass ->
                Panel(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenPass(pass.id) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
                    ) {
                        WaymarkIcon(WaymarkIcons.Barcode, tint = colors.accentAmber, size = 20.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${pass.designator} · ${pass.origin} → ${pass.destination}",
                                style = Waymark.type.cardTitle,
                                color = colors.textHeading,
                            )
                            Text(
                                text = listOfNotNull(
                                    pass.passengerName,
                                    pass.seat?.let { "Seat $it" },
                                    pass.boardingGroup?.let { "Group $it" },
                                ).joinToString(" · "),
                                style = Waymark.type.dataSmall,
                                color = colors.textDim,
                            )
                        }
                        WaymarkIcon(WaymarkIcons.ChevronRight, tint = colors.textFaint, size = 18.dp)
                    }
                }
            }
        }

        SectionHeader("Reservations")

        if (state.reservations.isEmpty()) {
            Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No codes stored yet.",
                    style = Waymark.type.hint,
                    color = colors.textDim,
                )
            }
        }

        SegmentKind.entries.forEach { kind ->
            val records = grouped[kind].orEmpty()
            if (records.isEmpty()) return@forEach
            FieldLabel(kindLabel(kind))
            records.forEach { reservation ->
                ReservationCard(
                    reservation = reservation,
                    state = state,
                    revealed = revealed,
                    onReveal = ::reveal,
                    onCopy = { clipboard.setText(AnnotatedString(it)) },
                    onOpenSegment = onOpenSegment,
                )
            }
            Spacer(Modifier.height(WaymarkSpacing.snug))
        }

        EditorialNote(
            term = "Sealed",
            body = "Codes are encrypted with a key held in this device's secure hardware. " +
                "A copy of the database, taken off the phone, reads as noise.",
        )
        Spacer(Modifier.height(WaymarkSpacing.section))
    }
}

@Composable
private fun ReservationCard(
    reservation: Reservation,
    state: TripUiState,
    revealed: Set<String>,
    onReveal: (String) -> Unit,
    onCopy: (String) -> Unit,
    onOpenSegment: (String) -> Unit,
) {
    val colors = Waymark.colors

    Panel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reservation.label,
                    style = Waymark.type.cardTitle,
                    color = colors.textHeading,
                )
                Text(
                    text = reservation.vendor,
                    style = Waymark.type.hint,
                    color = colors.textDim,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                reservation.travelerIds.mapNotNull { state.dossier?.party?.byId(it) }
                    .forEach { PartyMark(initials = it.initials, size = 22.dp) }
            }
        }

        Spacer(Modifier.height(WaymarkSpacing.small))

        reservation.secrets.forEachIndexed { index, secret ->
            val key = "${reservation.id}:$index"
            val open = key in revealed
            val owner = secret.travelerId?.let { state.dossier?.party?.byId(it)?.displayName }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FieldLabel(
                        listOfNotNull(secret.field.label, owner).joinToString(" · ")
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (open) secret.value else secret.masked,
                        style = Waymark.type.dataLarge,
                        color = if (open) colors.textStrong else colors.textMuted,
                    )
                }
                GhostIconButton(
                    icon = if (open) WaymarkIcons.EyeOff else WaymarkIcons.Eye,
                    contentDescription = if (open) "Hide" else "Reveal",
                    onClick = { onReveal(key) },
                )
                if (open) {
                    GhostIconButton(
                        icon = WaymarkIcons.Ticket,
                        contentDescription = "Copy",
                        onClick = { onCopy(secret.value) },
                    )
                }
            }
            Spacer(Modifier.height(WaymarkSpacing.snug))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Updated ${TimeText.relative(reservation.updatedAtMillis)}",
                style = Waymark.type.fieldLabel,
                color = colors.textFaint,
            )
            reservation.segmentId?.let { segmentId ->
                MutedButton(
                    text = "Open booking",
                    onClick = { onOpenSegment(segmentId) },
                )
            }
        }
    }
}

private fun kindLabel(kind: SegmentKind): String = when (kind) {
    SegmentKind.FLIGHT -> "Flights"
    SegmentKind.LODGING -> "Lodging"
    SegmentKind.GROUND -> "Ground"
    SegmentKind.EXPERIENCE -> "Experiences"
}
