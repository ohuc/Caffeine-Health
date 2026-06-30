package com.uc.homehealth.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uc.homehealth.ui.components.glanceInkOn
import com.uc.homehealth.ui.components.haIconFor
import com.uc.homehealth.ui.components.rememberTileBlobShape
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.Spacing
import com.uc.homehealth.ui.theme.customColors
import com.uc.homehealth.ui.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

// Shared top padding so every step's header anchors at the same Y — consistent across pages.
private val HeaderTopGap = 28.dp

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
                    .padding(vertical = 14.dp),
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
// Every page is top-anchored (no vertical centering) so headers line up step-to-step.

@Composable
private fun WelcomePage() {
    val cs = MaterialTheme.colorScheme
    val cc = MaterialTheme.customColors

    // One-time staggered entrance: hero, then heading, then each value prop.
    val appear = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { appear.targetState = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl),
    ) {
        Spacer(Modifier.height(HeaderTopGap))

        Staggered(appear, delayMillis = 0) {
            MorphingHero(
                color = cs.primary,
                glyphName = "pulse",
                modifier = Modifier.size(160.dp),
            )
        }

        Spacer(Modifier.height(28.dp))

        Staggered(appear, delayMillis = 90) {
            PageHeader(
                kicker = "Welcome to",
                title = "Home Health",
                subtitle = "Your whole home, one calm tap away.",
                titleSize = 56.sp,
            )
        }

        Spacer(Modifier.height(28.dp))

        Staggered(appear, delayMillis = 200) {
            ValueProp(
                iconName = "shield",
                accent = cc.sky,
                title = "Free & open source",
                body = "Inspect it, build it, or fork it — the whole app is FOSS. You're in control.",
            )
        }
        Spacer(Modifier.height(12.dp))
        Staggered(appear, delayMillis = 290) {
            ValueProp(
                iconName = "lock",
                accent = cc.mint,
                title = "Zero telemetry",
                body = "No analytics, no ads, no trackers. Nothing about your home leaves your device.",
            )
        }
        Spacer(Modifier.height(12.dp))
        Staggered(appear, delayMillis = 380) {
            ValueProp(
                iconName = "wifi",
                accent = cc.sand,
                title = "Straight to your home",
                body = "Talks directly to your Home Assistant on your own network — no servers in the middle.",
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun NamePage(
    name: String,
    onNameChange: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val blob = rememberTileBlobShape()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl),
    ) {
        Spacer(Modifier.height(HeaderTopGap))

        PageHeader(
            kicker = "Personalize",
            title = "What should we call you?",
            subtitle = "Used in your dashboard greeting. You can change it any time in Settings.",
        )

        Spacer(Modifier.height(28.dp))

        // Live greeting preview — gives the page a clear focal block and shows the payoff.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(cs.surfaceContainerHigh)
                .padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(104.dp).clip(blob).background(cs.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.trim().firstOrNull()?.toString()?.uppercase() ?: "✦",
                    fontFamily = InstrumentSerifFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 52.sp,
                    color = cs.onPrimary,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (name.isBlank()) "Welcome home." else "Welcome home, ${name.trim()}.",
                fontFamily = InstrumentSerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 26.sp,
                color = cs.onSurface,
                lineHeight = 30.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "DASHBOARD GREETING PREVIEW",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                color = cs.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))

        FieldLabel(iconName = "sparkle", text = "Greeting name", accent = cs.primary)
        Spacer(Modifier.height(10.dp))
        ExpressiveField(
            value = name,
            onValueChange = onNameChange,
            placeholder = "Your name",
            keyboard = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
        )

        Spacer(Modifier.height(32.dp))
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
    val cc = MaterialTheme.customColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl),
    ) {
        Spacer(Modifier.height(HeaderTopGap))

        PageHeader(
            kicker = "Connect",
            title = "Connect Home Assistant",
            subtitle = "Add your server URL and a long-lived access token. Not ready? Skip and explore with sample data first.",
        )

        Spacer(Modifier.height(28.dp))

        // Sections live directly on the background (no wrapper card) so the filled fields
        // keep contrast — surfaceVariant == surfaceContainerHigh, so a card would hide them.
        FieldLabel(iconName = "wifi", text = "Remote URL", accent = cs.primary)
        Spacer(Modifier.height(10.dp))
        ExpressiveField(
            value = url,
            onValueChange = onUrlChange,
            placeholder = "https://ha.example.com",
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )

        Spacer(Modifier.height(20.dp))
        FieldLabel(iconName = "wifi", text = "Local URL (optional)", accent = cc.sky)
        Spacer(Modifier.height(10.dp))
        ExpressiveField(
            value = localUrl,
            onValueChange = onLocalUrlChange,
            placeholder = "http://homeassistant.local:8123",
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )

        Spacer(Modifier.height(20.dp))
        FieldLabel(iconName = "wifi", text = "Home Wi-Fi networks", accent = cc.mint)
        Spacer(Modifier.height(10.dp))
        if (ssids.isNotEmpty()) {
            SsidChipList(ssids = ssids, onRemove = onRemoveSsid)
            Spacer(Modifier.height(10.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ExpressiveField(
                    value = ssidInput,
                    onValueChange = onSsidInputChange,
                    placeholder = "MyHomeWiFi",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                )
            }
            RoundIconButton(
                iconName = null,
                enabled = ssidInput.isNotBlank(),
                onClick = onAddSsid,
            )
        }
        TextButton(
            onClick = {
                if (hasLocationPermission()) onSsidDetected()
                else permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                "Use my current Wi-Fi",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = cs.primary,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "On any listed network the app uses the local URL; elsewhere it uses the remote URL. Detection needs Location permission and Location Services on.",
            fontFamily = MontserratFamily,
            fontSize = 11.sp,
            color = cs.onSurfaceVariant,
            lineHeight = 15.sp,
        )
        if (!locationServicesEnabled) {
            Text(
                text = "Location Services are off — turn them on in system settings to detect the current Wi-Fi.",
                fontFamily = MontserratFamily,
                fontSize = 11.sp,
                color = cs.error,
                lineHeight = 15.sp,
            )
        }

        Spacer(Modifier.height(20.dp))
        FieldLabel(iconName = "lock", text = "Long-lived access token", accent = cc.coral)
        Spacer(Modifier.height(10.dp))
        ExpressiveField(
            value = token,
            onValueChange = onTokenChange,
            placeholder = "eyJhbGciOi…",
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "HA → Profile → Security → Long-lived access tokens",
            fontFamily = MontserratFamily,
            fontSize = 11.sp,
            color = cs.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))
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
    val label = when (currentPage) {
        0 -> "Get started"
        1 -> "Continue"
        else -> "Connect"
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            PrimaryCta(
                label = label,
                enabled = !isLast || connectEnabled,
                onClick = { if (isLast) onConnect() else onNext() },
            )
        }
        if (isLast) {
            TextButton(
                onClick = onTryDemo,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Skip — explore with sample data",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

// Large expressive pill CTA: bold label + trailing arrow, accent fill.
@Composable
private fun PrimaryCta(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = cs.primary,
            contentColor = cs.onPrimary,
        ),
        shape = CircleShape,
        contentPadding = PaddingValues(start = 28.dp, end = 22.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Text(
            label,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
        Spacer(Modifier.size(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
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
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { i ->
            val active = i == currentPage
            val width by animateDpAsState(
                targetValue = if (active) 28.dp else 8.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "dot_w",
            )
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .background(
                        if (active) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.30f),
                        CircleShape,
                    ),
            )
        }
    }
}

// ─── Expressive building blocks ─────────────────────────────────────────────────

// Consistent header for every step: accent kicker + serif-italic title + subtitle.
@Composable
private fun PageHeader(
    kicker: String,
    title: String,
    subtitle: String,
    titleSize: TextUnit = 42.sp,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = kicker.uppercase(),
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            color = cs.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            fontFamily = InstrumentSerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = titleSize,
            color = cs.onBackground,
            lineHeight = titleSize * 1.08f,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            fontFamily = MontserratFamily,
            fontSize = 14.sp,
            color = cs.onSurfaceVariant,
            lineHeight = 20.sp,
        )
    }
}

// Big morphing MaterialShapes focal point — the hero moment. Slowly morphs between two
// expressive polygons and rotates, with a glyph centered on top. Flat fill, no glow.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingHero(
    color: Color,
    glyphName: String,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf) }
    val transition = rememberInfiniteTransition(label = "hero")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "morph",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val path = morph.toPath(progress).asComposePath()
                    val b = path.getBounds()
                    val span = maxOf(b.width, b.height)
                    if (span <= 0f) return@drawBehind
                    val scale = size.minDimension / span
                    // Apply (to a point) in order: center→origin, rotate, scale, →box center.
                    // Compose Matrix post-multiplies, so calls are added in reverse of that order.
                    val matrix = Matrix().apply {
                        translate(size.width / 2f, size.height / 2f)
                        scale(scale, scale)
                        rotateZ(rotation)
                        translate(-(b.left + b.width / 2f), -(b.top + b.height / 2f))
                    }
                    path.transform(matrix)
                    drawPath(path, color)
                },
        )
        Icon(
            imageVector = haIconFor(glyphName),
            contentDescription = null,
            tint = cs.onPrimary,
            modifier = Modifier.size(52.dp),
        )
    }
}

// Value proposition row: an expressive shape icon-chip in a vibrant accent + title/body.
@Composable
private fun ValueProp(
    iconName: String,
    accent: Color,
    title: String,
    body: String,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ShapeIcon(iconName = iconName, accent = accent, size = 48.dp, iconSize = 24.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = cs.onSurface,
            )
            Text(
                text = body,
                fontFamily = MontserratFamily,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                lineHeight = 17.sp,
            )
        }
    }
}

// An icon clipped into the app's signature expressive blob shape, filled with an accent.
@Composable
private fun ShapeIcon(
    iconName: String,
    accent: Color,
    size: Dp,
    iconSize: Dp,
) {
    val blob = rememberTileBlobShape()
    Box(
        modifier = Modifier.size(size).clip(blob).background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = haIconFor(iconName),
            contentDescription = null,
            tint = glanceInkOn(accent),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun FieldLabel(iconName: String, text: String, accent: Color) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ShapeIcon(iconName = iconName, accent = accent, size = 30.dp, iconSize = 16.dp)
        Text(
            text = text,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = cs.onSurface,
        )
    }
}

// Rounded, filled input with a subtle resting outline and an animated accent focus ring.
@Composable
private fun ExpressiveField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardOptions,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val cs = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val borderColor by animateColorAsState(
        targetValue = if (focused) cs.primary else cs.outline,
        label = "field_border",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused) 1.5.dp else 1.dp,
        label = "field_border_w",
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        textStyle = TextStyle(fontFamily = MontserratFamily, fontSize = 15.sp, color = cs.onSurface),
        cursorBrush = SolidColor(cs.primary),
        singleLine = true,
        keyboardOptions = keyboard,
        keyboardActions = KeyboardActions(),
        visualTransformation = visualTransformation,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(cs.surfaceVariant)
                    .border(borderWidth, borderColor, shape)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        fontFamily = MontserratFamily,
                        fontSize = 15.sp,
                        color = cs.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                inner()
            }
        },
    )
}

// Circular accent button (used to commit a typed SSID).
@Composable
private fun RoundIconButton(
    iconName: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(cs.primary.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (iconName == null) Icons.Outlined.Add else haIconFor(iconName),
            contentDescription = "Add",
            tint = cs.onPrimary,
            modifier = Modifier.size(24.dp),
        )
    }
}

// One-shot staggered entrance wrapper: fade + slide-up driven by a shared transition state.
@Composable
private fun Staggered(
    state: MutableTransitionState<Boolean>,
    delayMillis: Int,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(durationMillis = 420, delayMillis = delayMillis)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 460, delayMillis = delayMillis),
                initialOffsetY = { it / 4 },
            ),
    ) {
        content()
    }
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
                    .clip(CircleShape)
                    .background(cs.primaryContainer)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = ssid,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
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
