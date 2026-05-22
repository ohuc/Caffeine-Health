package com.uc.homehealth.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uc.homehealth.ui.components.haIconFor
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.InterFamily
import com.uc.homehealth.ui.theme.Spacing
import com.uc.homehealth.ui.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    // Once the ViewModel has flipped the persisted flag, hand control back to the host.
    LaunchedEffect(uiState.onboardingComplete) {
        if (uiState.onboardingComplete) onFinished()
    }

    val cs = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> NamePage(
                        name = uiState.name,
                        onNameChange = viewModel::onNameChange,
                    )
                    2 -> ConnectPage(
                        url = uiState.enteredUrl,
                        localUrl = uiState.enteredLocalUrl,
                        ssidInput = uiState.enteredSsidInput,
                        ssids = uiState.enteredSsids,
                        token = uiState.enteredToken,
                        locationServicesEnabled = uiState.locationServicesEnabled,
                        onUrlChange = viewModel::onUrlChange,
                        onLocalUrlChange = viewModel::onLocalUrlChange,
                        onSsidInputChange = viewModel::onSsidInputChange,
                        onAddSsid = viewModel::addTypedSsid,
                        onRemoveSsid = viewModel::removeSsid,
                        onTokenChange = viewModel::onTokenChange,
                        hasLocationPermission = viewModel::hasLocationPermission,
                        onSsidDetected = viewModel::detectSsid,
                    )
                }
            }

            PageIndicator(
                pageCount = PAGE_COUNT,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp),
            )

            OnboardingFooter(
                currentPage = pagerState.currentPage,
                connectEnabled = uiState.enteredUrl.isNotBlank() && uiState.enteredToken.isNotBlank(),
                onBack = {
                    if (pagerState.currentPage > 0) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                },
                onNext = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                onConnect = viewModel::finishWithConnect,
                onTryDemo = viewModel::finishWithDemo,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.xl, vertical = 12.dp),
            )
        }
    }
}

// ─── Pages ────────────────────────────────────────────────────────────────────

@Composable
private fun WelcomePage() {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .background(cs.primary.copy(alpha = 0.18f), CircleShape)
                    .blur(40.dp),
            )
            Column {
                Text(
                    text = "Welcome to",
                    fontFamily = InterFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurfaceVariant,
                )
                Text(
                    text = "Home Health",
                    fontFamily = InstrumentSerifFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 56.sp,
                    color = cs.onBackground,
                    lineHeight = 60.sp,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        InfoCard(
            iconName = "shield",
            title = "Free, open source, FOSS",
            body = "The whole app is open source. Inspect the code, build it yourself, or fork it — you decide.",
        )
        Spacer(Modifier.height(12.dp))
        InfoCard(
            iconName = "pulse",
            title = "Zero telemetry",
            body = "No analytics, no crash collectors, no ads. Nothing leaves your device.",
        )
        Spacer(Modifier.height(12.dp))
        InfoCard(
            iconName = "wifi",
            title = "Talks straight to your Home Assistant",
            body = "Your data stays on your network. The app connects directly to your HA — no servers in the middle.",
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NamePage(
    name: String,
    onNameChange: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "What should we call you?",
            fontFamily = InstrumentSerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 40.sp,
            color = cs.onBackground,
            lineHeight = 46.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Used in the dashboard greeting. You can change it any time in Settings.",
            fontFamily = InterFamily,
            fontSize = 14.sp,
            color = cs.onSurfaceVariant,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(cs.surfaceContainerHigh)
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .align(Alignment.TopEnd)
                    .background(cs.primary.copy(alpha = 0.20f), CircleShape)
                    .blur(24.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier.size(56.dp).background(cs.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = name.firstOrNull()?.toString()?.uppercase() ?: "A",
                        fontFamily = InstrumentSerifFamily,
                        fontSize = 28.sp,
                        color = cs.onPrimary,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "GREETING NAME",
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = cs.onSurfaceVariant,
                    )
                    PlainField(
                        value = name,
                        onValueChange = onNameChange,
                        placeholder = "Your name",
                        keyboard = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConnectPage(
    url: String,
    localUrl: String,
    ssidInput: String,
    ssids: List<String>,
    token: String,
    locationServicesEnabled: Boolean,
    onUrlChange: (String) -> Unit,
    onLocalUrlChange: (String) -> Unit,
    onSsidInputChange: (String) -> Unit,
    onAddSsid: () -> Unit,
    onRemoveSsid: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    hasLocationPermission: () -> Boolean,
    onSsidDetected: () -> Unit,
) {
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onSsidDetected() }

    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Connect Home Assistant",
            fontFamily = InstrumentSerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 40.sp,
            color = cs.onBackground,
            lineHeight = 46.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Enter your HA server URL and a long-lived access token. Or skip and explore the app with sample data first.",
            fontFamily = InterFamily,
            fontSize = 14.sp,
            color = cs.onSurfaceVariant,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(20.dp))

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
                Icon(
                    imageVector = haIconFor("wifi"),
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "Remote URL (used away from home)",
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = cs.onSurface,
                )
            }
            PlainField(
                value = url,
                onValueChange = onUrlChange,
                placeholder = "https://ha.example.com",
                keyboard = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = haIconFor("wifi"),
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "Local URL (optional, used on home Wi-Fi)",
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = cs.onSurface,
                )
            }
            PlainField(
                value = localUrl,
                onValueChange = onLocalUrlChange,
                placeholder = "http://homeassistant.local:8123",
                keyboard = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = haIconFor("wifi"),
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "Home Wi-Fi SSIDs",
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = cs.onSurface,
                )
            }
            SsidChipList(ssids = ssids, onRemove = onRemoveSsid)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    PlainField(
                        value = ssidInput,
                        onValueChange = onSsidInputChange,
                        placeholder = "MyHomeWiFi",
                        keyboard = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }
                Button(
                    onClick = onAddSsid,
                    enabled = ssidInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Add", fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            TextButton(
                onClick = {
                    if (hasLocationPermission()) onSsidDetected()
                    else permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                },
            ) {
                Text(
                    "Add current Wi-Fi",
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = cs.primary,
                )
            }
            Text(
                text = "When connected to any listed Wi-Fi the app uses the local URL; otherwise it uses the remote URL. Detection needs Location permission and Location Services turned on.",
                fontFamily = InterFamily,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant,
                lineHeight = 15.sp,
            )
            if (!locationServicesEnabled) {
                Text(
                    text = "Location Services are off — turn them on in system settings to detect the current Wi-Fi.",
                    fontFamily = InterFamily,
                    fontSize = 11.sp,
                    color = cs.error,
                    lineHeight = 15.sp,
                )
            }

            Spacer(Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = haIconFor("lock"),
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "Long-lived access token",
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = cs.onSurface,
                )
            }
            PlainField(
                value = token,
                onValueChange = onTokenChange,
                placeholder = "eyJhbGciOi…",
                keyboard = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                visualTransformation = PasswordVisualTransformation(),
            )

            Text(
                text = "HA → Profile → Security → Long-lived access tokens",
                fontFamily = InterFamily,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─── Footer / nav ─────────────────────────────────────────────────────────────

@Composable
private fun OnboardingFooter(
    currentPage: Int,
    connectEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onConnect: () -> Unit,
    onTryDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val isLast = currentPage == PAGE_COUNT - 1

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(
                visible = currentPage > 0,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                TextButton(onClick = onBack) {
                    Text(
                        "Back",
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (isLast) {
                Button(
                    onClick = onConnect,
                    enabled = connectEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    Text(
                        "Connect",
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            } else {
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    Text(
                        "Continue",
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        if (isLast) {
            TextButton(
                onClick = onTryDemo,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Skip — explore with demo data",
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { i ->
            val active = i == currentPage
            val width by animateDpAsState(if (active) 22.dp else 8.dp, label = "dot_w")
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .background(
                        if (active) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.35f),
                        CircleShape,
                    ),
            )
        }
    }
}

// ─── Building blocks ──────────────────────────────────────────────────────────

@Composable
private fun InfoCard(
    iconName: String,
    title: String,
    body: String,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerHigh, MaterialTheme.shapes.large)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(cs.primaryContainer, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = haIconFor(iconName),
                contentDescription = null,
                tint = cs.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontFamily = InterFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = cs.onSurface,
            )
            Text(
                text = body,
                fontFamily = InterFamily,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardOptions,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
) {
    val cs = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(fontFamily = InterFamily, fontSize = 13.sp, color = cs.onSurface),
        cursorBrush = SolidColor(cs.primary),
        singleLine = true,
        keyboardOptions = keyboard,
        keyboardActions = KeyboardActions(),
        visualTransformation = visualTransformation,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        fontFamily = InterFamily,
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                inner()
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SsidChipList(ssids: List<String>, onRemove: (String) -> Unit) {
    if (ssids.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ssids.forEach { ssid ->
            Row(
                modifier = Modifier
                    .background(cs.primaryContainer, RoundedCornerShape(20.dp))
                    .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = ssid,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = cs.onPrimaryContainer,
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable { onRemove(ssid) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Remove $ssid",
                        tint = cs.onPrimaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
