package com.vodr.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class VoicePackStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    @Synchronized
    fun listInstalled(): List<NarratorVoicePack> {
        return loadInstalled()
    }

    @Synchronized
    fun get(voicePackId: String): NarratorVoicePack? {
        return loadInstalled().firstOrNull { it.id == voicePackId }
    }

    @Synchronized
    fun installStarterPack(): NarratorVoicePack {
        val pack = NarratorVoicePack(
            id = STARTER_PACK_ID,
            displayName = "Studio Warm",
            description = "A polished offline narrator profile tuned for long-form listening.",
            languageTag = "en-US",
            speechRateMultiplier = 0.96f,
            pitchMultiplier = 0.98f,
            pauseMultiplier = 1.15f,
            expressiveness = 0.78f,
            providerLabel = "Offline Voice Pack",
            version = 1,
        )
        persist(merge(loadInstalled(), pack))
        return pack
    }

    @Synchronized
    fun installFromUrl(url: String): NarratorVoicePack {
        val trimmedUrl = url.trim()
        require(trimmedUrl.isNotBlank()) { "Voice pack URL is required." }
        val responseBody = openUrl(trimmedUrl).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val pack = parseVoicePack(
            json = JSONObject(responseBody),
            fallbackProviderLabel = "Downloaded Voice Pack",
        )
        persist(merge(loadInstalled(), pack))
        return pack
    }

    @Synchronized
    fun remove(voicePackId: String) {
        persist(loadInstalled().filterNot { it.id == voicePackId })
    }

    private fun openUrl(url: String) = ((URI(url).toURL().openConnection()) as HttpURLConnection).run {
        requestMethod = "GET"
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        if (responseCode !in 200..299) {
            val errorBody = errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            throw IllegalStateException(
                "Unable to download voice pack (HTTP $responseCode${errorBody.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""})",
            )
        }
        inputStream
    }

    private fun merge(
        existing: List<NarratorVoicePack>,
        pack: NarratorVoicePack,
    ): List<NarratorVoicePack> {
        return buildList {
            add(pack.copy(installedAtEpochMs = System.currentTimeMillis()))
            addAll(existing.filterNot { it.id == pack.id })
        }
    }

    private fun loadInstalled(): List<NarratorVoicePack> {
        if (!manifestFile.exists()) {
            return emptyList()
        }
        val json = runCatching { JSONObject(manifestFile.readText()) }.getOrNull() ?: return emptyList()
        val items = json.optJSONArray(KEY_VOICE_PACKS) ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                add(
                    parseVoicePack(
                        json = item,
                        fallbackProviderLabel = "Offline Voice Pack",
                    ),
                )
            }
        }
    }

    private fun persist(packs: List<NarratorVoicePack>) {
        val payload = JSONObject().put(
            KEY_VOICE_PACKS,
            JSONArray().apply {
                packs.forEach { pack ->
                    put(
                        JSONObject()
                            .put("id", pack.id)
                            .put("displayName", pack.displayName)
                            .put("description", pack.description)
                            .put("languageTag", pack.languageTag)
                            .put("systemVoiceName", pack.systemVoiceName)
                            .put("speechRateMultiplier", pack.speechRateMultiplier.toDouble())
                            .put("pitchMultiplier", pack.pitchMultiplier.toDouble())
                            .put("pauseMultiplier", pack.pauseMultiplier.toDouble())
                            .put("expressiveness", pack.expressiveness.toDouble())
                            .put("providerLabel", pack.providerLabel)
                            .put("version", pack.version)
                            .put("installedAtEpochMs", pack.installedAtEpochMs)
                    )
                }
            },
        )
        ensureDirectory()
        manifestFile.writeText(payload.toString())
    }

    private fun parseVoicePack(
        json: JSONObject,
        fallbackProviderLabel: String,
    ): NarratorVoicePack {
        val id = json.optString("id").trim().ifBlank {
            throw IllegalStateException("Voice pack id is missing.")
        }
        return NarratorVoicePack(
            id = id,
            displayName = json.optString("displayName").trim().ifBlank { id },
            description = json.optString("description").trim().ifBlank {
                "Custom voice pack"
            },
            languageTag = json.optString("languageTag").trim().ifBlank { "en-US" },
            systemVoiceName = json.optString("systemVoiceName").trim().ifBlank { null },
            speechRateMultiplier = json.optDouble("speechRateMultiplier", 1.0).toFloat()
                .coerceIn(0.7f, 1.3f),
            pitchMultiplier = json.optDouble("pitchMultiplier", 1.0).toFloat()
                .coerceIn(0.7f, 1.3f),
            pauseMultiplier = json.optDouble("pauseMultiplier", 1.0).toFloat()
                .coerceIn(0.5f, 1.8f),
            expressiveness = json.optDouble("expressiveness", 0.6).toFloat()
                .coerceIn(0f, 1f),
            providerLabel = json.optString("providerLabel").trim().ifBlank { fallbackProviderLabel },
            version = json.optInt("version", 1).coerceAtLeast(1),
            installedAtEpochMs = json.optLong("installedAtEpochMs", System.currentTimeMillis()),
        )
    }

    private fun ensureDirectory() {
        require(voicePackDirectory.exists() || voicePackDirectory.mkdirs()) {
            "Unable to create voice pack directory."
        }
    }

    private val voicePackDirectory: File
        get() = File(context.filesDir, VOICE_PACK_DIRECTORY_NAME)

    private val manifestFile: File
        get() = File(voicePackDirectory, MANIFEST_FILE_NAME)

    private companion object {
        private const val VOICE_PACK_DIRECTORY_NAME = "voice-packs"
        private const val MANIFEST_FILE_NAME = "installed-packs.json"
        private const val KEY_VOICE_PACKS = "voicePacks"
        private const val STARTER_PACK_ID = "studio-warm-en-us"
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val READ_TIMEOUT_MS = 8_000
    }
}
