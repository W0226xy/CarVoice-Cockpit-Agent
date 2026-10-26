package com.carvoice.agent.knowledge

import kotlin.test.Test
import kotlin.test.assertEquals

class CitationExtractorTest {
    @Test fun `extracts individual comma and range citations in first-seen order`() {
        assertEquals(listOf(1, 3), extractCitationIndexes("结论[1][3]", 3))
        assertEquals(listOf(1, 2), extractCitationIndexes("结论[1,2]", 3))
        assertEquals(listOf(1, 2, 3), extractCitationIndexes("结论[1-3]", 3))
        assertEquals(listOf(1, 2), extractCitationIndexes("结论[1，2][2]", 3))
    }

    @Test fun `ignores plain numbers and out of range citations`() {
        assertEquals(emptyList(), extractCitationIndexes("第 99 页，没有来源", 3))
        assertEquals(listOf(1), extractCitationIndexes("结论[99][1][0]", 3))
    }
}
