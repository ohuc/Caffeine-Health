package com.uc.homehealth.data

// User-added widget on a room's "More" tab. The widget type drives the UI tile;
// the payload is type-specific (currently always an entity_id).
sealed interface RoomWidget {
    val id: String

    data class Switch(val entityId: String) : RoomWidget {
        override val id: String get() = "switch|$entityId"
    }

    data class Sensor(val entityId: String) : RoomWidget {
        override val id: String get() = "sensor|$entityId"
    }

    data class Camera(val entityId: String) : RoomWidget {
        override val id: String get() = "camera|$entityId"
    }

    data class Media(val entityId: String) : RoomWidget {
        override val id: String get() = "media|$entityId"
    }

    companion object {
        // Wire-format encoding for DataStore. Format: "<type>|<payload>".
        // areaId is stored separately (one key per area) so this only encodes the widget body.
        fun encode(widget: RoomWidget): String = when (widget) {
            is Switch -> "switch|${widget.entityId}"
            is Sensor -> "sensor|${widget.entityId}"
            is Camera -> "camera|${widget.entityId}"
            is Media -> "media|${widget.entityId}"
        }

        fun decode(raw: String): RoomWidget? {
            val parts = raw.split('|', limit = 2)
            if (parts.size != 2) return null
            return when (parts[0]) {
                "switch" -> Switch(parts[1])
                "sensor" -> Sensor(parts[1])
                "camera" -> Camera(parts[1])
                "media" -> Media(parts[1])
                else -> null
            }
        }
    }
}
