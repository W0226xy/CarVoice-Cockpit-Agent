package com.carvoice.agent.knowledge

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class ParsedPage(val text: String, val pageNumber: Int?, val sectionTitle: String? = null)
interface DocumentParser { fun parse(bytes: ByteArray, contentType: String): List<ParsedPage> }

class SafeDocumentParser : DocumentParser {
    override fun parse(bytes: ByteArray, contentType: String): List<ParsedPage> = when (contentType) {
        "text/plain" -> listOf(ParsedPage(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).decode(java.nio.ByteBuffer.wrap(bytes)).toString(), null))
        "application/pdf" -> Loader.loadPDF(bytes).use { document ->
            (1..document.numberOfPages).map { page ->
                val stripper = PDFTextStripper().apply { startPage = page; endPage = page }
                ParsedPage(stripper.getText(document), page)
            }
        }
        else -> throw IllegalArgumentException("Only PDF and TXT documents are supported")
    }
}

data class UnembeddedChunk(val content: String, val chunkIndex: Int, val pageNumber: Int?, val sectionTitle: String?)
class DocumentChunker(private val chunkSize: Int, private val overlap: Int) {
    init { require(chunkSize > 0 && overlap in 0 until chunkSize) }
    fun chunk(pages: List<ParsedPage>): List<UnembeddedChunk> {
        val result = mutableListOf<UnembeddedChunk>(); var index = 0
        pages.forEach { page ->
            val text = page.text.replace(Regex("\\s+"), " ").trim()
            var start = 0
            while (start < text.length) {
                var end = minOf(start + chunkSize, text.length)
                if (end < text.length) {
                    val boundary = listOf('。', '！', '？', '\n').maxOf { text.substring(start, end).lastIndexOf(it) }
                    if (boundary >= chunkSize / 2) end = start + boundary + 1
                }
                val part = text.substring(start, end).trim()
                if (part.isNotBlank()) result += UnembeddedChunk(part, index++, page.pageNumber, page.sectionTitle)
                if (end == text.length) break
                start = (end - overlap).coerceAtLeast(start + 1)
            }
        }
        return result
    }
}

class DocumentIngestionService(
    private val repository: KnowledgeManagementRepository,
    private val parser: DocumentParser,
    private val chunker: DocumentChunker,
    private val embeddingService: EmbeddingService,
) {
    suspend fun process(baseId: UUID, documentId: UUID, contentType: String, bytes: ByteArray) {
        try {
            repository.updateDocumentStatus(documentId, DocumentStatus.PROCESSING)
            val chunks = chunker.chunk(parser.parse(bytes, contentType))
            require(chunks.isNotEmpty()) { "Document contains no extractable text" }
            val vectors = embeddingService.embedAll(chunks.map { it.content })
            require(vectors.size == chunks.size) { "Embedding response count is invalid" }
            // A deletion wins over a background import; never recreate chunks for a deleted row.
            if (repository.getDocument(baseId, documentId) == null) return
            repository.replaceChunks(documentId, baseId, chunks.zip(vectors).map { (chunk, vector) -> DocumentChunk(chunk.content, chunk.chunkIndex, chunk.pageNumber, chunk.sectionTitle, vector) })
            repository.updateDocumentStatus(documentId, DocumentStatus.READY)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (repository.getDocument(baseId, documentId) != null) repository.updateDocumentStatus(documentId, DocumentStatus.FAILED, error.safeMessage())
        }
    }
    suspend fun ingest(baseId: UUID, fileName: String, contentType: String, bytes: ByteArray): ManagedDocument {
        val document = repository.createDocument(baseId, fileName.safeFileName(), contentType); process(baseId, document.id, contentType, bytes); return requireNotNull(repository.getDocument(baseId, document.id))
    }
    private fun String.safeFileName() = substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(255).ifBlank { "document" }
    private fun Throwable.safeMessage() = (message ?: "Document processing failed").replace(Regex("[\\r\\n]"), " ").take(500)
}

/** Lifecycle-owned import runner. Jobs are keyed by document ID so retry/double-submit cannot race. */
class DocumentImportManager(private val scope: kotlinx.coroutines.CoroutineScope, private val ingestion: DocumentIngestionService) : AutoCloseable {
    private val jobs = java.util.concurrent.ConcurrentHashMap<UUID, kotlinx.coroutines.Job>()
    fun submit(baseId: UUID, document: ManagedDocument, bytes: ByteArray) {
        jobs.computeIfAbsent(document.id) { id -> scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try { ingestion.process(baseId, id, document.contentType, bytes) } finally { jobs.remove(id) }
        } }
    }
    override fun close() { jobs.values.forEach { it.cancel() }; jobs.clear() }
}
