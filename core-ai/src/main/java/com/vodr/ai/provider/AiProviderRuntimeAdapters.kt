package com.vodr.ai.provider

import com.vodr.ai.CustomProviderConfig
import com.vodr.ai.buildPersonalizationPrompt
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

data class EndpointPromptRequest(
    val prompt: String,
    val modelName: String,
    val taskInstruction: String,
)

interface EndpointTransport {
    fun execute(endpoint: String, body: String): String
}

class HttpUrlEndpointTransport : EndpointTransport {
    override fun execute(endpoint: String, body: String): String {
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        return connection.run {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(body)
            }
            val responseBody = (if (responseCode in 200..299) inputStream else errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { reader -> reader.readText() }
                .orEmpty()
            if (responseCode !in 200..299) {
                throw IllegalStateException(
                    "Endpoint returned HTTP $responseCode${responseBody.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}"
                )
            }
            responseBody
        }
    }

    private companion object {
        private const val CONNECT_TIMEOUT_MS = 2_000
        private const val READ_TIMEOUT_MS = 7_000
    }
}

class EndpointAiClient(
    private val transport: EndpointTransport = HttpUrlEndpointTransport(),
) {
    fun generate(
        config: CustomProviderConfig,
        request: EndpointPromptRequest,
    ): String {
        val endpoint = config.localEndpoint.trim()
        if (endpoint.isEmpty()) {
            throw IllegalStateException("Custom endpoint is not configured.")
        }
        val body = buildRequestBody(
            endpoint = endpoint,
            config = config,
            request = request,
        )
        val response = transport.execute(
            endpoint = endpoint,
            body = body,
        )
        return extractResponseText(response)
    }

    private fun buildRequestBody(
        endpoint: String,
        config: CustomProviderConfig,
        request: EndpointPromptRequest,
    ): String {
        val normalizedEndpoint = endpoint.lowercase()
        return when {
            normalizedEndpoint.contains("/api/generate") ->
                """
                {
                  "model": "${jsonEscape(request.modelName.ifBlank { config.modelName.ifBlank { DEFAULT_MODEL_NAME } })}",
                  "prompt": "${jsonEscape("${request.taskInstruction}\n\n${request.prompt}")}",
                  "stream": false
                }
                """.trimIndent()

            normalizedEndpoint.contains("/chat/completions") ->
                """
                {
                  "model": "${jsonEscape(request.modelName.ifBlank { config.modelName.ifBlank { DEFAULT_MODEL_NAME } })}",
                  "messages": [
                    {
                      "role": "system",
                      "content": "${jsonEscape(request.taskInstruction)}"
                    },
                    {
                      "role": "user",
                      "content": "${jsonEscape(request.prompt)}"
                    }
                  ],
                  "temperature": 0.2
                }
                """.trimIndent()

            normalizedEndpoint.contains("/completions") ->
                """
                {
                  "model": "${jsonEscape(request.modelName.ifBlank { config.modelName.ifBlank { DEFAULT_MODEL_NAME } })}",
                  "prompt": "${jsonEscape("${request.taskInstruction}\n\n${request.prompt}")}",
                  "temperature": 0.2
                }
                """.trimIndent()

            else ->
                """
                {
                  "model": "${jsonEscape(request.modelName.ifBlank { config.modelName.ifBlank { DEFAULT_MODEL_NAME } })}",
                  "instruction": "${jsonEscape(request.taskInstruction)}",
                  "prompt": "${jsonEscape(request.prompt)}"
                }
                """.trimIndent()
        }
    }

    private fun extractResponseText(response: String): String {
        listOf("response", "content", "text").forEach { key ->
            Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""")
                .find(response)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::jsonUnescape)
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        throw IllegalStateException("Endpoint response did not include generated text.")
    }

    private fun jsonEscape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun jsonUnescape(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private companion object {
        private const val DEFAULT_MODEL_NAME = "local-model"
    }
}

class LocalTemplateAiClient {
    fun render(
        config: CustomProviderConfig,
        defaultLabel: String,
        tone: String,
        style: String,
        inputText: String,
        taskInstruction: String,
    ): String {
        val path = config.localModelPath.trim()
        if (path.isEmpty()) {
            return buildPersonalizationPrompt(
                providerLabel = defaultLabel,
                inputText = inputText,
                tone = tone,
                style = style,
            )
        }

        val file = File(path)
        val template = runCatching { file.readText() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return buildPersonalizationPrompt(
                providerLabel = defaultLabel,
                inputText = inputText,
                tone = tone,
                style = style,
            )

        val resolvedTemplate = parseTemplate(template, taskInstruction)
        return resolvedTemplate
            .replace("{{providerLabel}}", defaultLabel)
            .replace("{{tone}}", tone.trim())
            .replace("{{style}}", style.trim())
            .replace("{{inputText}}", inputText.trim())
            .replace("{{modelName}}", config.modelName.ifBlank { "local-model" })
            .replace("{{taskInstruction}}", taskInstruction)
            .trim()
    }

    private fun parseTemplate(
        template: String,
        taskInstruction: String,
    ): String {
        val trimmed = template.trim()
        if (!trimmed.startsWith("{")) {
            return trimmed
        }

        return runCatching {
            val preferredKey = if (taskInstruction.contains("transcript", ignoreCase = true)) {
                "transcriptionTemplate"
            } else {
                "personalizationTemplate"
            }
            extractJsonString(trimmed, preferredKey)
                .ifBlank { extractJsonString(trimmed, "template") }
        }.getOrDefault(trimmed)
            .ifBlank { trimmed }
    }

    private fun extractJsonString(
        jsonLike: String,
        key: String,
    ): String {
        return Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""")
            .find(jsonLike)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\n", "\n")
            ?.replace("\\r", "\r")
            ?.replace("\\t", "\t")
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
            .orEmpty()
    }
}
