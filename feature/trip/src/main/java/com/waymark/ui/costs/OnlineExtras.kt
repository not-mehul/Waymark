package com.waymark.ui.costs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing

/**
 * The one place Waymark asks to go online, and says exactly what that means
 * before it does. The same switch serves the weather and the rates.
 */
@Composable
fun OnlineExtrasModal(enabled: Boolean, onSet: (Boolean) -> Unit, onDismiss: () -> Unit) {
    WaymarkModal(title = "Online extras", eyebrow = if (enabled) "On" else "Off", onDismiss = onDismiss) {
        Text(
            text = "Everything else in Waymark works with the radio off, and always will. These two " +
                "extras need to ask somebody:",
            style = Waymark.type.body,
            color = Waymark.colors.textBody,
        )
        Text(
            text = "· The weather where the trip will be, from Open-Meteo. It is sent a place, to " +
                "about a kilometre, and the dates — never the trip, the bookings or who is going.\n" +
                "· The day's exchange rates, from the European Central Bank by way of Frankfurter. " +
                "Nothing is sent but the request for today's rates.",
            style = Waymark.type.bodySmall,
            color = Waymark.colors.textBody,
        )
        Text(
            text = "Neither service needs an account. What is fetched is kept on the phone, so it " +
                "is still there abroad without a signal. Turn this off and the app makes no " +
                "network request at all.",
            style = Waymark.type.hint,
            color = Waymark.colors.textDim,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            if (enabled) {
                MutedButton(
                    text = "Turn off",
                    onClick = { onSet(false); onDismiss() },
                    modifier = Modifier.weight(1f),
                )
            } else {
                MutedButton(text = "Not now", onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = "Turn on",
                    onClick = { onSet(true); onDismiss() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
