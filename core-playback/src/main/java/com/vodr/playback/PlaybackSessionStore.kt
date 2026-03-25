package com.vodr.playback

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

internal data class PlaybackSessionSnapshot(
    val queue: List<PlaybackChapter>,
    val activeDocument: PlaybackDocument?,
    val runtimeMetadata: PlaybackRuntimeMetadata?,
    val currentChapterIndex: Int,
    val resumePositionMs: Long,
    val playbackSpeed: Float,
)

internal object PlaybackSessionCodec {
    fun encode(snapshot: PlaybackSessionSnapshot): String {
        return buildString {
            appendLine(HEADER_LINE)
            snapshot.activeDocument?.let { document ->
                append("DOC")
                append('\t')
                append(escape(document.title))
                append('\t')
                append(escape(document.sourceUri))
                append('\t')
                append(escape(document.mimeType))
                appendLine()
            }
            snapshot.runtimeMetadata?.let { metadata ->
                append("RUNTIME")
                append('\t')
                append(escape(metadata.personalizationProviderLabel.orEmpty()))
                append('\t')
                append(escape(metadata.personalizationDetail.orEmpty()))
                append('\t')
                append(escape(metadata.transcriptionProviderLabel.orEmpty()))
                append('\t')
                append(escape(metadata.transcriptionDetail.orEmpty()))
                appendLine()
            }
            append("STATE")
            append('\t')
            append(snapshot.currentChapterIndex)
            append('\t')
            append(snapshot.resumePositionMs)
            append('\t')
            append(snapshot.playbackSpeed)
            appendLine()
            snapshot.queue.forEach { chapter ->
                append("CHAPTER")
                append('\t')
                append(escape(chapter.id))
                append('\t')
                append(escape(chapter.title))
                append('\t')
                append(escape(chapter.text))
                appendLine()
            }
        }
    }

    fun decode(serialized: String): PlaybackSessionSnapshot? {
        if (serialized.isBlank()) {
            return null
        }
        val lines = serialized.lines().filter { it.isNotBlank() }
        if (lines.firstOrNull() != HEADER_LINE) {
            return null
        }
        var activeDocument: PlaybackDocument? = null
        var runtimeMetadata: PlaybackRuntimeMetadata? = null
        var currentChapterIndex = 0
        var resumePositionMs = 0L
        var playbackSpeed = PlaybackState.DEFAULT_PLAYBACK_SPEED
        val queue = mutableListOf<PlaybackChapter>()

        lines.drop(1).forEach { line ->
            val tokens = splitEscaped(line)
            when (tokens.firstOrNull()) {
                "DOC" -> {
                    if (tokens.size >= 4) {
                        activeDocument = PlaybackDocument(
                            title = unescape(tokens[1]),
                            sourceUri = unescape(tokens[2]),
                            mimeType = unescape(tokens[3]),
                        )
                    }
                }

                "STATE" -> {
                    currentChapterIndex = tokens.getOrNull(1)?.toIntOrNull() ?: 0
                    resumePositionMs = tokens.getOrNull(2)?.toLongOrNull() ?: 0L
                    playbackSpeed = tokens.getOrNull(3)?.toFloatOrNull()
                        ?.coerceIn(0.75f, 2.0f)
                        ?: PlaybackState.DEFAULT_PLAYBACK_SPEED
                }

                "RUNTIME" -> {
                    runtimeMetadata = PlaybackRuntimeMetadata(
                        personalizationProviderLabel = tokens.getOrNull(1)
                            ?.let(::unescape)
                            .orNullIfBlank(),
                        personalizationDetail = tokens.getOrNull(2)
                            ?.let(::unescape)
                            .orNullIfBlank(),
                        transcriptionProviderLabel = tokens.getOrNull(3)
                            ?.let(::unescape)
                            .orNullIfBlank(),
                        transcriptionDetail = tokens.getOrNull(4)
                            ?.let(::unescape)
                            .orNullIfBlank(),
                    )
                }

                "CHAPTER" -> {
                    if (tokens.size >= 4) {
                        queue += PlaybackChapter(
                            id = unescape(tokens[1]),
                            title = unescape(tokens[2]),
                            text = unescape(tokens[3]),
                        )
                    }
                }
            }
        }

        if (queue.isEmpty()) {
            return null
        }

        return PlaybackSessionSnapshot(
            queue = queue,
            activeDocument = activeDocument,
            runtimeMetadata = runtimeMetadata,
            currentChapterIndex = currentChapterIndex.coerceIn(0, queue.lastIndex),
            resumePositionMs = resumePositionMs.coerceAtLeast(0L),
            playbackSpeed = playbackSpeed,
        )
    }

    private fun escape(value: String): String {
        return buildString(value.length) {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '\t' -> append("\\t")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(character)
                }
            }
        }
    }

    private fun unescape(value: String): String {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character == '\\' && index + 1 < value.length) {
                when (value[index + 1]) {
                    '\\' -> output.append('\\')
                    't' -> output.append('\t')
                    'n' -> output.append('\n')
                    'r' -> output.append('\r')
                    else -> output.append(value[index + 1])
                }
                index += 2
            } else {
                output.append(character)
                index += 1
            }
        }
        return output.toString()
    }

    private fun splitEscaped(line: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < line.length) {
            val character = line[index]
            if (character == '\t') {
                parts += current.toString()
                current.setLength(0)
                index += 1
            } else if (character == '\\' && index + 1 < line.length) {
                current.append(character)
                current.append(line[index + 1])
                index += 2
            } else {
                current.append(character)
                index += 1
            }
        }
        parts += current.toString()
        return parts
    }

    private const val HEADER_LINE: String = "VODR_PLAYBACK_SESSION_V1"
}

@Singleton
class PlaybackSessionStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    internal fun load(): PlaybackSessionSnapshot? {
        return runCatching {
            if (!sessionFile.exists()) {
                null
            } else {
                PlaybackSessionCodec.decode(sessionFile.readText())
            }
        }.getOrNull()
    }

    internal fun save(snapshot: PlaybackSessionSnapshot) {
        runCatching {
            sessionFile.writeText(PlaybackSessionCodec.encode(snapshot))
        }
    }

    internal fun clear() {
        runCatching {
            if (sessionFile.exists()) {
                sessionFile.delete()
            }
        }
    }

    private val sessionFile: File
        get() = File(context.filesDir, SESSION_FILE_NAME)

    private companion object {
        private const val SESSION_FILE_NAME: String = "vodr-playback-session.txt"
    }
}

internal fun PlaybackState.toPlaybackSessionSnapshot(): PlaybackSessionSnapshot? {
    if (queue.isEmpty()) {
        return null
    }
    return PlaybackSessionSnapshot(
        queue = queue,
        activeDocument = activeDocument,
        runtimeMetadata = runtimeMetadata,
        currentChapterIndex = currentChapterIndex,
        resumePositionMs = resumePositionMs,
        playbackSpeed = playbackSpeed,
    )
}

internal fun PlaybackSessionSnapshot.toPlaybackState(): PlaybackState {
    val clampedIndex = currentChapterIndex.coerceIn(0, queue.lastIndex)
    val clampedSpeed = playbackSpeed.coerceIn(0.75f, 2.0f)
    return PlaybackState(
        queue = queue,
        activeDocument = activeDocument,
        runtimeMetadata = runtimeMetadata,
        currentChapterIndex = clampedIndex,
        resumePositionMs = resumePositionMs.coerceAtLeast(0L),
        currentChapterDurationMs = queue.getOrNull(clampedIndex)?.let { chapter ->
            PlaybackEstimator.estimatedDurationMs(
                text = chapter.text,
                playbackSpeed = clampedSpeed,
            )
        } ?: 0L,
        playbackSpeed = clampedSpeed,
        playbackStatus = PlaybackStatus.PAUSED,
        isVoiceReady = false,
        errorMessage = null,
    )
}

private fun String?.orNullIfBlank(): String? {
    return this?.takeIf { it.isNotBlank() }
}
