package com.uc.homehealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.InterFamily
import com.uc.homehealth.ui.theme.Spacing

/**
 * Shared scaffold for the Settings root and every settings subpage.
 *
 * Mirrors Caffeine's SettingsPageScaffold: italic serif title, optional
 * back button row, then the page body in a vertically-scrolling column.
 */
@Composable
fun SettingsPageScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    // Opaque background is required so adjacent screens don't bleed through during
    // the predictive-back slide animation in the parent NavHost.
    Box(modifier = modifier.fillMaxSize().background(cs.background)) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 130.dp),
    ) {
        if (onBack != null) {
            Tap(onClick = onBack) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.ml, end = Spacing.xl, top = 10.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Settings",
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(
                start = Spacing.xl,
                end = Spacing.xl,
                top = if (onBack != null) 6.dp else 16.dp,
                bottom = 18.dp,
            ),
        ) {
            Text(
                text = title,
                fontFamily = InstrumentSerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 40.sp,
                color = cs.onBackground,
            )
            if (subtitle != null) {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = subtitle,
                    fontFamily = InterFamily,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        content()
    }
    }
}
