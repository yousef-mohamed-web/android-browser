package com.example.geckobrowser.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.security.SecureRandom
import android.util.Base64

@Entity(tableName = "history") data class HistoryEntry(@PrimaryKey(autoGenerate = true) val id: Long = 0, val url: String, val title: String, val visitedAt: Long)
@Entity(tableName = "bookmarks") data class Bookmark(@PrimaryKey val url: String, val title: String, val createdAt: Long)
@Dao interface BrowserDao {
    @Insert suspend fun addHistory(item: HistoryEntry)
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 500") suspend fun history(): List<HistoryEntry>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveBookmark(item: Bookmark)
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC") suspend fun bookmarks(): List<Bookmark>
}
@Database(entities = [HistoryEntry::class, Bookmark::class], version = 1, exportSchema = true)
abstract class BrowserDatabase : RoomDatabase() { abstract fun dao(): BrowserDao
    companion object {
        @Volatile private var instance: BrowserDatabase? = null
        fun get(context: Context): BrowserDatabase = instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }
        private fun build(context: Context): BrowserDatabase {
            SQLiteDatabase.loadLibs(context)
            val prefs = androidx.security.crypto.EncryptedSharedPreferences.create("db_key", "master_key", context,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
            val passphrase = prefs.getString("sqlcipher_key", null) ?: ByteArray(32).also { SecureRandom().nextBytes(it) }.let { Base64.encodeToString(it, Base64.NO_WRAP) }.also { prefs.edit().putString("sqlcipher_key", it).apply() }
            val factory = SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8))
            return Room.databaseBuilder(context, BrowserDatabase::class.java, "browser.db").openHelperFactory(factory).build()
        }
    }
}
