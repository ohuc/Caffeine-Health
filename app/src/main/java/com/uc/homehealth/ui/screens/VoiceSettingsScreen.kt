package com.uc.homehealth.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uc.homehealth.data.HaTtsEngine
import com.uc.homehealth.data.HaTtsVoice
import com.uc.homehealth.ui.components.SettingsPageScaffold
import com.uc.homehealth.ui.components.SkeletonBox
import com.uc.homehealth.ui.components.Tap
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.Spacing
import com.uc.homehealth.ui.viewmodel.SettingsViewModel
import java.util.Locale

// Settings → Voice & TTS. Picks the default TTS engine + language + voice used by the
// announce composer for standard (non-Echo) media players. Engines/voices come straight
// from Home Assistant (`tts/engine/list`, `tts/engine/voices`); demo mode shows samples.
//
// Layout language matches the rest of the app: engines are a grouped list (22/6 corner
// pattern, like Pulse/Activity rows); language & voice are compact selectable pills.
// While the one-shot WS calls resolve, the page shows shimmer skeletons — never the
// "no engines" empty state (that's reserved for a *loaded* empty list).

private enum class VoicePagePhase { LOADING, NONE, LIST }

@Composable
fun VoiceSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val engines by viewModel.ttsEngines.collectAsStateWithLifecycle()
    val defaults by viewModel.ttsDefaults.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadTtsEngines() }

    SettingsPageScaffold(
        title = "Voice & TTS",
        subtitle = "Default engine and voice for announcements",
        onBack = onBack,
    ) {
        val pagePhase = when {
            engines == null -> VoicePagePhase.LOADING
            engines.orEmpty().isEmpty() -> VoicePagePhase.NONE
            else -> VoicePagePhase.LIST
        }
        AnimatedContent(
            targetState = pagePhase,
            modifier = Modifier.padding(horizontal = Spacing.ml),
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
            },
            label = "voice_page_phase",
        ) { phase ->
            when (phase) {
                VoicePagePhase.LOADING -> VoiceSettingsSkeleton()
                VoicePagePhase.NONE -> Text(
                    text = "No TTS engines found. Connect to Home Assistant and install a " +
                        "text-to-speech integration (e.g. Home Assistant Cloud, Piper, ElevenLabs).",
                    fontFamily = MontserratFamily,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                VoicePagePhase.LIST -> {
                    val engineList = engines.orEmpty()
                    val selectedEngine = engineList.firstOrNull { it.engineId == defaults.engineId }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("ENGINE")
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            engineList.forEachIndexed { index, engine ->
                                OptionRow(
                                    label = engine.name,
                                    sublabel = engine.engineId,
                                    selected = engine.engineId == defaults.engineId,
                                    shape = groupedRowShape(index, engineList.size),
                                    onClick = {
                                        viewModel.setTtsEngine(
                                            engine.engineId,
                                            defaultLanguageFor(engine, defaults.language),
                                        )
                                    },
                                )
                            }
                        }

                        // Language + voice for the selected engine crossfade/grow in when an
                        // engine is first picked (and re-animate when switching engines). Keyed
                        // on engine id so picking a language/voice updates in place.
                        AnimatedContent(
                            targetState = selectedEngine,
                            transitionSpec = {
                                fadeIn(tween(220)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
                            },
                            contentKey = { it?.engineId },
                            label = "engine_detail",
                        ) { engine ->
                            if (engine == null) {
                                Spacer(Modifier.size(0.dp))
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val language = defaults.language.ifBlank { defaultLanguageFor(engine, "") }
                                    if (engine.supportedLanguages.size > 1) {
                                        Spacer(Modifier.size(6.dp))
                                        SectionLabel("LANGUAGE")
                                        PillFlowRow {
                                            engine.supportedLanguages.forEach { lang ->
                                                ChoicePill(
                                                    label = languageDisplayName(lang),
                                                    selected = lang == language,
                                                    onClick = { viewModel.setTtsLanguage(lang) },
                                                )
                                            }
                                        }
                                    }
                                    VoiceSection(
                                        viewModel = viewModel,
                                        engineId = engine.engineId,
                                        language = language,
                                        selectedVoiceId = defaults.voiceId,
                                        onPickVoice = { viewModel.setTtsVoice(it) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceSection(
    viewModel: SettingsViewModel,
    engineId: String,
    language: String,
    selectedVoiceId: String,
    onPickVoice: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    // null = request in flight (skeleton); [] = engine really has no selectable voices.
    var voices by remember(engineId, language) { mutableStateOf<List<HaTtsVoice>?>(null) }
    LaunchedEffect(engineId, language) {
        voices = viewModel.loadTtsVoices(engineId, language)
    }

    Spacer(Modifier.size(6.dp))
    SectionLabel("VOICE")
    val voicePhase = when {
        voices == null -> VoicePagePhase.LOADING
        voices.orEmpty().isEmpty() -> VoicePagePhase.NONE
        else -> VoicePagePhase.LIST
    }
    // Crossfade + grow as the async voice list resolves instead of snapping in.
    AnimatedContent(
        targetState = voicePhase,
        transitionSpec = {
            fadeIn(tween(220)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
        },
        label = "settings_voice_phase",
    ) { phase ->
        when (phase) {
            VoicePagePhase.LOADING -> PillSkeletonRow()
            VoicePagePhase.NONE -> Text(
                "This engine speaks with a single built-in voice.",
                fontFamily = MontserratFamily, fontSize = 12.sp, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            VoicePagePhase.LIST -> PillFlowRow {
                // "Default voice" clears the saved voice id so HA picks the engine default.
                ChoicePill(
                    label = "Default voice",
                    selected = selectedVoiceId.isBlank(),
                    onClick = { onPickVoice("") },
                )
                voices.orEmpty().forEach { voice ->
                    ChoicePill(
                        label = voice.name,
                        selected = voice.voiceId == selectedVoiceId,
                        onClick = { onPickVoice(voice.voiceId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

// Grouped-list shape: large corners on the group's outer edges, small where rows meet —
// the same expressive grouped-list pattern Pulse and the Activity feed use.
private val GroupCornerLarge = 22.dp
private val GroupCornerSmall = 6.dp

private fun groupedRowShape(index: Int, count: Int): Shape = when {
    count == 1 -> RoundedCornerShape(GroupCornerLarge)
    index == 0 -> RoundedCornerShape(
        topStart = GroupCornerLarge, topEnd = GroupCornerLarge,
        bottomStart = GroupCornerSmall, bottomEnd = GroupCornerSmall,
    )
    index == count - 1 -> RoundedCornerShape(
        topStart = GroupCornerSmall, topEnd = GroupCornerSmall,
        bottomStart = GroupCornerLarge, bottomEnd = GroupCornerLarge,
    )
    else -> RoundedCornerShape(GroupCornerSmall)
}

@Composable
private fun OptionRow(
    label: String,
    sublabel: String?,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    // Selection uses the paired container/on-container roles (not a hand-rolled alpha).
    val container by animateColorAsState(
        targetValue = if (selected) cs.primaryContainer else cs.surfaceContainerHigh,
        label = "voice_option_container",
    )
    val content = if (selected) cs.onPrimaryContainer else cs.onSurface
    val supporting = if (selected) cs.onPrimaryContainer.copy(alpha = 0.7f) else cs.onSurfaceVariant
    Tap(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(container, shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = content)
                if (sublabel != null) {
                    Text(sublabel, fontFamily = MontserratFamily, fontSize = 11.sp, color = supporting, modifier = Modifier.padding(top = 1.dp))
                }
            }
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = cs.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ─── Pills (language & voice choices) ────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PillFlowRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun ChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) cs.primary else cs.surfaceContainerHigh,
        label = "voice_pill_container",
    )
    val content = if (selected) cs.onPrimary else cs.onSurface
    Tap(onClick = onClick) {
        Row(
            modifier = Modifier
                .background(container, CircleShape)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            }
            Text(
                text = label,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = content,
            )
        }
    }
}

// ─── Skeletons (shown while the WS calls resolve) ────────────────────────────

@Composable
private fun VoiceSettingsSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("ENGINE")
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { index ->
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth().height(62.dp),
                    shape = groupedRowShape(index, 3),
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        SectionLabel("VOICE")
        PillSkeletonRow()
    }
}

@Composable
private fun PillSkeletonRow() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        listOf(104.dp, 76.dp, 88.dp).forEach { width ->
            SkeletonBox(modifier = Modifier.width(width).height(33.dp), shape = CircleShape)
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

// "en-US"/"en_US" → "English (United States)"; falls back to the raw tag when the
// platform can't resolve it (never shows a blank pill).
private fun languageDisplayName(tag: String): String {
    val locale = Locale.forLanguageTag(tag.replace('_', '-'))
    val name = locale.displayName
    return if (name.isBlank() || locale.language.isBlank()) tag
    else name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

// Pick a sensible default language for an engine: keep the saved one if still supported,
// else match the device locale (exact tag, then language prefix), else the engine's first.
private fun defaultLanguageFor(engine: HaTtsEngine, current: String): String {
    if (current.isNotBlank() && current in engine.supportedLanguages) return current
    val langs = engine.supportedLanguages
    if (langs.isEmpty()) return current
    val locale = Locale.getDefault()
    val tag = locale.toLanguageTag()                       // e.g. "en-US"
    langs.firstOrNull { it.equals(tag, ignoreCase = true) }?.let { return it }
    val prefix = locale.language                            // e.g. "en"
    langs.firstOrNull { it.substringBefore('-').equals(prefix, ignoreCase = true) }?.let { return it }
    return langs.first()
}
