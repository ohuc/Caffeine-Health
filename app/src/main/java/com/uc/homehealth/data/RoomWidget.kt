package com.uc.homehealth.data

// PTZ (pan/tilt) entity bindings for a camera widget. Each direction is the
// entity_id of a *pressable* entity (button/input_button/switch/script/scene)
// that, when pressed, nudges the camera that way. Blank = unassigned for that
// direction (its arrow simply doesn't render). A non-null CameraPtz means the
// user answered "Yes" to PTZ controls; null means PTZ is off for this camera.
data class CameraPtz(
    val left: String = "",
    val right: String = "",
    val up: String = "",
    val down: String = "",
) {
    val hasAny: Boolean get() = left.isNotBlank() || right.isNotBlank() || up.isNotBlank() || down.isNotBlank()
}

// Which section of a room sheet a widget lives in. A widget added from the Media tab
// belongs to MEDIA and shows ONLY there; one added from the More catalog belongs to
// MORE and shows ONLY in More; a climate card added from the Climate tab belongs to
// CLIMATE. MORE is the default and is omitted from the wire format, so widgets stored
// before sections existed decode to MORE (preserving "added from More = shown in More").
enum class WidgetSection {
    MORE, MEDIA, CLIMATE;

    companion object {
        fun fromToken(token: String?): WidgetSection =
            entries.firstOrNull { it.name.equals(token, ignoreCase = true) } ?: MORE
    }
}

// User-added widget on a room. The widget type drives the UI tile; the payload is
// type-specific (currently always an entity_id). [section] scopes which tab the widget
// appears in (see [WidgetSection]).
sealed interface RoomWidget {
    val id: String

    // Section this widget was added to. Most types only ever live in More, so the
    // default is MORE; Media/Climate override it to support per-section placement and
    // fold it into [id] so the same entity can sit in two sections independently.
    val section: WidgetSection get() = WidgetSection.MORE

    data class Switch(val entityId: String) : RoomWidget {
        override val id: String get() = "switch|$entityId"
    }

    data class Sensor(val entityId: String) : RoomWidget {
        override val id: String get() = "sensor|$entityId"
    }

    // PTZ bindings are *not* part of [id] — the id stays "camera|<entityId>" so
    // reorder/remove/update-by-id keep working when the user only edits PTZ.
    data class Camera(val entityId: String, val ptz: CameraPtz? = null) : RoomWidget {
        override val id: String get() = "camera|$entityId"
    }

    data class Media(
        val entityId: String,
        override val section: WidgetSection = WidgetSection.MORE,
    ) : RoomWidget {
        override val id: String get() = "media|${section.name}|$entityId"
    }

    // A climate (thermostat / AC) entity rendered as a ClimateCard. Added either from
    // the Climate tab (section = CLIMATE) or the More catalog (section = MORE).
    data class Climate(
        val entityId: String,
        override val section: WidgetSection = WidgetSection.MORE,
    ) : RoomWidget {
        override val id: String get() = "climate|${section.name}|$entityId"
    }

    // A person.* (or other GPS-bearing) entity rendered as a map card. The map only
    // appears when the entity is actually reporting coordinates (see HaPersonLocation).
    data class Location(val entityId: String) : RoomWidget {
        override val id: String get() = "location|$entityId"
    }

    // PM2.5 air-quality card. [entityId] is the base PM2.5 sensor (µg/m³); the three
    // optional duration sensors feed the "time in each band" visualization. Modelled on
    // the IKEA VINDRIKTNING's clean/moderate/poor split. Only [entityId] is part of [id]
    // so editing the duration bindings keeps reorder/remove-by-id stable.
    data class AirQuality(
        val entityId: String,
        val cleanDurationId: String = "",
        val moderateDurationId: String = "",
        val poorDurationId: String = "",
    ) : RoomWidget {
        override val id: String get() = "airquality|$entityId"
    }

    companion object {
        // Wire-format encoding for DataStore. Format: "<type>|<payload>".
        // areaId is stored separately (one key per area) so this only encodes the widget body.
        // Camera optionally appends a "|ptz=L,R,U,D" segment; Media/Climate optionally append
        // a "|sec=<section>" segment (only when section != MORE). Entity ids never contain
        // '|' or ',', so this stays unambiguous and is backward-compatible with the old
        // rows (no extra segment), which decode to ptz = null / section = MORE.
        fun encode(widget: RoomWidget): String = when (widget) {
            is Switch -> "switch|${widget.entityId}"
            is Sensor -> "sensor|${widget.entityId}"
            is Camera -> buildString {
                append("camera|").append(widget.entityId)
                widget.ptz?.let { p ->
                    append("|ptz=").append(p.left).append(',').append(p.right)
                        .append(',').append(p.up).append(',').append(p.down)
                }
            }
            is Media -> "media|${widget.entityId}".withSection(widget.section)
            is Climate -> "climate|${widget.entityId}".withSection(widget.section)
            is Location -> "location|${widget.entityId}"
            // "airquality|<base>|<clean>|<moderate>|<poor>" — duration ids may be blank.
            is AirQuality -> "airquality|${widget.entityId}|${widget.cleanDurationId}|" +
                "${widget.moderateDurationId}|${widget.poorDurationId}"
        }

        private fun String.withSection(section: WidgetSection): String =
            if (section == WidgetSection.MORE) this else "$this|sec=${section.name.lowercase()}"

        fun decode(raw: String): RoomWidget? {
            val parts = raw.split('|', limit = 2)
            if (parts.size != 2) return null
            return when (parts[0]) {
                "switch" -> Switch(parts[1])
                "sensor" -> Sensor(parts[1])
                "camera" -> decodeCamera(parts[1])
                "media" -> decodeSectioned(parts[1]) { id, sec -> Media(id, sec) }
                "climate" -> decodeSectioned(parts[1]) { id, sec -> Climate(id, sec) }
                "location" -> Location(parts[1])
                "airquality" -> {
                    val segs = parts[1].split('|')
                    AirQuality(
                        entityId = segs[0],
                        cleanDurationId = segs.getOrElse(1) { "" },
                        moderateDurationId = segs.getOrElse(2) { "" },
                        poorDurationId = segs.getOrElse(3) { "" },
                    )
                }
                else -> null
            }
        }

        // body = "<entityId>" or "<entityId>|sec=<section>"
        private fun decodeSectioned(body: String, build: (String, WidgetSection) -> RoomWidget): RoomWidget {
            val segs = body.split('|')
            val entityId = segs[0]
            val section = segs.getOrNull(1)?.removePrefix("sec=")?.let { WidgetSection.fromToken(it) }
                ?: WidgetSection.MORE
            return build(entityId, section)
        }

        private fun decodeCamera(body: String): Camera {
            // body = "<entityId>" or "<entityId>|ptz=L,R,U,D"
            val segs = body.split('|')
            val entityId = segs[0]
            val ptz = segs.getOrNull(1)
                ?.removePrefix("ptz=")
                ?.split(',')
                ?.let { d -> CameraPtz(d.getOrElse(0) { "" }, d.getOrElse(1) { "" }, d.getOrElse(2) { "" }, d.getOrElse(3) { "" }) }
            return Camera(entityId, ptz)
        }
    }
}
