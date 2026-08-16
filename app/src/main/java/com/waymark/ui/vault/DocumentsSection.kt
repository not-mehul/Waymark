package com.waymark.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.waymark.domain.logic.DocumentUrgency
import com.waymark.domain.logic.DocumentVerdict
import com.waymark.domain.model.DocumentKind
import com.waymark.domain.model.Traveler
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.NoticeBanner
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.OptionalDateField
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.WaymarkIcon
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import java.time.LocalDate

/**
 * Passports, visas, insurance — the records that decide whether a trip happens
 * at all. Numbers are sealed and masked; dates are in the clear so the app can
 * warn about them without asking anyone to authenticate first.
 */
@Composable
fun ColumnScope.DocumentsSection(
    verdicts: List<DocumentVerdict>,
    party: List<Traveler>,
    missingPassportFor: List<String>,
    revealed: Set<String>,
    onReveal: (String) -> Unit,
    onAdd: (String, DocumentKind, String, String, String?, LocalDate?, String?) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = Waymark.colors
    var adding by remember { mutableStateOf(false) }

    SectionHeader("Documents")

    missingPassportFor.mapNotNull { id -> party.firstOrNull { it.id == id } }.forEach { traveler ->
        NoticeBanner(
            icon = WaymarkIcons.Alert,
            headline = "No passport recorded for ${traveler.displayName}",
            detail = "Waymark cannot warn about an expiry it has never seen.",
        )
        Spacer(Modifier.height(WaymarkSpacing.snug))
    }

    verdicts.forEach { verdict ->
        val document = verdict.document
        val owner = party.firstOrNull { it.id == document.travelerId }
        val open = document.id in revealed

        Panel(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
            ) {
                owner?.let { PartyMark(initials = it.initials, size = 26.dp) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.label,
                        style = Waymark.type.cardTitle,
                        color = colors.textHeading,
                    )
                    Text(
                        text = listOfNotNull(
                            document.kind.label,
                            document.issuer,
                            owner?.displayName,
                        ).joinToString(" · "),
                        style = Waymark.type.hint,
                        color = colors.textDim,
                    )
                }
                GhostIconButton(
                    icon = WaymarkIcons.Trash,
                    contentDescription = "Remove ${document.label}",
                    onClick = { onDelete(document.id) },
                )
            }

            Spacer(Modifier.height(WaymarkSpacing.small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FieldLabel("Number")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (open) document.number else document.maskedNumber,
                        style = Waymark.type.dataLarge,
                        color = if (open) colors.textStrong else colors.textMuted,
                    )
                }
                GhostIconButton(
                    icon = if (open) WaymarkIcons.EyeOff else WaymarkIcons.Eye,
                    contentDescription = if (open) "Hide" else "Reveal",
                    onClick = { onReveal(document.id) },
                )
            }

            Spacer(Modifier.height(WaymarkSpacing.snug))

            // The verdict, not just the date: a passport can be in date and
            // still be short of the validity a border will ask for.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
            ) {
                WaymarkIcon(
                    icon = when (verdict.urgency) {
                        DocumentUrgency.FINE -> WaymarkIcons.Check
                        DocumentUrgency.SOON -> WaymarkIcons.Clock
                        else -> WaymarkIcons.Alert
                    },
                    tint = urgencyColour(verdict.urgency),
                    size = 14.dp,
                )
                Text(
                    text = verdict.urgency.label,
                    style = Waymark.type.dataSmall,
                    color = urgencyColour(verdict.urgency),
                )
            }
            Text(text = verdict.detail, style = Waymark.type.hint, color = colors.textDim)
        }
        Spacer(Modifier.height(WaymarkSpacing.snug))
    }

    if (verdicts.isEmpty()) {
        Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "No documents stored.",
                style = Waymark.type.hint,
                color = colors.textDim,
            )
        }
        Spacer(Modifier.height(WaymarkSpacing.snug))
    }

    PrimaryButton(
        text = "Add document",
        icon = WaymarkIcons.Lock,
        onClick = { adding = true },
        modifier = Modifier.fillMaxWidth(),
    )

    if (adding) {
        AddDocumentModal(
            party = party,
            onDismiss = { adding = false },
            onAdd = { travelerId, kind, label, number, issuer, expires, note ->
                onAdd(travelerId, kind, label, number, issuer, expires, note)
                adding = false
            },
        )
    }
}

@Composable
private fun urgencyColour(urgency: DocumentUrgency) = when (urgency) {
    DocumentUrgency.FINE -> Waymark.colors.accentSage
    DocumentUrgency.SOON -> Waymark.colors.accentBright
    else -> Waymark.colors.danger
}

@Composable
private fun AddDocumentModal(
    party: List<Traveler>,
    onDismiss: () -> Unit,
    onAdd: (String, DocumentKind, String, String, String?, LocalDate?, String?) -> Unit,
) {
    var travelerId by remember { mutableStateOf(party.firstOrNull()?.id.orEmpty()) }
    var kind by remember { mutableStateOf(DocumentKind.PASSPORT) }
    var label by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var issuer by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    // A passport nobody has entered an expiry for is the common case on the
    // way in, so the date starts unset rather than defaulting to today and
    // quietly claiming the document has already lapsed.
    var expires by remember { mutableStateOf<LocalDate?>(null) }

    WaymarkModal(title = "Add document", eyebrow = "Vault", onDismiss = onDismiss) {
        if (party.size > 1) {
            FieldLabel("Whose")
            Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                party.forEach { traveler ->
                    OptionChip(
                        text = traveler.displayName,
                        selected = travelerId == traveler.id,
                        onClick = { travelerId = traveler.id },
                    )
                }
            }
        }

        FieldLabel("Kind")
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            listOf(DocumentKind.PASSPORT, DocumentKind.VISA, DocumentKind.INSURANCE)
                .forEach { option ->
                    OptionChip(
                        text = option.label,
                        selected = kind == option,
                        onClick = { kind = option },
                    )
                }
        }

        WaymarkTextField(
            value = label,
            onValueChange = { label = it },
            label = "Label",
            placeholder = kind.label,
            modifier = Modifier.fillMaxWidth(),
        )
        WaymarkTextField(
            value = number,
            onValueChange = { number = it },
            label = "Number",
            mono = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            WaymarkTextField(
                value = issuer,
                onValueChange = { issuer = it },
                label = "Issued by",
                placeholder = "United Kingdom",
                modifier = Modifier.weight(1f),
            )
            OptionalDateField(
                value = expires,
                onValueChange = { expires = it },
                label = "Expires",
                modifier = Modifier.weight(1f),
            )
        }
        WaymarkTextField(
            value = note,
            onValueChange = { note = it },
            label = "Note",
            placeholder = "Where the paper copy is",
            modifier = Modifier.fillMaxWidth(),
        )

        PrimaryButton(
            text = "Seal it",
            onClick = {
                onAdd(
                    travelerId,
                    kind,
                    label.ifBlank { kind.label },
                    number,
                    issuer.ifBlank { null },
                    expires,
                    note.ifBlank { null },
                )
            },
            enabled = travelerId.isNotBlank() && number.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
