package com.vodr.ai

internal fun buildPersonalizationPrompt(
    providerLabel: String,
    inputText: String,
    tone: String,
    style: String,
): String {
    return buildString {
        append("provider=")
        append(providerLabel)
        append('\n')
        append("tone=")
        append(normalizePromptToken(tone))
        append('\n')
        append("style=")
        append(normalizePromptToken(style))
        append('\n')
        append("input=")
        append(inputText.trim())
    }
}

internal fun normalizePromptToken(value: String): String {
    return value.trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
}
