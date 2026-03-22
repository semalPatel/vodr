package com.vodr.ai.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalizerPromptTemplateTest {

    @Test
    fun normalizesToneAndStyleBeforeBuildingPrompt() {
        val prompt = AICorePersonalizer().buildPrompt(
            inputText = "  Rewrite this  ",
            tone = "  Friendly   Professional ",
            style = "  Short Form ",
        )

        assertEquals(
            """
            provider=aicore
            tone=friendly professional
            style=short form
            input=Rewrite this
            """.trimIndent(),
            prompt,
        )
    }
}
