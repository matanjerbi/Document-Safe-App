package com.matan.vault.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class CredentialKind(val label: String) {
    WEBSITE("אתרים ואפליקציות"),
    EMAIL("מייל"),
    BANK("בנקים וכספים"),
    GOV("ממשלתי"),
    WORK("עבודה"),
    STUDIES("לימודים"),
    SOCIAL("רשתות חברתיות"),
    SHOPPING("קניות"),
    WIFI("Wi‑Fi"),
    OTHER("אחר");

    companion object {
        fun of(name: String): CredentialKind = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/**
 * A saved login. The whole DB is already SQLCipher-encrypted; on top of that, [secret] and
 * [secureNotes] are encrypted with a separate Android Keystore key (see SecretBox), so the
 * password never sits in the DB as plain text even after the DB is opened.
 */
@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: String = CredentialKind.WEBSITE.name,
    val username: String = "",
    val url: String = "",
    /** encrypted password (SecretBox) */
    val secret: String = "",
    /** encrypted free text: PIN, security questions, recovery codes (SecretBox) */
    val secureNotes: String = "",
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** when the password itself last changed */
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun kindOf(): CredentialKind = CredentialKind.of(kind)

    fun matches(q: String): Boolean =
        q.isEmpty() || listOf(title, username, url, kindOf().label).any { it.lowercase().contains(q) }
}

@Dao
interface CredentialDao {
    @Query("SELECT * FROM credentials ORDER BY favorite DESC, title COLLATE NOCASE")
    fun observeAll(): Flow<List<CredentialEntity>>

    @Query("SELECT * FROM credentials WHERE id = :id")
    fun observe(id: Long): Flow<CredentialEntity?>

    @Query("SELECT * FROM credentials WHERE id = :id")
    suspend fun get(id: Long): CredentialEntity?

    @Insert
    suspend fun insert(c: CredentialEntity): Long

    @Update
    suspend fun update(c: CredentialEntity)

    @Delete
    suspend fun delete(c: CredentialEntity)
}
