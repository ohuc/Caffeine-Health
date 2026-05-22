package com.uc.homehealth.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Per-room widget storage for the "More" tab. One DataStore key per area_id; each value
// is a newline-separated list of encoded RoomWidget entries.
@Singleton
class WidgetPreferences @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private fun keyFor(areaId: String) = stringPreferencesKey("room_widgets_$areaId")

    fun widgetsForRoom(areaId: String): Flow<List<RoomWidget>> =
        dataStore.data.map { prefs -> decode(prefs[keyFor(areaId)]) }.distinctUntilChanged()

    suspend fun addWidget(areaId: String, widget: RoomWidget) = mutate(areaId) { current ->
        if (current.any { it.id == widget.id }) current else current + widget
    }

    suspend fun removeWidget(areaId: String, widgetId: String) = mutate(areaId) { current ->
        current.filterNot { it.id == widgetId }
    }

    // Overwrites the room's widget order. Used by the More-tab reorder UI on drag end.
    suspend fun setOrder(areaId: String, widgets: List<RoomWidget>) {
        dataStore.edit { prefs -> prefs[keyFor(areaId)] = encode(widgets) }
    }

    private suspend fun mutate(areaId: String, transform: (List<RoomWidget>) -> List<RoomWidget>) {
        dataStore.edit { prefs ->
            val next = transform(decode(prefs[keyFor(areaId)]))
            prefs[keyFor(areaId)] = encode(next)
        }
    }

    private fun encode(widgets: List<RoomWidget>): String =
        widgets.joinToString(LIST_SEP) { RoomWidget.encode(it) }

    private fun decode(raw: String?): List<RoomWidget> =
        raw?.split(LIST_SEP)?.filter { it.isNotBlank() }?.mapNotNull { RoomWidget.decode(it) }
            ?: emptyList()

    private companion object {
        const val LIST_SEP = "\n"
    }
}
