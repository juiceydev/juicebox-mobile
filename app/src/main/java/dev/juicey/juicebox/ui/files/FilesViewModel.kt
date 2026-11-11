package dev.juicey.juicebox.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.juicey.juicebox.AppContainer
import dev.juicey.juicebox.core.friendlyMessage
import dev.juicey.juicebox.core.model.LibraryFile
import dev.juicey.juicebox.core.model.LinkVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FilesUiState(
    val files: List<LibraryFile> = emptyList(),
    val total: Int = 0,
    val totalBytes: Long = 0,
    val query: String = "",
    val refreshing: Boolean = false,
    val linkVariant: String = LinkVariant.PREVIEW,
    val error: String? = null,
)

class FilesViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.core.library.collect { all ->
                val server = container.core.currentServer()
                val mine = all
                    .filter { it.server == server }
                    .sortedWith(
                        compareByDescending<LibraryFile> { it.isPending }
                            .thenByDescending { it.uploadedAt },
                    )
                apply(mine)
            }
        }
        viewModelScope.launch {
            container.core.settings.collect { settings ->
                _state.update { it.copy(linkVariant = settings.linkVariant) }
            }
        }
        refresh()
    }

    private fun apply(files: List<LibraryFile>) {
        val query = _state.value.query
        val filtered = if (query.isBlank()) {
            files
        } else {
            files.filter { it.filename.contains(query, ignoreCase = true) }
        }
        _state.update {
            it.copy(
                files = filtered,
                total = files.size,
                totalBytes = files.sumOf { file -> file.sizeBytes },
            )
        }
    }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
        val server = container.core.currentServer()
        apply(container.core.library.value.filter { it.server == server }.sortedWith(
            compareByDescending<LibraryFile> { it.isPending }.thenByDescending { it.uploadedAt },
        ))
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            runCatching { container.core.refreshLibrary() }
                .onFailure { error ->
                    _state.update { it.copy(error = error.friendlyMessage()) }
                }
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun delete(file: LibraryFile) {
        viewModelScope.launch {
            runCatching { container.core.deleteFile(file.localId) }
                .onFailure { error -> _state.update { it.copy(error = error.friendlyMessage()) } }
        }
    }

    fun rename(file: LibraryFile, customId: String) {
        viewModelScope.launch {
            runCatching {
                container.core.renameFile(file.localId, customId.trim().takeIf { it.isNotEmpty() })
            }.onFailure { error ->
                _state.update { it.copy(error = error.friendlyMessage()) }
            }
        }
    }

    fun copy(link: String): Boolean = container.runner.copyLink(link)

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}
