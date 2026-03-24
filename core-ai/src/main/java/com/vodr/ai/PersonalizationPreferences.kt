package com.vodr.ai

import java.io.File
import java.net.URI

enum class PersonalizationProviderType {
    AUTO,
    AI_CORE,
    MEDIA_PIPE,
    CUSTOM_LOCAL_MODEL,
    CUSTOM_ENDPOINT,
    OFFLINE_HEURISTIC,
}

data class CustomProviderConfig(
    val localModelPath: String = "",
    val localEndpoint: String = "",
    val modelName: String = "",
)

data class PersonalizationPreferences(
    val providerType: PersonalizationProviderType = PersonalizationProviderType.AUTO,
    val customProviderConfig: CustomProviderConfig = CustomProviderConfig(),
    val offlineOnly: Boolean = true,
)

data class ResolvedProviderSelection(
    val providerType: PersonalizationProviderType,
    val detail: String? = null,
)

enum class ProbeAvailability {
    AVAILABLE,
    UNAVAILABLE,
    BLOCKED,
}

data class PersonalizationProbeResult(
    val providerType: PersonalizationProviderType,
    val availability: ProbeAvailability,
    val detail: String? = null,
)

interface PersonalizationRuntimeProbe {
    val providerType: PersonalizationProviderType

    fun probe(preferences: PersonalizationPreferences): PersonalizationProbeResult
}

class PersonalizationProbeRegistry(
    probes: List<PersonalizationRuntimeProbe>,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {
    private val probesByProviderType = probes.associateBy { it.providerType }
    private val cache = mutableMapOf<CacheKey, CachedProbeResult>()

    init {
        require(cacheTtlMs > 0) {
            "cacheTtlMs must be greater than 0"
        }
    }

    fun probe(
        providerType: PersonalizationProviderType,
        preferences: PersonalizationPreferences,
    ): PersonalizationProbeResult {
        val cacheKey = CacheKey(
            providerType = providerType,
            offlineOnly = preferences.offlineOnly,
            localModelPath = preferences.customProviderConfig.localModelPath,
            localEndpoint = preferences.customProviderConfig.localEndpoint,
            modelName = preferences.customProviderConfig.modelName,
        )
        val now = timeProvider()
        val cached = cache[cacheKey]
        if (cached != null && now - cached.checkedAtEpochMs < cacheTtlMs) {
            return cached.result
        }

        val result = probesByProviderType[providerType]?.probe(preferences)
            ?: PersonalizationProbeResult(
                providerType = providerType,
                availability = ProbeAvailability.UNAVAILABLE,
                detail = "No runtime probe registered.",
            )
        cache[cacheKey] = CachedProbeResult(
            result = result,
            checkedAtEpochMs = now,
        )
        return result
    }

    private data class CacheKey(
        val providerType: PersonalizationProviderType,
        val offlineOnly: Boolean,
        val localModelPath: String,
        val localEndpoint: String,
        val modelName: String,
    )

    private data class CachedProbeResult(
        val result: PersonalizationProbeResult,
        val checkedAtEpochMs: Long,
    )

    companion object {
        private const val DEFAULT_CACHE_TTL_MS = 60_000L
    }
}

class AICoreRuntimeProbe(
    private val deviceCapabilityDetector: DeviceCapabilityDetector,
) : PersonalizationRuntimeProbe {
    override val providerType: PersonalizationProviderType = PersonalizationProviderType.AI_CORE

    override fun probe(preferences: PersonalizationPreferences): PersonalizationProbeResult {
        val capabilities = deviceCapabilityDetector.detect()
        return PersonalizationProbeResult(
            providerType = providerType,
            availability = if (capabilities.supportsAICore) {
                ProbeAvailability.AVAILABLE
            } else {
                ProbeAvailability.UNAVAILABLE
            },
            detail = if (capabilities.supportsAICore) {
                "AICore runtime available."
            } else {
                "AICore unsupported on this device."
            },
        )
    }
}

class MediaPipeRuntimeProbe(
    private val deviceCapabilityDetector: DeviceCapabilityDetector,
) : PersonalizationRuntimeProbe {
    override val providerType: PersonalizationProviderType = PersonalizationProviderType.MEDIA_PIPE

    override fun probe(preferences: PersonalizationPreferences): PersonalizationProbeResult {
        val capabilities = deviceCapabilityDetector.detect()
        return PersonalizationProbeResult(
            providerType = providerType,
            availability = if (capabilities.supportsMediaPipe) {
                ProbeAvailability.AVAILABLE
            } else {
                ProbeAvailability.UNAVAILABLE
            },
            detail = if (capabilities.supportsMediaPipe) {
                "MediaPipe runtime available."
            } else {
                "MediaPipe unavailable on this device."
            },
        )
    }
}

class CustomLocalModelRuntimeProbe : PersonalizationRuntimeProbe {
    override val providerType: PersonalizationProviderType =
        PersonalizationProviderType.CUSTOM_LOCAL_MODEL

    override fun probe(preferences: PersonalizationPreferences): PersonalizationProbeResult {
        val path = preferences.customProviderConfig.localModelPath.trim()
        if (path.isEmpty()) {
            return PersonalizationProbeResult(
                providerType = providerType,
                availability = ProbeAvailability.UNAVAILABLE,
                detail = "No local model path configured.",
            )
        }

        val file = File(path)
        return PersonalizationProbeResult(
            providerType = providerType,
            availability = if (file.isFile && file.canRead()) {
                ProbeAvailability.AVAILABLE
            } else {
                ProbeAvailability.UNAVAILABLE
            },
            detail = if (file.isFile && file.canRead()) {
                "Readable local model configured."
            } else {
                "Configured local model path is not readable."
            },
        )
    }
}

class CustomEndpointRuntimeProbe : PersonalizationRuntimeProbe {
    override val providerType: PersonalizationProviderType = PersonalizationProviderType.CUSTOM_ENDPOINT

    override fun probe(preferences: PersonalizationPreferences): PersonalizationProbeResult {
        if (preferences.offlineOnly) {
            return PersonalizationProbeResult(
                providerType = providerType,
                availability = ProbeAvailability.BLOCKED,
                detail = "Offline-only mode blocks network providers.",
            )
        }

        val endpoint = preferences.customProviderConfig.localEndpoint.trim()
        if (endpoint.isEmpty()) {
            return PersonalizationProbeResult(
                providerType = providerType,
                availability = ProbeAvailability.UNAVAILABLE,
                detail = "No custom endpoint configured.",
            )
        }

        return runCatching { URI(endpoint) }.fold(
            onSuccess = { uri ->
                val isValidScheme = uri.scheme == "http" || uri.scheme == "https"
                val hasHost = !uri.host.isNullOrBlank()
                PersonalizationProbeResult(
                    providerType = providerType,
                    availability = if (isValidScheme && hasHost) {
                        ProbeAvailability.AVAILABLE
                    } else {
                        ProbeAvailability.UNAVAILABLE
                    },
                    detail = if (isValidScheme && hasHost) {
                        "Custom endpoint configured."
                    } else {
                        "Custom endpoint must include an http(s) host."
                    },
                )
            },
            onFailure = {
                PersonalizationProbeResult(
                    providerType = providerType,
                    availability = ProbeAvailability.UNAVAILABLE,
                    detail = "Custom endpoint is not a valid URI.",
                )
            },
        )
    }
}

class OfflineHeuristicRuntimeProbe : PersonalizationRuntimeProbe {
    override val providerType: PersonalizationProviderType =
        PersonalizationProviderType.OFFLINE_HEURISTIC

    override fun probe(preferences: PersonalizationPreferences): PersonalizationProbeResult {
        return PersonalizationProbeResult(
            providerType = providerType,
            availability = ProbeAvailability.AVAILABLE,
            detail = "Offline heuristic fallback is always available.",
        )
    }
}

fun PersonalizationProviderType.toDisplayName(): String {
    return when (this) {
        PersonalizationProviderType.AUTO -> "Auto"
        PersonalizationProviderType.AI_CORE -> "AI Core"
        PersonalizationProviderType.MEDIA_PIPE -> "MediaPipe"
        PersonalizationProviderType.CUSTOM_LOCAL_MODEL -> "Custom Local Model"
        PersonalizationProviderType.CUSTOM_ENDPOINT -> "Custom Endpoint"
        PersonalizationProviderType.OFFLINE_HEURISTIC -> "Offline Fallback"
    }
}
