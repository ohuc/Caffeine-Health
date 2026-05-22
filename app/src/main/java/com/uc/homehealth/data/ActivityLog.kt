package com.uc.homehealth.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory log of actions the user performed via this app while connected to HA.
 * Lives for the process lifetime — cleared on cold start.
 */
@Singleton
class ActivityLog @Inject constructor() {

    private val nextId = AtomicInteger(1)
    private val _events = MutableStateFlow<List<HaNotification>>(emptyList())
    val events: StateFlow<List<HaNotification>> = _events.asStateFlow()

    fun record(kind: String, title: String, body: String) {
        val event = HaNotification(
            id = nextId.getAndIncrement(),
            kind = kind,
            title = title,
            body = body,
            timestamp = System.currentTimeMillis(),
        )
        _events.update { list -> (listOf(event) + list).take(MAX_EVENTS) }
    }

    fun clear() {
        _events.value = emptyList()
    }

    private fun MutableStateFlow<List<HaNotification>>.update(transform: (List<HaNotification>) -> List<HaNotification>) {
        while (true) {
            val current = value
            val next = transform(current)
            if (compareAndSet(current, next)) return
        }
    }

    companion object {
        private const val MAX_EVENTS = 200
    }
}
