package com.vodr.playback

import android.speech.tts.TextToSpeech
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Locale
import org.junit.Assert.assertArrayEquals
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

    @Test
    fun resolveSafeTtsChunkMaxChars_capsOverlyLargeEngineLimits() {
        assertEquals(3_000, resolveSafeTtsChunkMaxChars(engineReportedMaxChars = 7_455))
        assertEquals(1_200, resolveSafeTtsChunkMaxChars(engineReportedMaxChars = 1_200))
        assertEquals(3_000, resolveSafeTtsChunkMaxChars(engineReportedMaxChars = 0))
    }

    @Test
    fun concatenateWaveFiles_mergesPcmPayloadInOrder() {
        val tempDirectory = Files.createTempDirectory("vodr-wave-test").toFile()
        try {
            val first = File(tempDirectory, "first.wav").apply {
                writeBytes(buildTestWaveFile(audioData = byteArrayOf(1, 2, 3, 4)))
            }
            val second = File(tempDirectory, "second.wav").apply {
                writeBytes(buildTestWaveFile(audioData = byteArrayOf(5, 6)))
            }
            val output = File(tempDirectory, "merged.wav")

            concatenateWaveFiles(
                inputFiles = listOf(first, second),
                outputFile = output,
            )

            assertEquals("RIFF", String(output.readBytes(), 0, 4, Charsets.US_ASCII))
            assertArrayEquals(
                byteArrayOf(1, 2, 3, 4, 5, 6),
                readWaveAudioData(output),
            )
        } finally {
            tempDirectory.deleteRecursively()
        }
    }

    private fun buildTestWaveFile(audioData: ByteArray): ByteArray {
        val formatChunk = byteArrayOf(
            1, 0,
            1, 0,
            0x80.toByte(), 0x3E, 0, 0,
            0x00, 0x7D, 0, 0,
            2, 0,
            16, 0,
        )
        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            writeLittleEndianInt(4 + 8 + formatChunk.size + 8 + audioData.size)
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write("fmt ".toByteArray(Charsets.US_ASCII))
            writeLittleEndianInt(formatChunk.size)
            write(formatChunk)
            write("data".toByteArray(Charsets.US_ASCII))
            writeLittleEndianInt(audioData.size)
            write(audioData)
        }.toByteArray()
    }

    private fun readWaveAudioData(file: File): ByteArray {
        val bytes = file.readBytes()
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = readLittleEndianInt(bytes, offset + 4)
            val chunkStart = offset + 8
            val chunkEnd = chunkStart + chunkSize
            if (chunkId == "data") {
                return bytes.copyOfRange(chunkStart, chunkEnd)
            }
            offset = chunkEnd + (chunkSize % 2)
        }
        error("Wave data chunk missing")
    }

    private fun ByteArrayOutputStream.writeLittleEndianInt(value: Int) {
        write(
            byteArrayOf(
                (value and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                ((value shr 16) and 0xFF).toByte(),
                ((value shr 24) and 0xFF).toByte(),
            ),
        )
    }

    private fun readLittleEndianInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}
