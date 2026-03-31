package com.vodr.playback

import android.speech.tts.TextToSpeech
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VodrPlaybackServiceTest {

    @Test
    fun selectFirstSupportedTtsLocale_prefersDefaultLocaleWhenSupported() {
        val selected = selectFirstSupportedTtsLocale(defaultLocale = Locale.CANADA) { locale ->
            if (locale == Locale.CANADA) {
                TextToSpeech.LANG_COUNTRY_AVAILABLE
            } else {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
        }

        assertEquals(Locale.CANADA, selected)
    }

    @Test
    fun selectFirstSupportedTtsLocale_fallsBackToUsWhenDefaultLocaleUnsupported() {
        val selected = selectFirstSupportedTtsLocale(defaultLocale = Locale.JAPAN) { locale ->
            if (locale == Locale.US) {
                TextToSpeech.LANG_AVAILABLE
            } else {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
        }

        assertEquals(Locale.US, selected)
    }

    @Test
    fun selectFirstSupportedTtsLocale_returnsNullWhenNoLocaleSupported() {
        val selected = selectFirstSupportedTtsLocale(defaultLocale = Locale.FRANCE) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }

        assertNull(selected)
    }

    @Test
    fun supportedTtsLanguageResult_acceptsAvailableCodesOnly() {
        assertEquals(true, TextToSpeech.LANG_AVAILABLE.isSupportedTtsLanguageResult())
        assertEquals(true, TextToSpeech.LANG_COUNTRY_AVAILABLE.isSupportedTtsLanguageResult())
        assertEquals(true, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE.isSupportedTtsLanguageResult())
        assertEquals(false, TextToSpeech.LANG_MISSING_DATA.isSupportedTtsLanguageResult())
        assertEquals(false, TextToSpeech.LANG_NOT_SUPPORTED.isSupportedTtsLanguageResult())
    }

    @Test
    fun resolvePreferredTtsEnginePackageName_usesDefaultWhenInstalled() {
        val engine = resolvePreferredTtsEnginePackageName(
            defaultEnginePackage = "com.example.tts",
            availableEngines = listOf("com.example.tts", "com.google.android.tts"),
        )

        assertEquals("com.example.tts", engine)
    }

    @Test
    fun resolvePreferredTtsEnginePackageName_fallsBackToGoogleTtsWhenDefaultMissing() {
        val engine = resolvePreferredTtsEnginePackageName(
            defaultEnginePackage = null,
            availableEngines = listOf("com.google.android.tts", "com.example.tts"),
        )

        assertEquals("com.google.android.tts", engine)
    }

    @Test
    fun splitTextForTts_breaksLongInputIntoBoundedUtterances() {
        val chunks = splitTextForTts(
            text = "One short sentence. This is a much longer sentence that should be split into multiple pieces when needed for playback.",
            maxChars = 24,
        )

        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.length <= 24 })
        assertEquals(
            "One short sentence. This is a much longer sentence that should be split into multiple pieces when needed for playback.",
            chunks.joinToString(" "),
        )
    }
}
