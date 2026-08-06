package com.example.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/* ============================================================================
 * ROOM DB — replay oturumları (HTML IndexedDB karşılığı, max 100 FIFO)
 * ========================================================================== */

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val t0: Long,
    val t1: Long,
    val symbol: String,
    @ColumnInfo(name = "events_json") val eventsJson: String
)

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY t0 DESC")
    fun allSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY t0 DESC LIMIT 100")
    suspend fun latestSessions(): List<SessionEntity>

    @Query("DELETE FROM sessions WHERE id NOT IN (SELECT id FROM sessions ORDER BY t0 DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [SessionEntity::class], version = 1, exportSchema = false)
abstract class BozokDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile private var INSTANCE: BozokDatabase? = null
        fun getInstance(context: Context): BozokDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, BozokDatabase::class.java, "bozokpro.db")
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
