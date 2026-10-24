package com.matan.vault.security

import java.security.SecureRandom
import kotlin.math.log2

enum class Strength(val label: String, val score: Int) {
    VERY_WEAK("חלשה מאוד", 0),
    WEAK("חלשה", 1),
    FAIR("בינונית", 2),
    STRONG("חזקה", 3),
    VERY_STRONG("חזקה מאוד", 4),
}

object PasswordTools {
    private const val LOWER = "abcdefghjkmnpqrstuvwxyz"   // no i, l, o
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"   // no I, O
    private const val DIGITS = "23456789"                  // no 0, 1
    private const val SYMBOLS = "!@#$%^&*-_=+?"

    private val random = SecureRandom()

    /** Random password with at least one character of every chosen group. Look-alike characters are left out. */
    fun generate(length: Int = 18, digits: Boolean = true, symbols: Boolean = true, upper: Boolean = true): String {
        val groups = buildList {
            add(LOWER)
            if (upper) add(UPPER)
            if (digits) add(DIGITS)
            if (symbols) add(SYMBOLS)
        }
        val len = length.coerceIn(groups.size, 64)
        val all = groups.joinToString("")
        val chars = MutableList(len) { all[random.nextInt(all.length)] }
        // guarantee one from each group, at distinct random positions
        val positions = (0 until len).shuffled(random).take(groups.size)
        groups.forEachIndexed { i, g -> chars[positions[i]] = g[random.nextInt(g.length)] }
        return chars.joinToString("")
    }

    private val common = setOf(
        "123456", "1234567", "12345678", "123456789", "1234567890", "password", "qwerty", "qwerty123",
        "111111", "000000", "abc123", "iloveyou", "admin", "welcome", "letmein", "123123", "654321",
        "password1", "1q2w3e4r", "zaq12wsx", "asdfgh", "123qwe", "aa123456", "israel", "shalom",
    )

    fun strength(pw: String): Strength {
        if (pw.isEmpty()) return Strength.VERY_WEAK
        val lower = pw.lowercase()
        if (lower in common || pw.toSet().size <= 2) return Strength.VERY_WEAK

        var pool = 0
        if (pw.any { it.isLowerCase() }) pool += 26
        if (pw.any { it.isUpperCase() }) pool += 26
        if (pw.any { it.isDigit() }) pool += 10
        if (pw.any { !it.isLetterOrDigit() }) pool += 33
        if (pw.any { it.code > 127 && it.isLetter() }) pool += 27 // Hebrew letters etc.
        var bits = pw.length * log2(pool.coerceAtLeast(10).toDouble())

        // penalties for things attackers try first
        if (pw.all { it.isDigit() }) bits *= 0.7
        if (hasSequence(lower)) bits -= 12
        if (Regex("(.)\\1{2,}").containsMatchIn(pw)) bits -= 8
        if (Regex("(19|20)\\d{2}").containsMatchIn(pw)) bits -= 6
        // "name1996" / "Name123!" — a word with digits stuck on the end
        if (Regex("^[A-Za-z]+\\d+[^A-Za-z0-9]?$").matches(pw)) bits -= 8
        if (common.any { it.length >= 5 && lower.contains(it) }) bits -= 20

        return when {
            bits < 28 -> Strength.VERY_WEAK
            bits < 40 -> Strength.WEAK
            bits < 60 -> Strength.FAIR
            bits < 80 -> Strength.STRONG
            else -> Strength.VERY_STRONG
        }
    }

    private fun hasSequence(s: String): Boolean {
        var run = 1
        for (i in 1 until s.length) {
            val d = s[i].code - s[i - 1].code
            run = if (d == 1 || d == -1) run + 1 else 1
            if (run >= 4) return true
        }
        return false
    }
}
