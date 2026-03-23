package com.vodr.tts

class OfflineTtsEngine(
    private val cacheStore: AudioCacheStore,
) : TtsEngine {

    override fun cachedAudioPath(chunkKey: String): String? = cacheStore.get(chunkKey)

    override fun cacheAudioPath(chunkKey: String, filePath: String) {
        cacheStore.put(chunkKey, filePath)
    }
}
