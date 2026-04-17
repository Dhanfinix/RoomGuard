package dev.dhanfinix.roomguard.sample.data

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM ${NoteDatabase.TABLE_NAME} ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM ${NoteDatabase.TABLE_NAME}")
    suspend fun clearAllNotes()

    @RawQuery
    suspend fun checkpoint(query: SupportSQLiteQuery): Int
}
