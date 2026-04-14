package dev.dhanfinix.roomguard.sample.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.dhanfinix.roomguard.sample.data.NoteDao
import dev.dhanfinix.roomguard.sample.data.NoteEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(private val dao: NoteDao) : ViewModel() {

    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val notes: StateFlow<List<NoteEntity>> = refreshTrigger
        .flatMapLatest { dao.getAllNotes() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    fun addNote(title: String, body: String) {
        viewModelScope.launch {
            dao.insert(NoteEntity(title = title, body = body))
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            dao.delete(note)
        }
    }

    class Factory(private val dao: NoteDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(dao) as T
    }
}
