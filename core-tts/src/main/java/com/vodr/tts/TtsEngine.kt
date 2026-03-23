package com.vodr.tts

interface TtsEngine {
    fun cachedAudioPath(chunkKey: String): String?

    fun cacheAudioPath(chunkKey: String, filePath: String)
}
