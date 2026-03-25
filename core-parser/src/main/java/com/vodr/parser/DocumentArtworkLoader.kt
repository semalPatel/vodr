package com.vodr.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

object DocumentArtworkLoader {
    private val cache = object : LruCache<String, Bitmap>(CACHE_SIZE) {}

    fun load(
        context: Context,
        sourceUri: String,
        mimeType: String,
        title: String,
        maxSizePx: Int = DEFAULT_MAX_SIZE_PX,
    ): Bitmap {
        val cacheKey = "$sourceUri|$maxSizePx"
        cache.get(cacheKey)?.let { cached ->
            return cached
        }
        val extracted = runCatching {
            when (mimeType) {
                PDF_MIME_TYPE -> loadPdfArtwork(
                    context = context,
                    sourceUri = sourceUri,
                    maxSizePx = maxSizePx,
                )

                EPUB_MIME_TYPE -> loadEpubArtwork(
                    context = context,
                    sourceUri = sourceUri,
                    maxSizePx = maxSizePx,
                )

                else -> null
            }
        }.getOrNull()
        val artwork = extracted ?: createPlaceholderArtwork(
            title = title,
            mimeType = mimeType,
            maxSizePx = maxSizePx,
        )
        cache.put(cacheKey, artwork)
        return artwork
    }

    private fun loadPdfArtwork(
        context: Context,
        sourceUri: String,
        maxSizePx: Int,
    ): Bitmap? {
        val uri = Uri.parse(sourceUri)
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        descriptor.use { fileDescriptor ->
            val renderer = PdfRenderer(fileDescriptor)
            renderer.use {
                if (renderer.pageCount == 0) {
                    return null
                }
                val page = renderer.openPage(0)
                page.use {
                    val longestEdge = maxOf(page.width, page.height).coerceAtLeast(1)
                    val scale = maxSizePx.toFloat() / longestEdge.toFloat()
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    bitmap.eraseColor(Color.WHITE)
                    val matrix = Matrix().apply {
                        postScale(scale, scale)
                    }
                    page.render(
                        bitmap,
                        null,
                        matrix,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                    )
                    return bitmap
                }
            }
        }
    }

    private fun loadEpubArtwork(
        context: Context,
        sourceUri: String,
        maxSizePx: Int,
    ): Bitmap? {
        val uri = Uri.parse(sourceUri)
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        inputStream.use { stream ->
            val entries = readZipEntries(stream)
            val containerBytes = entries["META-INF/container.xml"] ?: return null
            val containerDocument = parseXml(containerBytes)
            val opfPath = readRootfilePath(containerDocument)
            val opfBytes = entries[opfPath] ?: return null
            val opfDocument = parseXml(opfBytes)
            val manifest = readManifest(opfDocument)
            val packageDirectory = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
            val coverHref = readCoverHref(
                opfDocument = opfDocument,
                manifest = manifest,
            ) ?: return null
            val coverBytes = entries[resolveRelativePath(packageDirectory, coverHref)] ?: return null
            return decodeBitmap(bytes = coverBytes, maxSizePx = maxSizePx)
        }
    }

    private fun readZipEntries(inputStream: InputStream): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun readRootfilePath(containerDocument: Document): String {
        val rootfiles = containerDocument.getElementsByTagNameNS("*", "rootfile")
        require(rootfiles.length > 0) {
            "EPUB container is missing a rootfile"
        }
        return (rootfiles.item(0) as Element).getAttribute("full-path")
    }

    private fun readManifest(opfDocument: Document): List<EpubManifestItem> {
        val items = mutableListOf<EpubManifestItem>()
        val manifestNodes = opfDocument.getElementsByTagNameNS("*", "item")
        for (index in 0 until manifestNodes.length) {
            val element = manifestNodes.item(index) as Element
            items += EpubManifestItem(
                id = element.getAttribute("id"),
                href = element.getAttribute("href"),
                properties = element.getAttribute("properties"),
            )
        }
        return items
    }

    private fun readCoverHref(
        opfDocument: Document,
        manifest: List<EpubManifestItem>,
    ): String? {
        val metadataNodes = opfDocument.getElementsByTagNameNS("*", "meta")
        for (index in 0 until metadataNodes.length) {
            val element = metadataNodes.item(index) as? Element ?: continue
            val coverId = if (element.getAttribute("name") == "cover") {
                element.getAttribute("content")
            } else {
                ""
            }
            if (coverId.isNotBlank()) {
                return manifest.firstOrNull { it.id == coverId }?.href
            }
        }
        return manifest.firstOrNull { "cover-image" in it.properties }?.href
            ?: manifest.firstOrNull { it.href.lowercase(Locale.US).contains("cover") }?.href
    }

    private fun resolveRelativePath(packageDirectory: String, href: String): String {
        return if (packageDirectory.isBlank()) {
            href
        } else {
            "$packageDirectory/$href"
        }
    }

    private fun decodeBitmap(
        bytes: ByteArray,
        maxSizePx: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longestEdge = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        val sampleSize = (longestEdge / maxSizePx).coerceAtLeast(1)
        val options = BitmapFactory.Options().apply {
            inSampleSize = Integer.highestOneBit(sampleSize).coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun createPlaceholderArtwork(
        title: String,
        mimeType: String,
        maxSizePx: Int,
    ): Bitmap {
        val width = (maxSizePx * PLACEHOLDER_WIDTH_RATIO).toInt()
        val height = maxSizePx
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (topColor, bottomColor) = paletteFor(title)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                topColor,
                bottomColor,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            CORNER_RADIUS_PX,
            CORNER_RADIUS_PX,
            backgroundPaint,
        )

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(64, 255, 255, 255)
        }
        val badgeRect = RectF(
            BADGE_MARGIN_PX,
            BADGE_MARGIN_PX,
            width.toFloat() - BADGE_MARGIN_PX,
            BADGE_HEIGHT_PX + BADGE_MARGIN_PX,
        )
        canvas.drawRoundRect(badgeRect, BADGE_RADIUS_PX, BADGE_RADIUS_PX, badgePaint)

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = BADGE_TEXT_SIZE_PX
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
        }
        canvas.drawText(
            if (mimeType == EPUB_MIME_TYPE) "EPUB" else "PDF",
            badgeRect.centerX(),
            badgeRect.centerY() + (BADGE_TEXT_SIZE_PX / 3f),
            badgeTextPaint,
        )

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = TITLE_TEXT_SIZE_PX
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
        }
        canvas.drawText(
            initialsFor(title),
            width / 2f,
            height / 2f + (TITLE_TEXT_SIZE_PX / 3f),
            titlePaint,
        )

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            textAlign = Paint.Align.CENTER
            textSize = SUBTITLE_TEXT_SIZE_PX
        }
        canvas.drawText(
            "Offline ready",
            width / 2f,
            height - FOOTER_MARGIN_PX,
            subtitlePaint,
        )
        return bitmap
    }

    private fun paletteFor(title: String): Pair<Int, Int> {
        val hue = (title.hashCode().ushr(1) % 360).toFloat()
        val topColor = Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.82f))
        val bottomColor = Color.HSVToColor(floatArrayOf((hue + 28f) % 360f, 0.72f, 0.5f))
        return topColor to bottomColor
    }

    private fun initialsFor(title: String): String {
        val words = title
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> "VO"
            words.size == 1 -> words.first().take(2).uppercase(Locale.US)
            else -> "${words.first().first()}${words.last().first()}".uppercase(Locale.US)
        }
    }

    private data class EpubManifestItem(
        val id: String,
        val href: String,
        val properties: String,
    )

    private const val CACHE_SIZE: Int = 24
    private const val DEFAULT_MAX_SIZE_PX: Int = 480
    private const val PLACEHOLDER_WIDTH_RATIO: Float = 0.78f
    private const val CORNER_RADIUS_PX: Float = 28f
    private const val BADGE_MARGIN_PX: Float = 24f
    private const val BADGE_HEIGHT_PX: Float = 72f
    private const val BADGE_RADIUS_PX: Float = 18f
    private const val BADGE_TEXT_SIZE_PX: Float = 30f
    private const val TITLE_TEXT_SIZE_PX: Float = 112f
    private const val SUBTITLE_TEXT_SIZE_PX: Float = 28f
    private const val FOOTER_MARGIN_PX: Float = 40f
    private const val PDF_MIME_TYPE: String = "application/pdf"
    private const val EPUB_MIME_TYPE: String = "application/epub+zip"
}
