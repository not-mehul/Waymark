package com.waymark.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.waymark.BuildConfig
import com.waymark.ui.components.Hairline
import com.waymark.ui.components.SectionLabel
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing

/**
 * What this is, what version it is, and where the data in it came from.
 *
 * An app that claims to work offline should be able to say so from inside
 * itself, and an app that bundles somebody else's public-domain data should
 * credit it somewhere a reader can actually reach. Both live here, behind the
 * version line at the foot of the shelf — the conventional place to look, and
 * out of the way of the trips.
 */
@Composable
fun AboutModal(onDismiss: () -> Unit) {
    WaymarkModal(
        title = "Waymark",
        eyebrow = "Version ${BuildConfig.VERSION_NAME}",
        onDismiss = onDismiss,
    ) {
        Text(
            text = "Everything for a trip in one place, held on this device. " +
                "Waymark asks for no network permission, so it cannot phone home, " +
                "cannot leak an itinerary, and cannot stop working when the signal does.",
            style = Waymark.type.bodySmall,
            color = Waymark.colors.textBody,
        )
        Text(
            text = "Nothing is backed up off this device. A trip lives on this phone " +
                "until you export it as markdown, which is the way to keep a copy " +
                "anywhere else or hand one to somebody.",
            style = Waymark.type.hint,
            color = Waymark.colors.textDim,
        )

        Hairline()

        SectionLabel("Bundled data")
        Credit("Coastlines", "Natural Earth 1:110m — public domain")
        Credit("Stations", "IATA airport table — deduplicated, zones resolved from coordinates")
        Credit("Destination notes", "Hand-compiled")

        Hairline()

        Credit("Licence", "MIT")
    }
}

@Composable
private fun Credit(label: String, detail: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight),
    ) {
        Text(text = label, style = Waymark.type.bodySmall, color = Waymark.colors.textBody)
        Text(text = detail, style = Waymark.type.hint, color = Waymark.colors.textDim)
    }
}
