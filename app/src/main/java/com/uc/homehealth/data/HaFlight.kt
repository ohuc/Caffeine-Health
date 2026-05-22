package com.uc.homehealth.data

// Mirrors the relevant subset of the FlightRadar24 integration's
// sensor.flightradar24_additional_tracked attributes.flights[] entries.
// Times are unix seconds (HA returns them that way). Distances are km, speeds kts,
// altitudes ft — matching what the prototype card shows verbatim.
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
)
