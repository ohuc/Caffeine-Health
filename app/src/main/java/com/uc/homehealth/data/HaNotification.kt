package com.uc.homehealth.data

data class HaNotification(
    val id: Int,
    val kind: String,   // motion | door | energy | climate | auto | update | light | scene
    val title: String,
    val body: String,
    val timestamp: Long, // epoch millis
)
