package com.guesssong.app.model

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val previewUrl: String,
    val coverUrl: String?,
)
