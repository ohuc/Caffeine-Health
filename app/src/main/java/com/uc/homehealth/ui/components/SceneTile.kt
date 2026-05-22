package com.uc.homehealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.HaScene
import com.uc.homehealth.ui.theme.InterFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.PillShape

@Composable
fun SceneTile(
    scene: HaScene,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = scene.isActive
    val cs = MaterialTheme.colorScheme

    Tap(onClick = onTap, modifier = modifier) {
        Row(
            modifier = Modifier
                .background(
                    color = if (isActive) cs.primary else cs.surfaceContainerHigh,
                    shape = PillShape,
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = scene.emoji, fontSize = 18.sp)

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = scene.name,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = (-0.1).sp,
                    color = if (isActive) cs.onPrimary else cs.onSurface,
                )
                Text(
                    text = if (isActive) "Active" else "Tap to run",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = (if (isActive) cs.onPrimary else cs.onSurface).copy(alpha = 0.65f),
                )
            }
        }
    }
}
