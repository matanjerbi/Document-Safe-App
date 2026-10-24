package com.matan.vault.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordToolsTest {

    @Test
    fun generatedPasswordHasRequestedLengthAndAllGroups() {
        repeat(200) {
            val pw = PasswordTools.generate(16)
            assertEquals(16, pw.length)
            assertTrue(pw.any { it.isLowerCase() })
            assertTrue(pw.any { it.isUpperCase() })
            assertTrue(pw.any { it.isDigit() })
            assertTrue(pw.any { !it.isLetterOrDigit() })
            assertTrue(pw.none { it in "0O1lI" })
        }
    }

    @Test
    fun lettersOnlyWhenOptionsOff() {
        val pw = PasswordTools.generate(12, digits = false, symbols = false)
        assertTrue(pw.all { it.isLetter() })
    }

    @Test
    fun generatedPasswordsDiffer() {
        assertNotEquals(PasswordTools.generate(), PasswordTools.generate())
    }

    @Test
    fun strengthRanksSensibly() {
        assertEquals(Strength.VERY_WEAK, PasswordTools.strength("123456"))
        assertEquals(Strength.VERY_WEAK, PasswordTools.strength("password"))
        assertEquals(Strength.VERY_WEAK, PasswordTools.strength("aaaaaaaa"))
        assertTrue(PasswordTools.strength("matan1996").score <= Strength.WEAK.score)
        assertTrue(PasswordTools.strength(PasswordTools.generate(18)).score >= Strength.STRONG.score)
        assertEquals(Strength.VERY_STRONG, PasswordTools.strength(PasswordTools.generate(24)))
    }
}
