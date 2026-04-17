package dev.dhanfinix.roomguard.sample.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NoteEntity::class], version = 1, exportSchema = false)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        const val TABLE_NAME = "notes"
        const val DB_FILE_NAME = "notes.db"

        fun getInstance(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    DB_FILE_NAME
                ).build().also { INSTANCE = it }
            }
        }
    }
}
