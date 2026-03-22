package com.vodr.parser

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class EpubParser {

    fun parse(inputStream: InputStream): ParsedDocument {
        val entries = readZipEntries(inputStream)
        val containerDocument = parseXml(entries.getValue("META-INF/container.xml"))
        val opfPath = readRootfilePath(containerDocument)
        val opfDocument = parseXml(entries.getValue(opfPath))
        val manifest = readManifest(opfDocument)
        val spine = readSpine(opfDocument)
        val packageDirectory = opfPath.substringBeforeLast('/', missingDelimiterValue = "")

        val lines = mutableListOf<String>()
        for (itemId in spine) {
            val href = requireNotNull(manifest[itemId]) {
                "Missing manifest item for spine entry: $itemId"
            }
            val path = resolveRelativePath(packageDirectory, href)
            val xhtmlDocument = parseXml(entries.getValue(path))
            lines += extractBlockLines(xhtmlDocument)
        }

        return buildParsedDocument(lines)
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
        val element = rootfiles.item(0) as Element
        return element.getAttribute("full-path")
    }

    private fun readManifest(opfDocument: Document): Map<String, String> {
        val manifestNodes = opfDocument.getElementsByTagNameNS("*", "item")
        val items = linkedMapOf<String, String>()
        for (index in 0 until manifestNodes.length) {
            val element = manifestNodes.item(index) as Element
            val id = element.getAttribute("id")
            val href = element.getAttribute("href")
            if (id.isNotBlank() && href.isNotBlank()) {
                items[id] = href
            }
        }
        return items
    }

    private fun readSpine(opfDocument: Document): List<String> {
        val spineNodes = opfDocument.getElementsByTagNameNS("*", "itemref")
        val ids = mutableListOf<String>()
        for (index in 0 until spineNodes.length) {
            val element = spineNodes.item(index) as Element
            val idref = element.getAttribute("idref")
            if (idref.isNotBlank()) {
                ids += idref
            }
        }
        return ids
    }

    private fun extractBlockLines(document: Document): List<String> {
        val lines = mutableListOf<String>()
        walkForBlockText(document.documentElement, lines)
        return lines
    }

    private fun walkForBlockText(node: Node, lines: MutableList<String>) {
        when (node.nodeType) {
            Node.ELEMENT_NODE -> {
                val element = node as Element
                if (element.localName in BLOCK_TAGS) {
                    val line = element.textContent
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    if (line.isNotBlank()) {
                        lines += line
                    }
                }
                val children = element.childNodes
                for (index in 0 until children.length) {
                    walkForBlockText(children.item(index), lines)
                }
            }
        }
    }

    private fun resolveRelativePath(packageDirectory: String, href: String): String {
        return if (packageDirectory.isBlank()) {
            href
        } else {
            "$packageDirectory/$href"
        }
    }

    private companion object {
        private val BLOCK_TAGS = setOf("h1", "h2", "h3", "p", "li")
    }
}
