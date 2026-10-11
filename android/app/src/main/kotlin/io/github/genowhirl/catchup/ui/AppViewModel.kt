package io.github.genowhirl.catchup.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.genowhirl.catchup.core.Op
import io.github.genowhirl.catchup.core.Person
import io.github.genowhirl.catchup.data.AppSettings
import io.github.genowhirl.catchup.data.Directory
import io.github.genowhirl.catchup.data.Storage
import io.github.genowhirl.catchup.repository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.repository

    val directory: StateFlow<Directory> = repo.directory
    val settings: StateFlow<AppSettings> = repo.settings
    val sync = repo.sync

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Short confirmations for a snackbar ("Logged call with Maya"). */
    val messages: SharedFlow<String> = _messages

    fun person(id: String): Person? = directory.value.people.firstOrNull { it.id == id }

    fun apply(vararg ops: Op, message: String? = null, then: (List<String>) -> Unit = {}) {
        viewModelScope.launch {
            val paths = repo.apply(*ops)
            message?.let { _messages.tryEmit(it) }
            then(paths)
        }
    }

    fun say(message: String) {
        _messages.tryEmit(message)
    }

    fun syncNow() = viewModelScope.launch { repo.syncNow() }

    fun updateSettings(change: (AppSettings) -> AppSettings) = viewModelScope.launch { repo.updateSettings(change) }

    fun switchStorage(storage: Storage, carry: Boolean, done: () -> Unit = {}) = viewModelScope.launch {
        repo.switchStorage(storage, carry)
        done()
    }

    fun files(): Map<String, String> = repo.files()
}
