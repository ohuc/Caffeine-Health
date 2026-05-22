package com.uc.homehealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.InterFamily
import com.uc.homehealth.ui.theme.customColors
import com.uc.homehealth.data.HaFavorite

private fun iconNameFor(kind: String) = when (kind) {
    "climate" -> "thermo"
    "light"   -> "bulb"
    "media"   -> "speaker"
    "lock"    -> "lock"
    "energy"  -> "energy"
    else      -> "bulb"
}

// Small horizontal favorite card — matches dashboard.jsx FavCard.
@Composable
fun FavCard(
    fav: HaFavorite,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val custom = MaterialTheme.customColors

    Tap(onClick = onTap, modifier = modifier) {
        Column(
            modifier = Modifier
                .width(144.dp)
                .background(cs.surfaceContainerHigh, MaterialTheme.shapes.medium)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                // Icon circle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = if (fav.isOn) cs.primary else cs.surfaceVariant,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = haIconFor(iconNameFor(fav.kind)),
                        contentDescription = fav.kind,
                        tint = if (fav.isOn) cs.onPrimary else cs.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                // Status dot
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, end = 2.dp)
                        .size(8.dp)
                        .background(
                            color = if (fav.isOn) custom.mint else cs.outline,
                            shape = CircleShape,
                        ),
                )
            }

            Column {
                Text(
                    text = fav.name,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = (-0.1).sp,
                    color = cs.onSurface,
                )
                Text(
                    text = fav.state,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                val favValue = fav.value
                if (favValue != null) {
                    val valueStr = if (favValue == favValue.toInt().toFloat()) {
                        favValue.toInt().toString()
                    } else {
                        favValue.toString()
                    }
                    RollingNumberText(
                        text = "$valueStr${fav.unit.orEmpty()}",
                        style = TextStyle(
                            fontFamily = InstrumentSerifFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 28.sp,
                            lineHeight = 28.sp,
                            color = cs.onSurface,
                        ),
                        modifier = Modifier.padding(top = 4.dp),
                        labelPrefix = "fav_value",
                    )
                }
            }
        }
    }
}
