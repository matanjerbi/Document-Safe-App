package com.matan.vault.ocr

import com.matan.vault.data.Category
import java.time.LocalDate

/** Everything we managed to pull out of a document's text. Every field is a suggestion the user confirms. */
data class Extracted(
    val category: Category? = null,
    val title: String? = null,
    val holderName: String? = null,
    val docNumber: String? = null,
    val issuer: String? = null,
    val issueDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    val birthDate: LocalDate? = null,
    val amount: String? = null,
    val fromMrz: Boolean = false,
) {
    val isEmpty: Boolean
        get() = category == null && docNumber == null && issueDate == null &&
            expiryDate == null && amount == null && holderName == null
}

object DocParser {

    private enum class Kind { EXPIRY, ISSUE, BIRTH, NONE }

    private data class DateHit(val date: LocalDate, val kind: Kind)

    private val expiryWords = listOf(
        "expiry", "expires", "expiration", "exp.", "exp ", "valid until", "valid thru", "valid to",
        "date of expiry", "בתוקף עד", "תוקף", "פקיעה", "פג", "עד תאריך", "תום",
    )
    private val issueWords = listOf(
        "date of issue", "issued", "issue", "הנפקה", "הוצאה", "ניתן ב", "תאריך הוצאה", "תחילת",
        "purchase", "date:", "תאריך:",
    )
    private val birthWords = listOf("birth", "born", "לידה", "dob")

    private val monthNames = listOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
    )

    private val numericDate = Regex("""(?<!\d)(\d{1,2})\s?[./\-]\s?(\d{1,2})\s?[./\-]\s?(\d{4}|\d{2})(?!\d)""")
    private val isoDate = Regex("""(?<!\d)(\d{4})[./\-](\d{1,2})[./\-](\d{1,2})(?!\d)""")
    private val textDate = Regex("""(?i)(?<!\d)(\d{1,2})\s*(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[A-Z]*[\s/.]*(?:[A-Z]{3,4}[\s/.]*)?(\d{4}|\d{2})(?!\d)""")
    private val amountRx = Regex("""(?<![\d.,])(\d{1,3}(?:,\d{3})+|\d+)\.(\d{2})(?!\d)""")
    private val nineDigits = Regex("""(?<!\d)(\d{9})(?!\d)""")
    private val labeledNumber = Regex(
        """(?i)(?:passport\s*no|licen[cs]e\s*no|policy\s*no|no\.|number|מספר|מס['׳"]?|פוליסה)\s*[:#.]?\s*([A-Z0-9][A-Z0-9\-]{4,16})"""
    )

    fun parse(raw: String, today: LocalDate = LocalDate.now()): Extracted {
        val text = raw.replace('«', '<').replace('‹', '<')
        val lower = text.lowercase()

        parseMrz(text, today)?.let { mrz ->
            return mrz.copy(title = mrz.title ?: titleFor(mrz.category))
        }

        val category = guessCategory(lower)
        val hits = findDates(text, today)

        val expiry = hits.filter { it.kind == Kind.EXPIRY }.maxByOrNull { it.date }?.date
            ?: hits.filter { it.kind == Kind.NONE && it.date >= today.minusDays(30) }
                .maxByOrNull { it.date }?.date
                ?.takeIf { category != Category.RECEIPT && category != Category.FINANCE }

        val birth = hits.filter { it.kind == Kind.BIRTH }.minByOrNull { it.date }?.date
            ?: hits.filter { it.kind == Kind.NONE && it.date < today.minusYears(16) }
                .minByOrNull { it.date }?.date
                ?.takeIf { category == Category.ID || category == Category.LICENSE || category == Category.PASSPORT }

        val issue = hits.filter { it.kind == Kind.ISSUE }.maxByOrNull { it.date }?.date
            ?: hits.filter { it.kind == Kind.NONE && it.date != expiry && it.date != birth && it.date <= today }
                .maxByOrNull { it.date }?.date

        val docNumber = findIsraeliId(text)
            ?: labeledNumber.findAll(text)
                .map { it.groupValues[1] }
                .firstOrNull { s -> s.count { it.isDigit() } >= 4 }

        val amount = if (category == Category.RECEIPT || lower.contains("₪") || lower.contains("total")) {
            findAmount(text)
        } else null

        return Extracted(
            category = category,
            title = titleFor(category),
            docNumber = docNumber,
            issueDate = issue,
            expiryDate = expiry,
            birthDate = birth,
            amount = amount,
        )
    }

    // ---------- categories ----------

    fun guessCategory(lower: String): Category? = when {
        lower.containsAny("passport", "דרכון", "p<isr") -> Category.PASSPORT
        lower.containsAny("רישיון רכב", "רשיון רכב", "vehicle", "טסט", "רישוי") -> Category.VEHICLE
        lower.containsAny("driving", "driver", "רישיון נהיגה", "רשיון נהיגה") -> Category.LICENSE
        lower.containsAny("identity card", "תעודת זהות", "ת.ז", "id card") -> Category.ID
        lower.containsAny("insurance", "policy", "ביטוח", "פוליסה", "harel", "migdal", "phoenix", "menora", "clal") -> Category.INSURANCE
        lower.containsAny("warranty", "אחריות", "receipt", "invoice", "קבלה", "חשבונית", "vat", "מע\"מ", "total") -> Category.RECEIPT
        lower.containsAny("prescription", "מרשם", "קופת חולים", "clalit", "maccabi", "meuhedet", "leumit health") -> Category.HEALTH
        lower.containsAny("open university", "האוניברסיטה הפתוחה", "university", "transcript", "גיליון ציונים") -> Category.STUDIES
        lower.containsAny("idf", "צה\"ל", "תעודת שחרור", "discharge") -> Category.ARMY
        lower.containsAny("שכירות", "lease", "rent", "tenant", "שוכר") -> Category.HOUSING
        lower.containsAny("payslip", "salary", "תלוש", "טופס 106", "bank", "בנק") -> Category.FINANCE
        else -> null
    }

    fun titleFor(category: Category?): String? = when (category) {
        Category.PASSPORT -> "דרכון"
        Category.ID -> "תעודת זהות"
        Category.LICENSE -> "רישיון נהיגה"
        Category.VEHICLE -> "רישיון רכב"
        Category.INSURANCE -> "פוליסת ביטוח"
        Category.RECEIPT -> "קבלה"
        Category.HEALTH -> "מסמך רפואי"
        Category.STUDIES -> "אישור לימודים"
        Category.ARMY -> "מסמך צבאי"
        Category.HOUSING -> "חוזה שכירות"
        Category.FINANCE -> "מסמך כספי"
        else -> null
    }

    private fun String.containsAny(vararg words: String) = words.any { contains(it) }

    // ---------- dates ----------

    private fun findDates(text: String, today: LocalDate): List<DateHit> {
        val lines = text.lines()
        val out = mutableListOf<DateHit>()
        lines.forEachIndexed { i, line ->
            val context = (if (i > 0) lines[i - 1] + " " else "") + line
            fun add(date: LocalDate?, start: Int) {
                if (date == null) return
                if (date.year < 1900 || date.year > today.year + 30) return
                val before = (line.substring(0, start)).lowercase()
                val kind = classify(before.ifBlank { context.lowercase() })
                out += DateHit(date, kind)
            }
            numericDate.findAll(line).forEach { m ->
                add(safeDate(year(m.groupValues[3]), m.groupValues[2].toInt(), m.groupValues[1].toInt()), m.range.first)
            }
            isoDate.findAll(line).forEach { m ->
                add(safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()), m.range.first)
            }
            textDate.findAll(line).forEach { m ->
                val month = monthNames.indexOf(m.groupValues[2].uppercase()) + 1
                add(safeDate(year(m.groupValues[3]), month, m.groupValues[1].toInt()), m.range.first)
            }
        }
        return out.distinctBy { it.date to it.kind }
    }

    private fun classify(before: String): Kind {
        // the keyword closest to the date wins
        val tail = before.takeLast(40)
        val e = expiryWords.maxOf { tail.lastIndexOf(it) }
        val s = issueWords.maxOf { tail.lastIndexOf(it) }
        val b = birthWords.maxOf { tail.lastIndexOf(it) }
        val best = maxOf(e, s, b)
        return when {
            best < 0 -> Kind.NONE
            best == e -> Kind.EXPIRY
            best == b -> Kind.BIRTH
            else -> Kind.ISSUE
        }
    }

    private fun year(s: String): Int {
        val y = s.toInt()
        return if (s.length == 2) (if (y <= 60) 2000 + y else 1900 + y) else y
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        try { LocalDate.of(y, m, d) } catch (_: Exception) { null }

    // ---------- numbers ----------

    fun validIsraeliId(id: String): Boolean {
        if (id.length != 9 || !id.all { it.isDigit() }) return false
        var sum = 0
        id.forEachIndexed { i, c ->
            var v = (c - '0') * (if (i % 2 == 0) 1 else 2)
            if (v > 9) v -= 9
            sum += v
        }
        return sum % 10 == 0 && id != "000000000"
    }

    private fun findIsraeliId(text: String): String? =
        nineDigits.findAll(text).map { it.value }.firstOrNull { validIsraeliId(it) }

    private fun findAmount(text: String): String? {
        val totalWords = listOf("total", "סה\"כ", "סהכ", "לתשלום", "amount", "₪", "nis", "ש\"ח")
        val all = mutableListOf<Pair<Double, Boolean>>()
        text.lines().forEach { line ->
            val preferred = totalWords.any { line.lowercase().contains(it) }
            amountRx.findAll(line).forEach { m ->
                val v = (m.groupValues[1].replace(",", "") + "." + m.groupValues[2]).toDoubleOrNull()
                if (v != null && v > 0 && v < 1_000_000) all += v to preferred
            }
        }
        val pick = all.filter { it.second }.maxByOrNull { it.first } ?: all.maxByOrNull { it.first }
        return pick?.let { "%.2f".format(java.util.Locale.US, it.first) }
    }

    // ---------- MRZ (passport TD3, ID card TD1) ----------

    private fun digits(s: String) = s
        .replace('O', '0').replace('Q', '0').replace('D', '0')
        .replace('I', '1').replace('L', '1')
        .replace('S', '5').replace('B', '8').replace('Z', '2').replace('G', '6')

    private fun mrzDate(s: String, future: Boolean, today: LocalDate): LocalDate? {
        val d = digits(s)
        if (d.length != 6 || !d.all { it.isDigit() }) return null
        val yy = d.substring(0, 2).toInt()
        val century = if (future) 2000 else if (yy > today.year % 100) 1900 else 2000
        return safeDate(century + yy, d.substring(2, 4).toInt(), d.substring(4, 6).toInt())
    }

    private fun parseMrz(text: String, today: LocalDate): Extracted? {
        val lines = text.lines()
            .map { it.replace(" ", "").uppercase() }
            .filter { it.length >= 28 && it.count { c -> c == '<' } >= 2 && it.all { c -> c.isLetterOrDigit() || c == '<' } }

        // TD3: two lines of 44
        for (i in 0 until lines.size - 1) {
            val l1 = lines[i]
            val l2 = lines[i + 1]
            if (l1.startsWith("P") && l1.length >= 40 && l2.length >= 40) {
                val expiry = mrzDate(l2.substring(21, 27), true, today) ?: continue
                val birth = mrzDate(l2.substring(13, 19), false, today)
                val names = l1.substring(5).split("<<").filter { it.isNotBlank() }
                val holder = names.take(2).reversed().joinToString(" ") { it.replace('<', ' ').trim() }
                return Extracted(
                    category = Category.PASSPORT,
                    title = "דרכון",
                    holderName = holder.ifBlank { null },
                    docNumber = l2.substring(0, 9).trimEnd('<'),
                    issuer = l1.substring(2, 5).replace("<", ""),
                    expiryDate = expiry,
                    birthDate = birth,
                    issueDate = findDates(text, today).filter { it.date < expiry && it.date != birth && it.date <= today }
                        .maxByOrNull { it.date }?.date,
                    fromMrz = true,
                )
            }
        }
        // TD1: three lines of 30
        for (i in 0 until lines.size - 2) {
            val l1 = lines[i]
            val l2 = lines[i + 1]
            val l3 = lines[i + 2]
            if (l1.length in 28..32 && l2.length in 28..32 && (l1.startsWith("I") || l1.startsWith("A") || l1.startsWith("C"))) {
                val expiry = mrzDate(l2.substring(8, 14), true, today) ?: continue
                val birth = mrzDate(l2.substring(0, 6), false, today)
                val names = l3.split("<<").filter { it.isNotBlank() }
                val holder = names.take(2).reversed().joinToString(" ") { it.replace('<', ' ').trim() }
                val optional = l1.substring(15).replace("<", "")
                val idNumber = Regex("""\d{9}""").find(digits(optional))?.value?.takeIf { validIsraeliId(it) }
                return Extracted(
                    category = Category.ID,
                    title = "תעודת זהות",
                    holderName = holder.ifBlank { null },
                    docNumber = idNumber ?: l1.substring(5, 14).trimEnd('<'),
                    issuer = l1.substring(2, 5).replace("<", ""),
                    expiryDate = expiry,
                    birthDate = birth,
                    fromMrz = true,
                )
            }
        }
        return null
    }
}
