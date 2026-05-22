package com.uc.homehealth.data

data class HaFavorite(
    val id: String,
    val kind: String,      // climate | light | media | lock | energy
    val name: String,
    val value: Float?,
    val unit: String?,
    val state: String,
    val isOn: Boolean,
    val room: String,
)
