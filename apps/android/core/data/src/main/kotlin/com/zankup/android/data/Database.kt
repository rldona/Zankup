package com.zankup.android.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** An answer of the API as it was last seen, by its key (see [CacheKeys]). */
@Entity(tableName = "answers")
data class Answer(@PrimaryKey val key: String, val json: String, val savedAt: Long)

/** An activity's series (the heavy part): their own table, the latest [Store.KEPT_STREAMS] kept. */
@Entity(tableName = "streams")
data class Streams(@PrimaryKey val activityId: Long, val json: String, val savedAt: Long)

@Dao
interface AnswerDao {
    @Query("SELECT * FROM answers WHERE `key` = :key")
    fun observe(key: String): Flow<Answer?>

    @Query("SELECT * FROM answers WHERE `key` = :key")
    suspend fun get(key: String): Answer?

    @Upsert
    suspend fun put(answer: Answer)

    @Query("DELETE FROM answers WHERE savedAt < :before")
    suspend fun forgetOlderThan(before: Long)

    @Query("DELETE FROM answers")
    suspend fun clear()
}

@Dao
interface StreamsDao {
    @Query("SELECT * FROM streams WHERE activityId = :id")
    suspend fun get(id: Long): Streams?

    @Upsert
    suspend fun put(streams: Streams)

    @Query(
        "DELETE FROM streams WHERE activityId NOT IN (SELECT activityId FROM streams ORDER BY savedAt DESC LIMIT :kept)"
    )
    suspend fun keepLatest(kept: Int)

    @Query("DELETE FROM streams")
    suspend fun clear()
}

/**
 * What the phone keeps (OS-1120): a cache, never the only copy, so a new schema simply starts it again.
 * Excluded from backups; deleted when signing out.
 */
@Database(entities = [Answer::class, Streams::class], version = 1, exportSchema = false)
abstract class ZankupDatabase : RoomDatabase() {
    abstract fun answers(): AnswerDao

    abstract fun streams(): StreamsDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ZankupDatabase = Room
        .databaseBuilder(context, ZankupDatabase::class.java, "zankup.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}
