package com.matan.vault.ocr

import com.matan.vault.data.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DocParserTest {
    private val today = LocalDate.of(2026, 10, 23)

    @Test
    fun passportMrz() {
        val text = """
            PASSPORT
            P<ISRJERBI<<MATAN<<<<<<<<<<<<<<<<<<<<<<<<<<<
            12345678<4ISR9801012M3103124<<<<<<<<<<<<<<06
        """.trimIndent()
        val r = DocParser.parse(text, today)
        assertTrue(r.fromMrz)
        assertEquals(Category.PASSPORT, r.category)
        assertEquals("12345678", r.docNumber)
        assertEquals(LocalDate.of(2031, 3, 12), r.expiryDate)
        assertEquals(LocalDate.of(1998, 1, 1), r.birthDate)
        assertEquals("MATAN JERBI", r.holderName)
        assertEquals("ISR", r.issuer)
    }

    @Test
    fun keywordDates() {
        val text = """
            Driving Licence
            Date of issue 14/02/2021
            Valid until 14.02.2031
        """.trimIndent()
        val r = DocParser.parse(text, today)
        assertEquals(Category.LICENSE, r.category)
        assertEquals(LocalDate.of(2031, 2, 14), r.expiryDate)
        assertEquals(LocalDate.of(2021, 2, 14), r.issueDate)
    }

    @Test
    fun receiptAmountNoExpiry() {
        val text = """
            KSP receipt
            21/10/2026 14:22
            Headphones 399.90
            TOTAL 449.80
        """.trimIndent()
        val r = DocParser.parse(text, today)
        assertEquals(Category.RECEIPT, r.category)
        assertEquals("449.80", r.amount)
        assertNull(r.expiryDate)
        assertEquals(LocalDate.of(2026, 10, 21), r.issueDate)
    }

    @Test
    fun israeliIdChecksum() {
        assertTrue(DocParser.validIsraeliId("000000018"))
        assertTrue(!DocParser.validIsraeliId("123456789"))
    }

    @Test
    fun undatedLatestFutureIsExpiry() {
        val r = DocParser.parse("Insurance policy\n01/01/2026\n31/12/2026", today)
        assertEquals(Category.INSURANCE, r.category)
        assertEquals(LocalDate.of(2026, 12, 31), r.expiryDate)
        assertEquals(LocalDate.of(2026, 1, 1), r.issueDate)
    }
}
