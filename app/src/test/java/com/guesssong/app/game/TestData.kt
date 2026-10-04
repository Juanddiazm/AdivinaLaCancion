package com.guesssong.app.game

import com.guesssong.app.model.Track

fun track(id: Long, title: String = "Song $id", artist: String = "Artist $id", preview: String = "https://x/$id.mp3") =
    Track(id = id, title = title, artist = artist, previewUrl = preview, coverUrl = null)

fun tracks(count: Int) = (1L..count).map { track(it) }
