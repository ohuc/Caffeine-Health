package com.uc.homehealth.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.HaTtsEngine
import com.uc.homehealth.data.HaTtsVoice
import com.uc.homehealth.data.TtsTarget
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.PillShape
import dev.chrisbanes.haze.HazeState

// Announce / text-to-speech composer. Opened from a media player card. For Echo devices
// it offers a Speak/Announce choice (Amazon's own voice); for standard players it speaks
// through the saved TTS engine + voice, with an optional inline override picker.
@Composable
fun TtsComposerSheet(
    visible: Boolean,
    target: TtsTarget?,
    engines: List<HaTtsEngine>,
    defaultEngineId: String,
    defaultVoiceId: String,
    defaultLanguage: String,
    sending: Boolean,
    hazeState: HazeState? = null,
    loadVoices: suspend (engineId: String, language: String) -> List<HaTtsVoice>,
    onSend: (message: String, announce: Boolean, engineId: String?, voiceId: String?, language: String?) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    // Optional one-tap message (Pulse's spoken "Home status"); fills the field when tapped.
    suggestedMessage: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    val isEcho = target?.isEcho == true

    var message by remember { mutableStateOf("") }
    // Echo only: Announce (chime + SSML) vs Speak (plain). Defaults to Announce — the
    // more recognizable Echo behaviour.
    var announce by remember { mutableStateOf(true) }
    // Inline engine/voice override (standard players). Null engine = use the saved default.
    var overrideOpen by remember { mutableStateOf(false) }
    var overrideEngineId by remember { mutableStateOf<String?>(null) }
    var overrideVoiceId by remember { mutableStateOf<String?>(null) }

    // Reset transient state every time the sheet (re)opens for a new player.
    LaunchedEffect(visible, target?.entityId) {
        if (visible) {
            message = ""
            announce = true
            overrideOpen = false
            overrideEngineId = null
            overrideVoiceId = null
        }
    }

    val effectiveEngineId = overrideEngineId ?: defaultEngineId
    val canSend = message.isNotBlank() && !sending && (isEcho || effectiveEngineId.isNotBlank())

    AppBottomSheet(visible = visible, onDismiss = onDismiss, hazeState = hazeState, maxHeightFraction = 0.9f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 16.dp),
        ) {
            Text(
                text = "Speak to ${target?.friendlyName ?: "player"}",
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 30.sp,
                lineHeight = 32.sp,
                color = cs.onSurface,
            )
            Text(
                text = if (isEcho) "Amazon Echo · speaks in Alexa's voice"
                else "Plays a spoken message on this media player",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.size(16.dp))
            MessageField(value = message, onValueChange = { message = it })

            if (suggestedMessage != null) {
                Spacer(Modifier.size(10.dp))
                Tap(onClick = { haptic.tick(); message = suggestedMessage }) {
                    Row(
                        modifier = Modifier
                            .background(cs.surfaceContainerHigh, PillShape)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = haIconFor("pulse"),
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.size(7.dp))
                        Text(
                            text = "Home status",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = cs.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.size(14.dp))
            if (isEcho) {
                EchoModeToggle(announce = announce, onChange = { announce = it })
            } else {
                StandardVoiceSection(
                    engines = engines,
                    defaultEngineId = defaultEngineId,
                    defaultVoiceId = defaultVoiceId,
                    defaultLanguage = defaultLanguage,
                    overrideOpen = overrideOpen,
                    overrideEngineId = overrideEngineId,
                    overrideVoiceId = overrideVoiceId,
                    onToggleOverride = { overrideOpen = !overrideOpen },
                    onPickEngine = { id -> overrideEngineId = id; overrideVoiceId = null },
                    onPickVoice = { vid -> overrideVoiceId = vid },
                    onClearOverride = { overrideEngineId = null; overrideVoiceId = null },
                    onOpenSettings = onOpenSettings,
                    loadVoices = loadVoices,
                )
            }

            Spacer(Modifier.size(18.dp))
            SendButton(
                enabled = canSend,
                sending = sending,
                onClick = {
                    haptic.confirm()
                    val (e, v, l) = if (isEcho) {
                        Triple<String?, String?, String?>(null, null, null)
                    } else if (overrideEngineId != null) {
                        // Override active: resolve the language from the picked engine.
                        val eng = engines.firstOrNull { it.engineId == overrideEngineId }
                        val lang = eng?.let { resolveLanguage(it, defaultLanguage) }
                        Triple(overrideEngineId, overrideVoiceId, lang)
                    } else {
                        Triple<String?, String?, String?>(null, null, null) // repo uses saved defaults
                    }
                    onSend(message.trim(), announce, e, v, l)
                },
            )
        }
    }
}

private enum class VoicePhase { LOADING, NONE, LIST }

// Prefer the saved default language if the engine supports it, else its first language.
private fun resolveLanguage(engine: HaTtsEngine, preferred: String): String =
    when {
        preferred.isNotBlank() && preferred in engine.supportedLanguages -> preferred
        else -> engine.supportedLanguages.firstOrNull() ?: preferred
    }

@Composable
private fun MessageField(value: String, onValueChange: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(fontFamily = MontserratFamily, fontSize = 15.sp, color = cs.onSurface),
        cursorBrush = SolidColor(cs.primary),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp)
                    .background(cs.surfaceVariant, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Type something to say…",
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

@Composable
private fun EchoModeToggle(announce: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column {
        Text(
            text = "DELIVERY",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SegmentPill(
                label = "Announce",
                caption = "Chime, then speak",
                selected = announce,
                onClick = { onChange(true) },
                modifier = Modifier.weight(1f),
            )
            SegmentPill(
                label = "Speak",
                caption = "Plain message",
                selected = !announce,
                onClick = { onChange(false) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SegmentPill(
    label: String,
    caption: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val container = if (selected) cs.primary else cs.surfaceVariant
    val content = if (selected) cs.onPrimary else cs.onSurface
    Tap(onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(container, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = content)
            Text(
                caption,
                fontFamily = MontserratFamily,
                fontSize = 10.sp,
                color = if (selected) cs.onPrimary.copy(alpha = 0.8f) else cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun StandardVoiceSection(
    engines: List<HaTtsEngine>,
    defaultEngineId: String,
    defaultVoiceId: String,
    defaultLanguage: String,
    overrideOpen: Boolean,
    overrideEngineId: String?,
    overrideVoiceId: String?,
    onToggleOverride: () -> Unit,
    onPickEngine: (String) -> Unit,
    onPickVoice: (String) -> Unit,
    onClearOverride: () -> Unit,
    onOpenSettings: () -> Unit,
    loadVoices: suspend (engineId: String, language: String) -> List<HaTtsVoice>,
) {
    val cs = MaterialTheme.colorScheme
    val defaultEngineName = engines.firstOrNull { it.engineId == defaultEngineId }?.name
        ?: defaultEngineId.removePrefix("tts.")

    // Header row: current voice summary + a "Change" toggle.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (defaultEngineId.isBlank() && overrideEngineId == null) {
                Text("No voice set", fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.onSurface)
                Text(
                    "Pick an engine below or set a default in Settings → Voice & TTS",
                    fontFamily = MontserratFamily, fontSize = 11.sp, color = cs.onSurfaceVariant, lineHeight = 14.sp,
                )
            } else {
                val activeEngine = overrideEngineId ?: defaultEngineId
                val activeEngineName = engines.firstOrNull { it.engineId == activeEngine }?.name
                    ?: activeEngine.removePrefix("tts.")
                val activeVoice = (overrideVoiceId ?: defaultVoiceId).ifBlank { "default voice" }
                Text(
                    if (overrideEngineId != null) "$activeEngineName · $activeVoice" else "$defaultEngineName · ${defaultVoiceId.ifBlank { "default voice" }}",
                    fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.onSurface,
                )
                Text(
                    if (overrideEngineId != null) "Just for this message" else "Saved default",
                    fontFamily = MontserratFamily, fontSize = 11.sp, color = cs.onSurfaceVariant,
                )
            }
        }
        Tap(onClick = onToggleOverride) {
            Text(
                text = if (overrideOpen) "Done" else "Change",
                fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }

    // Expand/collapse the override panel instead of snapping it in/out.
    AnimatedVisibility(
        visible = overrideOpen,
        enter = fadeIn(tween(200)) + expandVertically(tween(240, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(200, easing = FastOutSlowInEasing)),
    ) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
            if (engines.isEmpty()) {
                Text(
                    "No TTS engines available. Connect to Home Assistant, or open Settings → Voice & TTS.",
                    fontFamily = MontserratFamily, fontSize = 12.sp, color = cs.onSurfaceVariant, lineHeight = 16.sp,
                )
                Spacer(Modifier.size(8.dp))
                Tap(onClick = onOpenSettings) {
                    Text("Open Voice & TTS settings", fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.primary)
                }
            } else {
                Text("ENGINE", fontFamily = MontserratFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 1.2.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.size(6.dp))
                val selectedEngine = overrideEngineId ?: defaultEngineId
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    engines.forEach { engine ->
                        OptionRow(
                            label = engine.name,
                            sublabel = engine.engineId,
                            selected = engine.engineId == selectedEngine,
                            onClick = { onPickEngine(engine.engineId) },
                        )
                    }
                }

                // Voice list for the selected engine. Reloads whenever the engine changes.
                val activeEngine = engines.firstOrNull { it.engineId == selectedEngine }
                if (activeEngine != null) {
                    val language = resolveLanguage(activeEngine, defaultLanguage)
                    var voices by remember { mutableStateOf<List<HaTtsVoice>>(emptyList()) }
                    var loading by remember { mutableStateOf(false) }
                    LaunchedEffect(activeEngine.engineId, language) {
                        loading = true
                        voices = loadVoices(activeEngine.engineId, language)
                        loading = false
                    }
                    Spacer(Modifier.size(12.dp))
                    Text("VOICE", fontFamily = MontserratFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 1.2.sp, color = cs.onSurfaceVariant)
                    Spacer(Modifier.size(6.dp))
                    val voicePhase = when {
                        loading -> VoicePhase.LOADING
                        voices.isEmpty() -> VoicePhase.NONE
                        else -> VoicePhase.LIST
                    }
                    // Crossfade + grow as the async voice list resolves, so it eases in
                    // instead of snapping from "Loading…" to a full list.
                    AnimatedContent(
                        targetState = voicePhase,
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
                        },
                        label = "voice_phase",
                    ) { phase ->
                        when (phase) {
                            VoicePhase.LOADING -> Text(
                                "Loading voices…",
                                fontFamily = MontserratFamily, fontSize = 12.sp, color = cs.onSurfaceVariant,
                            )
                            VoicePhase.NONE -> Text(
                                "This engine has no selectable voices.",
                                fontFamily = MontserratFamily, fontSize = 12.sp, color = cs.onSurfaceVariant,
                            )
                            VoicePhase.LIST -> Column(
                                modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                voices.forEach { voice ->
                                    OptionRow(
                                        label = voice.name,
                                        sublabel = null,
                                        selected = voice.voiceId == overrideVoiceId,
                                        onClick = { onPickVoice(voice.voiceId) },
                                    )
                                }
                            }
                        }
                    }
                }

                if (overrideEngineId != null) {
                    Spacer(Modifier.size(10.dp))
                    Tap(onClick = onClearOverride) {
                        Text("Use saved default instead", fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    sublabel: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val container = if (selected) cs.primary.copy(alpha = 0.16f) else cs.surfaceVariant.copy(alpha = 0.5f)
    Tap(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(container, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.onSurface)
                if (sublabel != null) {
                    Text(sublabel, fontFamily = MontserratFamily, fontSize = 10.sp, color = cs.onSurfaceVariant)
                }
            }
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, sending: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val container = if (enabled) cs.primary else cs.surfaceVariant
    val content = if (enabled) cs.onPrimary else cs.onSurfaceVariant.copy(alpha = 0.6f)
    Tap(onClick = { if (enabled) onClick() }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(container, RoundedCornerShape(18.dp))
                .padding(vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (sending) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = content)
            } else {
                Icon(Icons.Outlined.Campaign, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = if (sending) "Sending…" else "Send",
                fontFamily = MontserratFamily, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = content,
            )
        }
    }
}
