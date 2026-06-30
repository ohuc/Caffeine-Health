package com.uc.homehealth.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.BuildConfig
import com.uc.homehealth.R
import com.uc.homehealth.ui.components.SettingsPageScaffold
import com.uc.homehealth.ui.components.Tap
import com.uc.homehealth.ui.components.haIconFor
import com.uc.homehealth.ui.components.rememberAppHaptics
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.Spacing

// The white field mirrors ic_launcher_background.xml so the in-app icon tile is
// pixel-identical to the launcher icon, independent of theme / Material You.
private val IconTileTop = Color(0xFFFDFDFD)
private val IconTileBottom = Color(0xFFFDFDFD)

/**
 * Settings → About. App identity (the brand "Pulse House" mark), build info, a credits
 * strip, and a couple of outbound links. Pure presentation — version/build come from
 * [BuildConfig], the OS line from [Build]; nothing here touches Home Assistant.
 */
@Composable
fun AboutSettingsScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptic = rememberAppHaptics()

    SettingsPageScaffold(
        title = "About",
        subtitle = "App info, build & credits",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.ml),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeroCard()

            TextCard(
                title = "What is this",
                body = "Home Health is a calm, expressive remote and dashboard for your " +
                    "Home Assistant home — lights, climate, cameras, media, energy and a live " +
                    "“pulse” of how the house is doing, all in one place. Signed out, it runs " +
                    "entirely on sample data in demo mode, so you can explore before connecting.",
            )

            BuildInfoCard()

            BuiltWithCard()

            SectionCard(title = "Links", icon = haIconFor("open")) {
                LinkRow(
                    icon = haIconFor("home"),
                    title = "Home Assistant",
                    subtitle = "home-assistant.io",
                    onClick = { haptic.navigation(); openUrl(context, "https://www.home-assistant.io") },
                )
                LinkRow(
                    icon = haIconFor("palette"),
                    title = "Material 3 Expressive",
                    subtitle = "m3.material.io",
                    onClick = { haptic.navigation(); openUrl(context, "https://m3.material.io") },
                )
            }

            Spacer(Modifier.size(2.dp))
            Text(
                text = "Made with care for a calmer home.",
                fontFamily = InstrumentSerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 18.sp,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------

@Composable
private fun HeroCard() {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerHigh, MaterialTheme.shapes.large)
            .padding(horizontal = 18.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Brand icon tile — the launcher icon, reconstructed in-app.
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(IconTileTop, IconTileBottom)))
                .border(1.dp, cs.outlineVariant, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo_glyph),
                contentDescription = "Home Health icon",
                modifier = Modifier.size(84.dp),
            )
        }
        Spacer(Modifier.size(14.dp))
        Text(
            text = "Home Health",
            fontFamily = InstrumentSerifFamily,
            fontSize = 34.sp,
            color = cs.onSurface,
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = "Remote & dashboard for Home Assistant",
            fontFamily = MontserratFamily,
            fontSize = 12.sp,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(14.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(cs.primary.copy(alpha = 0.16f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = "v${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = cs.primary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
private fun TextCard(title: String, body: String) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerHigh, MaterialTheme.shapes.large)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.onSurface)
        Text(
            text = body,
            fontFamily = MontserratFamily,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun BuildInfoCard() {
    val rows = listOf(
        "Version" to BuildConfig.VERSION_NAME,
        "Build" to BuildConfig.VERSION_CODE.toString(),
        "Package" to BuildConfig.APPLICATION_ID,
        "Android" to "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
    )
    SectionCard(title = "Build information", icon = haIconFor("chip")) {
        rows.forEach { (label, value) -> InfoRow(label, value) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuiltWithCard() {
    val tech = listOf(
        "Jetpack Compose", "Material 3 Expressive", "Hilt", "Navigation 3",
        "DataStore", "Haze", "MapLibre", "Media3", "WebRTC", "OkHttp",
    )
    SectionCard(title = "Built with", icon = haIconFor("extension")) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tech.forEach { TechChip(it) }
        }
    }
}

// ---------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerHigh, MaterialTheme.shapes.large)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
            Text(title, fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.onSurface)
        }
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = cs.onSurfaceVariant)
        Text(
            text = value,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = cs.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Tap(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(cs.primary.copy(alpha = 0.16f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(17.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontFamily = MontserratFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = cs.onSurface)
                Text(subtitle, fontFamily = MontserratFamily, fontSize = 11.sp, color = cs.onSurfaceVariant)
            }
            Icon(haIconFor("open"), contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun TechChip(text: String) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(cs.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = cs.onSurfaceVariant)
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
