package com.vodr.tts

class NarrationPlanner {
    fun plan(text: String): NarrationPlan {
        val normalized = text
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) {
            return NarrationPlan.Empty
        }

        val segments = normalized.split(SENTENCE_BOUNDARY_REGEX)
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { sentence ->
                val style = sentence.toNarrationStyle()
                NarrationSegment(
                    text = sentence,
                    style = style,
                    pauseAfterMs = style.defaultPauseMs(),
                )
            }

        if (segments.isEmpty()) {
            return NarrationPlan.Empty
        }

        return NarrationPlan(
            dominantStyle = segments
                .groupingBy { it.style }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?: NarrationStyle.NEUTRAL,
            segments = segments,
        )
    }
}

private fun String.toNarrationStyle(): NarrationStyle {
    val trimmed = trim()
    return when {
        trimmed.contains('"') || trimmed.contains('“') || trimmed.contains('”') -> NarrationStyle.DIALOGUE
        trimmed.endsWith("?") -> NarrationStyle.QUESTION
        trimmed.endsWith("!") -> NarrationStyle.EXCITED
        trimmed.contains("...") || trimmed.contains("…") -> NarrationStyle.SUSPENSE
        trimmed.length > 180 -> NarrationStyle.REFLECTIVE
        else -> NarrationStyle.NEUTRAL
    }
}

private fun NarrationStyle.defaultPauseMs(): Long {
    return when (this) {
        NarrationStyle.NEUTRAL -> 320L
        NarrationStyle.DIALOGUE -> 260L
        NarrationStyle.QUESTION -> 380L
        NarrationStyle.EXCITED -> 220L
        NarrationStyle.SUSPENSE -> 520L
        NarrationStyle.REFLECTIVE -> 440L
    }
}

private val SENTENCE_BOUNDARY_REGEX = Regex("(?<=[.!?…])\\s+")
