package com.vodr.tts

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Properties

class AudioCacheStore(private val cacheDir: File) {

    private val indexFile = File(cacheDir, INDEX_FILE_NAME)
    private val entries = linkedMapOf<String, String>()

    init {
        require((cacheDir.exists() && cacheDir.isDirectory) || cacheDir.mkdirs()) {
            "Unable to create cache directory: ${cacheDir.absolutePath}"
        }
        load()
    }

    @Synchronized
    fun get(chunkKey: String): String? = entries[chunkKey]

    @Synchronized
    fun put(chunkKey: String, filePath: String) {
        entries[chunkKey] = filePath
        persist()
    }

    private fun load() {
        if (!indexFile.exists()) {
            return
        }

        val properties = Properties()
        FileInputStream(indexFile).use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                properties.load(reader)
            }
        }

        for (name in properties.stringPropertyNames()) {
            entries[name] = properties.getProperty(name).orEmpty()
        }
    }

    private fun persist() {
        val properties = Properties()
        entries.forEach { (chunkKey, filePath) ->
            properties.setProperty(chunkKey, filePath)
        }

        FileOutputStream(indexFile).use { output ->
            OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
                properties.store(writer, null)
            }
        }
    }

    private companion object {
        const val INDEX_FILE_NAME = "audio-cache.properties"
    }
}
