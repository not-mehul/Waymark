package com.waymark.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing

/**
 * One screen, one header.
 *
 * Every secondary screen used to open with the same three-part stack: a back
 * chevron beside a tracked eyebrow, then a serif title, then a line of prose
 * explaining the title. Three restatements of the same fact, above the fold,
 * before any content. This is that header reduced to one line — the title
 * itself, with the back control and at most one action on the same baseline.
 *
 * Anything that needs a sentence of explanation gets it next to the control it
 * explains, not at the top of the page.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrolling: Boolean = true,
    spacing: Dp = WaymarkSpacing.medium,
    action: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    WaymarkBackdrop {
        Column(
            modifier = modifier
                .fillMaxSize()
                .systemBarsPadding()
                .then(if (scrolling) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = WaymarkSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = WaymarkSpacing.snug),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight),
            ) {
                GhostIconButton(
                    icon = WaymarkIcons.ArrowLeft,
                    contentDescription = "Back",
                    onClick = onBack,
                    // Pulled back into the margin so the chevron's ink, not its
                    // tap target, lines up with the text below it. Offset, not
                    // padding: padding rejects a negative value outright.
                    modifier = Modifier.offset(x = -WaymarkSpacing.snug),
                )
                Text(
                    text = title,
                    style = Waymark.type.cardTitle,
                    color = Waymark.colors.textHeading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }

            content()

            if (scrolling) Spacer(Modifier.height(WaymarkSpacing.section))
        }
    }
}

/**
 * The line of quiet prose that used to be an `EditorialNote` at the foot of
 * every screen. Kept for the few places where a caveat genuinely changes what
 * the reader should believe — not for restating what the controls already say.
 */
@Composable
fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Waymark.type.hint,
        color = Waymark.colors.textDim,
        modifier = modifier,
    )
}

/**
 * A row of labelled facts that share a line. Collapses to whatever fits; each
 * entry keeps the serif number over tracked mono label of [Stat].
 */
@Composable
fun StatRow(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.large),
        content = content,
    )
}

/** The standard empty state: one line, dim, no panel, no illustration. */
@Composable
fun EmptyLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Waymark.type.hint,
        color = Waymark.colors.textDim,
        modifier = modifier.padding(vertical = WaymarkSpacing.small),
    )
}
