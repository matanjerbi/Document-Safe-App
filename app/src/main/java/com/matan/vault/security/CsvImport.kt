package com.matan.vault.security

import com.matan.vault.data.CredentialKind

/** One login read from an exported passwords file (Google Password Manager / Chrome / other managers). */
data class ImportedLogin(
    val title: String,
    val url: String,
    val username: String,
    val password: String,
    val note: String,
)

/**
 * Parses the CSV that Google Password Manager exports (passwords.google.com → Settings → Export):
 *   name,url,username,password,note
 * Also accepts the column names used by Chrome, Samsung Pass, Bitwarden, 1Password, LastPass and others,
 * in any order. Pure Kotlin so it can be unit-tested without a device.
 */
object CsvImport {

    private val titleCols = listOf("name", "title", "account", "site", "website name")
    private val urlCols = listOf("url", "website", "login_uri", "web site", "origin_url", "address")
    private val userCols = listOf("username", "login", "login_username", "user", "email", "user name", "login name")
    private val passCols = listOf("password", "login_password", "pass")
    private val noteCols = listOf("note", "notes", "extra", "comments", "comment")

    class FormatException(msg: String) : Exception(msg)

    fun parse(text: String): List<ImportedLogin> {
        val rows = parseRows(text.removePrefix("﻿"))
        if (rows.isEmpty()) throw FormatException("הקובץ ריק")
        val header = rows.first().map { it.trim().lowercase() }
        fun col(names: List<String>) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }
        val iPass = col(passCols) ?: throw FormatException("לא נמצאה עמודת password בקובץ")
        val iTitle = col(titleCols)
        val iUrl = col(urlCols)
        val iUser = col(userCols)
        val iNote = col(noteCols)

        return rows.drop(1).mapNotNull { r ->
            fun get(i: Int?) = i?.let { r.getOrNull(it) }?.trim().orEmpty()
            val pw = r.getOrNull(iPass).orEmpty()
            val url = get(iUrl)
            val user = get(iUser)
            if (pw.isEmpty() && user.isEmpty()) return@mapNotNull null
            ImportedLogin(
                title = get(iTitle).ifBlank { titleFromUrl(url) }.ifBlank { user },
                url = url,
                username = user,
                password = pw,
                note = get(iNote),
            )
        }
    }

    /** RFC-4180 CSV: quoted fields, "" escapes, commas and newlines inside quotes. */
    internal fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(ch)
            } else when (ch) {
                '"' -> inQuotes = true
                ',' -> { row.add(field.toString()); field.clear() }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    /** "https://accounts.google.com/signin" → "accounts.google.com"; "android://…@com.whatsapp/" → "com.whatsapp" */
    fun host(url: String): String {
        val u = url.trim()
        if (u.startsWith("android://")) return u.substringAfterLast('@').trimEnd('/')
        return u.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':')
            .removePrefix("www.").lowercase()
    }

    fun titleFromUrl(url: String): String {
        val h = host(url)
        if (h.isEmpty()) return ""
        if (url.startsWith("android://")) return h.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        val parts = h.split('.')
        // drop common second-level suffixes: co.il, org.il, gov.il, co.uk …
        val main = when {
            parts.size >= 3 && parts[parts.size - 2] in setOf("co", "org", "gov", "ac", "net", "muni", "com") -> parts[parts.size - 3]
            parts.size >= 2 -> parts[parts.size - 2]
            else -> parts[0]
        }
        return main.replaceFirstChar { it.uppercase() }
    }

    fun guessKind(url: String, title: String): CredentialKind {
        val s = (host(url) + " " + title).lowercase()
        fun any(vararg k: String) = k.any { it in s }
        if (host(url) == "x.com") return CredentialKind.SOCIAL
        return when {
            any(".gov.il", "gov.il", "btl.", "mof.", "taxes", "misim", "idf.", "my.gov", "gov.") -> CredentialKind.GOV
            any("bank", "poalim", "leumi", "discount", "mizrahi", "tefahot", "otsar", "fibi", "isracard",
                "cal-online", "cal.co", "max.co", "leumi-card", "paypal", "bit.", "pepper", "one zero", "onezero",
                "meitav", "altshuler", "migdal", "harel", "clal", "menora", "phoenix", "fnx", "psagot", "ibi.",
                "interactivebrokers", "etoro", "binance", "coinbase", "revolut", "wise.com", "excellence") -> CredentialKind.BANK
            any("gmail", "outlook", "hotmail", "live.com", "yahoo", "proton", "walla", "icloud", "mail.") -> CredentialKind.EMAIL
            any("facebook", "instagram", "twitter", "tiktok", "linkedin", "reddit", "snapchat", "whatsapp",
                "telegram", "discord", "pinterest", "threads", "tinder", "bumble") -> CredentialKind.SOCIAL
            any("openu", "university", "ac.il", ".edu", "moodle", "coursera", "udemy", "campus", "tau.", "huji", "technion") -> CredentialKind.STUDIES
            any("amazon", "aliexpress", "ebay", "shein", "ksp", "ivory", "bug.co", "zap.", "yad2", "shufersal", "rami-levy",
                "temu", "asos", "terminalx", "wolt", "10bis", "cibus", "booking", "airbnb") -> CredentialKind.SHOPPING
            any("slack", "atlassian", "jira", "confluence", "github", "gitlab", "redis", "azure", "aws", "okta", "zoom",
                "monday", "notion", "office", "teams") -> CredentialKind.WORK
            else -> CredentialKind.WEBSITE
        }
    }
}
