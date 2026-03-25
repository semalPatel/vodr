package com.vodr.playback

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

internal data class PlaybackSessionSnapshot(
    val sessionId: String,
    val updatedAtEpochMs: Long,
    val isFavorite: Boolean = false,
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
            append("SESSION")
            append('\t')
            append(escape(snapshot.sessionId))
            append('\t')
            append(snapshot.updatedAtEpochMs)
            appendLine()
            if (snapshot.isFavorite) {
                append("FAVORITE")
                append('\t')
                append('1')
                appendLine()
            }
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
        var sessionId: String? = null
        var updatedAtEpochMs = 0L
        var isFavorite = false
        var activeDocument: PlaybackDocument? = null
        var runtimeMetadata: PlaybackRuntimeMetadata? = null
        var currentChapterIndex = 0
        var resumePositionMs = 0L
        var playbackSpeed = PlaybackState.DEFAULT_PLAYBACK_SPEED
        val queue = mutableListOf<PlaybackChapter>()

        lines.drop(1).forEach { line ->
            val tokens = splitEscaped(line)
            when (tokens.firstOrNull()) {
                "SESSION" -> {
                    sessionId = tokens.getOrNull(1)?.let(::unescape)
                    updatedAtEpochMs = tokens.getOrNull(2)?.toLongOrNull() ?: 0L
                }

                "FAVORITE" -> {
                    isFavorite = tokens.getOrNull(1) == "1"
                }

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
            sessionId = sessionId
                ?.takeIf { it.isNotBlank() }
                ?: deriveSessionId(activeDocument = activeDocument, queue = queue),
            updatedAtEpochMs = updatedAtEpochMs,
            isFavorite = isFavorite,
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

internal object PlaybackSessionHistoryCodec {
    fun encode(history: List<PlaybackSessionSnapshot>): String {
        return buildString {
            appendLine(HEADER_LINE)
            history.forEach { snapshot ->
                appendLine(SESSION_BEGIN_LINE)
                append(PlaybackSessionCodec.encode(snapshot))
                appendLine(SESSION_END_LINE)
            }
        }
    }

    fun decode(serialized: String): List<PlaybackSessionSnapshot> {
        if (serialized.isBlank()) {
            return emptyList()
        }
        val lines = serialized.lines()
        if (lines.firstOrNull() != HEADER_LINE) {
            return emptyList()
        }
        val snapshots = mutableListOf<PlaybackSessionSnapshot>()
        val currentBlock = mutableListOf<String>()
        var isCollecting = false
        lines.drop(1).forEach { line ->
            when (line) {
                SESSION_BEGIN_LINE -> {
                    isCollecting = true
                    currentBlock.clear()
                }

                SESSION_END_LINE -> {
                    if (isCollecting) {
                        PlaybackSessionCodec.decode(currentBlock.joinToString(separator = "\n"))
                            ?.let(snapshots::add)
                    }
                    currentBlock.clear()
                    isCollecting = false
                }

                else -> {
                    if (isCollecting) {
                        currentBlock += line
                    }
                }
            }
        }
        return snapshots
    }

    private const val HEADER_LINE: String = "VODR_PLAYBACK_HISTORY_V1"
    private const val SESSION_BEGIN_LINE: String = "BEGIN_SESSION"
    private const val SESSION_END_LINE: String = "END_SESSION"
}

@Singleton
class PlaybackSessionStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    internal fun load(): PlaybackSessionSnapshot? = loadHistory().firstOrNull()

    internal fun loadHistory(): List<PlaybackSessionSnapshot> {
        return runCatching {
            if (!sessionFile.exists()) {
                emptyList()
            } else {
                decodePersistedHistory(sessionFile.readText())
            }
        }.getOrDefault(emptyList())
    }

    internal fun save(snapshot: PlaybackSessionSnapshot): List<PlaybackSessionSnapshot> {
        val history = buildHistory(snapshot, loadHistory())
        saveHistory(history)
        return history
    }

    internal fun saveHistory(history: List<PlaybackSessionSnapshot>) {
        runCatching {
            sessionFile.writeText(
                PlaybackSessionHistoryCodec.encode(history.take(MAX_HISTORY_SIZE)),
            )
        }
    }

    internal fun restore(sessionId: String): List<PlaybackSessionSnapshot> {
        val existing = loadHistory()
        val target = existing.firstOrNull { it.sessionId == sessionId } ?: return existing
        val updated = buildHistory(
            snapshot = target.copy(updatedAtEpochMs = System.currentTimeMillis()),
            existing = existing,
        )
        saveHistory(updated)
        return updated
    }

    internal fun remove(sessionId: String): List<PlaybackSessionSnapshot> {
        val updated = loadHistory().filterNot { it.sessionId == sessionId }
        saveHistory(updated)
        return updated
    }

    internal fun setFavorite(
        sessionId: String,
        isFavorite: Boolean,
    ): List<PlaybackSessionSnapshot> {
        val updated = loadHistory().map { snapshot ->
            if (snapshot.sessionId == sessionId) {
                snapshot.copy(isFavorite = isFavorite)
            } else {
                snapshot
            }
        }
        saveHistory(updated)
        return updated
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

    private fun decodePersistedHistory(serialized: String): List<PlaybackSessionSnapshot> {
        return when {
            serialized.startsWith("VODR_PLAYBACK_HISTORY_V1") -> {
                PlaybackSessionHistoryCodec.decode(serialized)
            }

            serialized.startsWith("VODR_PLAYBACK_SESSION_V1") -> {
                PlaybackSessionCodec.decode(serialized)?.let(::listOf) ?: emptyList()
            }

            else -> emptyList()
        }
    }

    private fun buildHistory(
        snapshot: PlaybackSessionSnapshot,
        existing: List<PlaybackSessionSnapshot>,
    ): List<PlaybackSessionSnapshot> {
        return buildList {
            add(snapshot)
            addAll(existing.filterNot { it.sessionId == snapshot.sessionId })
        }.take(MAX_HISTORY_SIZE)
    }

    private companion object {
        private const val SESSION_FILE_NAME: String = "vodr-playback-session.txt"
        private const val MAX_HISTORY_SIZE: Int = 5
    }
}

internal fun PlaybackState.toPlaybackSessionSnapshot(
    nowEpochMs: Long = System.currentTimeMillis(),
): PlaybackSessionSnapshot? {
    if (queue.isEmpty()) {
        return null
    }
    val sessionId = deriveSessionId(activeDocument = activeDocument, queue = queue)
    return PlaybackSessionSnapshot(
        sessionId = sessionId,
        updatedAtEpochMs = nowEpochMs,
        isFavorite = sessionHistory.firstOrNull { it.sessionId == sessionId }?.isFavorite == true,
        queue = queue,
        activeDocument = activeDocument,
        runtimeMetadata = runtimeMetadata,
        currentChapterIndex = currentChapterIndex,
        resumePositionMs = resumePositionMs,
        playbackSpeed = playbackSpeed,
    )
}

internal fun PlaybackSessionSnapshot.toPlaybackState(
    history: List<PlaybackSessionSnapshot>,
): PlaybackState {
    val clampedIndex = currentChapterIndex.coerceIn(0, queue.lastIndex)
    val clampedSpeed = playbackSpeed.coerceIn(0.75f, 2.0f)
    return PlaybackState(
        queue = queue,
        activeDocument = activeDocument,
        runtimeMetadata = runtimeMetadata,
        sessionHistory = history.toSessionHistory(),
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

internal fun List<PlaybackSessionSnapshot>.toSessionHistory(): List<PlaybackSessionSummary> {
    return map { snapshot ->
        val document = snapshot.activeDocument
        val chapterTitle = snapshot.queue
            .getOrNull(snapshot.currentChapterIndex.coerceIn(0, snapshot.queue.lastIndex))
            ?.title
            ?: snapshot.queue.firstOrNull()?.title
            ?: "Listening session"
        val durationMs = snapshot.queue
            .getOrNull(snapshot.currentChapterIndex.coerceIn(0, snapshot.queue.lastIndex))
            ?.let { chapter ->
                PlaybackEstimator.estimatedDurationMs(
                    text = chapter.text,
                    playbackSpeed = snapshot.playbackSpeed,
                )
            }
            ?: 0L
        PlaybackSessionSummary(
            sessionId = snapshot.sessionId,
            documentTitle = document?.title ?: chapterTitle,
            documentSourceUri = document?.sourceUri ?: snapshot.sessionId,
            documentMimeType = document?.mimeType ?: "application/octet-stream",
            chapterTitle = chapterTitle,
            progressFraction = if (durationMs > 0L) {
                (snapshot.resumePositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            },
            updatedAtEpochMs = snapshot.updatedAtEpochMs,
            isFavorite = snapshot.isFavorite,
            personalizationProviderLabel = snapshot.runtimeMetadata?.personalizationProviderLabel,
            transcriptionProviderLabel = snapshot.runtimeMetadata?.transcriptionProviderLabel,
        )
    }
}

private fun deriveSessionId(
    activeDocument: PlaybackDocument?,
    queue: List<PlaybackChapter>,
): String {
    return activeDocument?.sourceUri?.takeIf { it.isNotBlank() }
        ?: queue.firstOrNull()?.id
        ?: "vodr-session"
}
