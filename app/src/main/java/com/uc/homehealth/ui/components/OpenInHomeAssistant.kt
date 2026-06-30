package com.uc.homehealth.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.R
import com.uc.homehealth.ui.theme.MontserratFamily

// Shared "hand off to Home Assistant" affordance — the Settings page and the Pulse
// report both deep-link out with the same branded button and launch behavior.

private const val HA_COMPANION_PACKAGE = "io.homeassistant.companion.android"
private const val HA_COMPANION_MINIMAL_PACKAGE = "io.homeassistant.companion.android.minimal"

private val HaBlue = Color(0xFF18BCF2)

/** Opens the HA companion app if installed, else the server URL in a browser. */
fun launchHomeAssistant(context: Context, haUrl: String) {
    val pm = context.packageManager
    val launchIntent = pm.getLaunchIntentForPackage(HA_COMPANION_PACKAGE)
        ?: pm.getLaunchIntentForPackage(HA_COMPANION_MINIMAL_PACKAGE)
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return
    }
    val webTarget = haUrl.takeIf { it.isNotBlank() } ?: "https://www.home-assistant.io/"
    val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webTarget)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(viewIntent) }
}

@Composable
fun OpenInHomeAssistantButton(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // HA's brand blue is light, so the legible ink on it is near-black — picked by the
    // same luminance helper the glance tiles use, not assumed white.
    val ink = glanceInkOn(HaBlue)
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = HaBlue,
            contentColor = ink,
        ),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_home_assistant),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = ink,
            )
            Text(
                text = subtitle,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = ink.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
