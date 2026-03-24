package com.vodr.ai.provider

import com.vodr.ai.CustomProviderConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AiProviderRuntimeAdaptersTest {

    @Test
    fun localTemplateClientRendersPlaceholdersFromFile() {
        val templateFile = java.io.File.createTempFile("vodr-template", ".prompt")
        templateFile.writeText("model={{modelName}} tone={{tone}} style={{style}} input={{inputText}}")

        val result = LocalTemplateAiClient().render(
            config = CustomProviderConfig(
                localModelPath = templateFile.absolutePath,
                modelName = "reader-local",
            ),
            defaultLabel = "custom-local",
            tone = "warm",
            style = "balanced",
            inputText = "Chapter preview",
            taskInstruction = "unused",
        )

        assertEquals(
            "model=reader-local tone=warm style=balanced input=Chapter preview",
            result,
        )
    }

    @Test
    fun endpointClientParsesChatCompletionResponse() {
        val client = EndpointAiClient(
            transport = FakeEndpointTransport(
                """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "AI generated chapter label"
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val result = client.generate(
            config = CustomProviderConfig(
                localEndpoint = "https://example.com/v1/chat/completions",
                modelName = "reader-model",
            ),
            request = EndpointPromptRequest(
                prompt = "prompt",
                modelName = "reader-model",
                taskInstruction = "Summarize",
            ),
        )

        assertEquals("AI generated chapter label", result)
    }

    private class FakeEndpointTransport(
        private val responseBody: String,
    ) : EndpointTransport {
        override fun execute(endpoint: String, body: String): String = responseBody
    }
}
