package com.matan.vault.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.matan.vault.security.KeyManager
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY favorite DESC, createdAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observe(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE archived = 0 AND remindersOn = 1 AND expiryDate IS NOT NULL")
    suspend fun withExpiry(): List<DocumentEntity>

    @Insert
    suspend fun insert(doc: DocumentEntity): Long

    @Update
    suspend fun update(doc: DocumentEntity)

    @Delete
    suspend fun delete(doc: DocumentEntity)

    @Query("UPDATE documents SET lastNotifiedDay = :day WHERE id IN (:ids)")
    suspend fun markNotified(ids: List<Long>, day: Long)
}

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY position")
    fun observeFor(docId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY position")
    suspend fun pagesFor(docId: Long): List<PageEntity>

    @Query("SELECT * FROM pages WHERE position = 0")
    fun observeCovers(): Flow<List<PageEntity>>

    @Insert
    suspend fun insertAll(pages: List<PageEntity>)

    @Query("DELETE FROM pages WHERE documentId = :docId")
    suspend fun deleteFor(docId: Long)
}

@Database(entities = [DocumentEntity::class, PageEntity::class, CredentialEntity::class], version = 2, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun documents(): DocumentDao
    abstract fun pages(): PageDao
    abstract fun credentials(): CredentialDao

    companion object {
        @Volatile private var instance: VaultDatabase? = null

        fun get(context: Context): VaultDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        /** v2: saved passwords. Existing documents stay untouched. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `credentials` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `kind` TEXT NOT NULL, `username` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, `secret` TEXT NOT NULL, `secureNotes` TEXT NOT NULL, " +
                        "`favorite` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
            }
        }

        private fun build(context: Context): VaultDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(KeyManager.databasePassphrase(context))
            return Room.databaseBuilder(context, VaultDatabase::class.java, "vault.db")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
