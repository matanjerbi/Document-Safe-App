package com.matan.vault.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Category(val label: String, val defaultLeadDays: Int, val repeatDays: Int) {
    ID("תעודות", 60, 14),
    PASSPORT("דרכון", 90, 14),
    LICENSE("רישיון נהיגה", 60, 14),
    VEHICLE("רכב", 30, 7),
    INSURANCE("ביטוחים", 30, 7),
    FINANCE("כספים ועבודה", 0, 0),
    HOUSING("דיור", 90, 30),
    RECEIPT("קבלות ואחריות", 30, 0),
    STUDIES("לימודים", 0, 0),
    ARMY("צבא", 30, 14),
    HEALTH("בריאות", 30, 7),
    OTHER("אחר", 30, 7);

    companion object {
        fun of(name: String): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,
    val owner: String = "",
    val tags: String = "",
    val docNumber: String = "",
    val issuer: String = "",
    /** epoch day (LocalDate.toEpochDay) */
    val issueDate: Long? = null,
    val expiryDate: Long? = null,
    val amount: String = "",
    val notes: String = "",
    val ocrText: String = "",
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val remindersOn: Boolean = true,
    val leadDays: Int = 30,
    val lastNotifiedDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun cat(): Category = Category.of(category)

    fun expiry(): LocalDate? = expiryDate?.let { LocalDate.ofEpochDay(it) }
    fun issue(): LocalDate? = issueDate?.let { LocalDate.ofEpochDay(it) }

    fun daysLeft(today: LocalDate = LocalDate.now()): Long? =
        expiry()?.let { ChronoUnit.DAYS.between(today, it) }

    fun expiryState(today: LocalDate = LocalDate.now()): ExpiryState {
        val d = daysLeft(today) ?: return ExpiryState.NONE
        return when {
            d < 0 -> ExpiryState.EXPIRED
            d <= leadDays.coerceAtLeast(14) -> ExpiryState.SOON
            else -> ExpiryState.OK
        }
    }

    fun tagList(): List<String> = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

enum class ExpiryState { NONE, OK, SOON, EXPIRED }

@Entity(
    tableName = "pages",
    indices = [Index("documentId")]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val position: Int,
    /** file name inside the encrypted vault directory */
    val fileName: String,
)
