package com.uc.homehealth.data

// A Home Assistant TTS engine entity (the `tts.*` entities), surfaced for the
// Settings voice picker and the inline override in the announce composer.
// `engineId` is the entity_id used as the target of the `tts.speak` action.
data class HaTtsEngine(
    val engineId: String,                 // e.g. "tts.elevenlabs"
    val name: String,                     // friendly label, e.g. "ElevenLabs"
    val supportedLanguages: List<String>, // e.g. ["en-US", "en-GB"]
    val deprecated: Boolean = false,
)

// One selectable voice for an engine+language, from the `tts/engine/voices`
// WebSocket command. `voiceId` is what goes in `options.voice` of `tts.speak`.
// Engines without per-voice selection (e.g. Google Translate) return an empty list.
data class HaTtsVoice(
    val voiceId: String,
    val name: String,
)

// How a media_player accepts spoken text. Amazon Echo devices (added via either the
// "Alexa Media Player" custom integration or the official "Alexa Devices" integration)
// speak in Amazon's own voice through a separate notify flow with a Speak/Announce
// choice — they must NOT go through `tts.speak`. Everything else uses the configured
// HA TTS engine + voice. The repository keys all of this off the media_player entity's
// registry `platform`; the UI only needs [isEcho] to swap the composer's controls.
data class TtsTarget(
    val entityId: String,
    val friendlyName: String,
    val isEcho: Boolean,
)
