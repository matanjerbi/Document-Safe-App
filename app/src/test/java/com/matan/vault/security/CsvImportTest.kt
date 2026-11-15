package com.matan.vault.security

import com.matan.vault.data.CredentialKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvImportTest {

    @Test
    fun parsesGooglePasswordManagerExport() {
        val csv = "﻿name,url,username,password,note\r\n" +
            "accounts.google.com,https://accounts.google.com/signin,matan@gmail.com,\"p,a\"\"ss\",\r\n" +
            ",https://www.bankhapoalim.co.il/login,12345,Secr3t!,\"line1\nline2\"\r\n" +
            "WhatsApp,android://abc@com.whatsapp/,0501234567,pw,\r\n"
        val rows = CsvImport.parse(csv)
        assertEquals(3, rows.size)
        assertEquals("p,a\"ss", rows[0].password)
        assertEquals("matan@gmail.com", rows[0].username)
        assertEquals("Bankhapoalim", rows[1].title)
        assertEquals("line1\nline2", rows[1].note)
        assertEquals("com.whatsapp", CsvImport.host(rows[2].url))
    }

    @Test
    fun acceptsOtherColumnOrders() {
        val csv = "url,username,password,name\nhttps://github.com,matan,x1,GitHub\n"
        val r = CsvImport.parse(csv).single()
        assertEquals("GitHub", r.title)
        assertEquals("x1", r.password)
    }

    @Test(expected = CsvImport.FormatException::class)
    fun rejectsFileWithoutPasswordColumn() {
        CsvImport.parse("a,b,c\n1,2,3\n")
    }

    @Test
    fun guessesKinds() {
        assertEquals(CredentialKind.EMAIL, CsvImport.guessKind("https://mail.google.com", "gmail"))
        assertEquals(CredentialKind.BANK, CsvImport.guessKind("https://login.bankhapoalim.co.il", ""))
        assertEquals(CredentialKind.GOV, CsvImport.guessKind("https://www.gov.il/he", ""))
        assertEquals(CredentialKind.SOCIAL, CsvImport.guessKind("https://x.com", ""))
        assertEquals(CredentialKind.WEBSITE, CsvImport.guessKind("https://www.dropbox.com", ""))
        assertTrue(CsvImport.titleFromUrl("https://www.ynet.co.il/home").equals("Ynet"))
    }
}
