package com.uc.homehealth.data

import com.google.gson.JsonObject

// FlightRadar24 integration entity ids — shared by the WS repository and the
// background flight-schedule worker (which reaches them over REST).
object Fr24Entities {
    const val TRACKED = "sensor.flightradar24_additional_tracked"
    const val ADD = "text.flightradar24_add_to_track"
    const val REMOVE = "text.flightradar24_remove_from_track"
}

// Mirrors the relevant subset of the FlightRadar24 integration's
// sensor.flightradar24_additional_tracked attributes.flights[] entries.
// Times are unix seconds (HA returns them that way). Distances are km, speeds kts,
// altitudes ft — matching what the prototype card shows verbatim.
//
// NOTE on data availability: entries with tracked_type == "schedule" only carry
// id / flight_number / callsign — every time/airport field is null until FR24
// assigns the flight a live aircraft. Model absent data as null, never zeros.
data class HaFlight(
    val id: String,
    val flightNumber: String,
    val callsign: String?,
    val aircraftRegistration: String?,
    val aircraftPhotoSmall: String?,
    val aircraftPhotoMedium: String?,
    val aircraftPhotoLarge: String?,
    val aircraftModel: String?,
    val airline: String?,
    val airlineIata: String?,
    val originCity: String?,
    val originIata: String?,
    val originName: String?,
    val destinationCity: String?,
    val destinationIata: String?,
    val destinationName: String?,
    val scheduledDeparture: Long?,
    val scheduledArrival: Long?,
    val realDeparture: Long?,
    val realArrival: Long?,
    val estimatedDeparture: Long?,
    val estimatedArrival: Long?,
    val altitudeFt: Int,
    val groundSpeedKts: Int,
    val distanceKm: Float,
    val heading: Int,
    val onGround: Boolean,
    val trackedType: String?,
    // Origin airport's UTC offset in seconds — lets the flight-schedule verifier
    // compare a flight's departure against the calendar date printed on a ticket
    // (which is the departure-airport-local date, not the phone's).
    val originTzOffsetSec: Int? = null,
)

// Parses one attributes.flights[] element. Shared by HaHomeRepository (WS state
// cache) and FlightScheduleEngine (REST /api/states fetch from the background worker).
object HaFlightJson {
    fun parse(o: JsonObject): HaFlight? {
        val id = o["id"]?.takeIf { !it.isJsonNull }?.asString ?: return null
        val flightNumber = o["flight_number"]?.takeIf { !it.isJsonNull }?.asString ?: return null
        fun s(k: String) = o[k]?.takeIf { !it.isJsonNull }?.asString
        fun i(k: String) = o[k]?.takeIf { !it.isJsonNull }?.runCatching { asInt }?.getOrNull()
        fun l(k: String) = o[k]?.takeIf { !it.isJsonNull }?.runCatching { asLong }?.getOrNull()
        fun f(k: String) = o[k]?.takeIf { !it.isJsonNull }?.runCatching { asFloat }?.getOrNull()
        return HaFlight(
            id = id,
            flightNumber = flightNumber,
            callsign = s("callsign"),
            aircraftRegistration = s("aircraft_registration"),
            aircraftPhotoSmall = s("aircraft_photo_small"),
            aircraftPhotoMedium = s("aircraft_photo_medium"),
            aircraftPhotoLarge = s("aircraft_photo_large"),
            aircraftModel = s("aircraft_model"),
            airline = s("airline") ?: s("airline_short"),
            airlineIata = s("airline_iata"),
            originCity = s("airport_origin_city"),
            originIata = s("airport_origin_code_iata"),
            originName = s("airport_origin_name"),
            destinationCity = s("airport_destination_city"),
            destinationIata = s("airport_destination_code_iata"),
            destinationName = s("airport_destination_name"),
            scheduledDeparture = l("time_scheduled_departure"),
            scheduledArrival = l("time_scheduled_arrival"),
            realDeparture = l("time_real_departure"),
            realArrival = l("time_real_arrival"),
            estimatedDeparture = l("time_estimated_departure"),
            estimatedArrival = l("time_estimated_arrival"),
            altitudeFt = i("altitude") ?: 0,
            groundSpeedKts = i("ground_speed") ?: 0,
            distanceKm = f("distance") ?: 0f,
            heading = i("heading") ?: 0,
            onGround = (i("on_ground") ?: 0) != 0,
            trackedType = s("tracked_type"),
            originTzOffsetSec = i("airport_origin_timezone_offset"),
        )
    }
}
