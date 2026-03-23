package com.vodr.tts

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class AudioCacheStoreTest {

    @Test
    fun persistsChunkKeyToFilePathIndex() {
        val cacheDir = Files.createTempDirectory("audio-cache-store-test").toFile()
        try {
            val store = AudioCacheStore(cacheDir)

            store.put("chunk-1", "/tmp/audio/chunk-1.m4a")

            val reloaded = AudioCacheStore(cacheDir)
            assertEquals("/tmp/audio/chunk-1.m4a", reloaded.get("chunk-1"))
        } finally {
            cacheDir.deleteRecursively()
        }
    }
}
