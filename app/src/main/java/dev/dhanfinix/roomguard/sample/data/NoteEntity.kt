package dev.dhanfinix.roomguard.sample.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = NoteDatabase.TABLE_NAME)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis()
)
