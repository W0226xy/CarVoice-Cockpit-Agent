package com.carvoice.agent.knowledge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DocumentProcessingTest {
    @Test fun `txt parser decodes utf8 and chunker preserves overlap`() {
        val pages = SafeDocumentParser().parse("第一段胎压报警。第二段继续说明胎压检查。".toByteArray(), "text/plain")
        val chunks = DocumentChunker(14, 4).chunk(pages)
        assertEquals(1, pages.size)
        assertTrue(chunks.size >= 2)
        assertTrue(chunks[1].content.any { it in chunks[0].content })
    }
    @Test fun `empty document yields no chunks`() {
        assertTrue(DocumentChunker(20, 2).chunk(listOf(ParsedPage("   ", null))).isEmpty())
    }
    @Test fun `unsupported type is rejected`() {
        assertFailsWith<IllegalArgumentException> { SafeDocumentParser().parse(byteArrayOf(1), "application/zip") }
    }
}
