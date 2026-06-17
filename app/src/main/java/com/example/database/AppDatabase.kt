package com.example.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// =====================================================================
// Entities
// =====================================================================

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val builderPrefix: String = "@builder",
    val executorPrefix: String = "@executor",
    val treedocPrefix: String = "@treedoc",
    val baseDir: String,
    val goldenMode: Boolean = false,
    val bubbleEnabled: Boolean = false,
    val autostartEnabled: Boolean = false,
    val ignoreBatteryEnabled: Boolean = false,
    val logLevel: String = "NORMAL" // NORMAL, DETAILED, JSON
)

@Entity(tableName = "log_entries")
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "info", "success", "error"
    val tag: String,  // "builder", "executor", "treedoc", "system"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_files")
data class AppFileEntity(
    @PrimaryKey val filepath: String, // relative or full path
    val content: String,
    val size: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String, // "user", "gemini"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

// =====================================================================
// DAOs
// =====================================================================

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: SettingsEntity)
}

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<LogEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntryEntity)

    @Query("DELETE FROM log_entries")
    suspend fun clearLogs()
}

@Dao
interface AppFileDao {
    @Query("SELECT * FROM app_files ORDER BY timestamp DESC")
    fun getAllFilesFlow(): Flow<List<AppFileEntity>>

    @Query("SELECT * FROM app_files ORDER BY timestamp DESC")
    suspend fun getAllFiles(): List<AppFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: AppFileEntity)

    @Query("DELETE FROM app_files WHERE filepath = :filepath")
    suspend fun deleteFile(filepath: String)

    @Query("DELETE FROM app_files")
    suspend fun clearFiles()
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages")
    suspend fun clearHistory()
}

// =====================================================================
// AppDatabase
// =====================================================================

@Database(
    entities = [
        SettingsEntity::class,
        LogEntryEntity::class,
        AppFileEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun appFileDao(): AppFileDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_platform_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
