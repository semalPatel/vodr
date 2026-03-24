package com.vodr.playback

internal object PlaybackEstimator {
    private const val BASE_CHARACTERS_PER_SECOND: Float = 13f

    fun estimatedDurationMs(
        text: String,
        playbackSpeed: Float,
    ): Long {
        val charsPerSecond = BASE_CHARACTERS_PER_SECOND * playbackSpeed.coerceAtLeast(0.1f)
        return ((text.length / charsPerSecond) * 1_000f).toLong().coerceAtLeast(1_000L)
    }

    fun estimatedCharacterOffset(
        text: String,
        positionMs: Long,
        playbackSpeed: Float,
    ): Int {
        val charsPerSecond = BASE_CHARACTERS_PER_SECOND * playbackSpeed.coerceAtLeast(0.1f)
        val offset = ((positionMs / 1_000f) * charsPerSecond).toInt()
        return offset.coerceIn(0, text.length)
    }
}
