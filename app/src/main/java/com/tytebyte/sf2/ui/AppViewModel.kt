package com.tytebyte.sf2.ui

import android.app.Application
import android.content.Context
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tytebyte.sf2.data.ImageItem
import com.tytebyte.sf2.data.MediaRepository
import com.tytebyte.sf2.data.Scanner
import com.tytebyte.sf2.engine.Candidate
import com.tytebyte.sf2.engine.DuplicateFinder
import com.tytebyte.sf2.engine.MatchKind
import com.tytebyte.sf2.engine.Sensitivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Scanning : Screen
    data object Results : Screen
    data class Group(val key: String) : Screen
    data class Viewer(val key: String, val index: Int) : Screen
}

/** Группа повторов. [members] отсортированы: первым идёт лучший по качеству кадр. */
data class DupGroup(val key: String, val members: List<ImageItem>, val kind: MatchKind) {
    val reclaimable: Long get() = members.drop(1).sumOf { it.size }
}

data class UiState(
    val screen: Screen = Screen.Home,
    val sensitivity: Sensitivity = Sensitivity.BALANCED,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val groups: List<DupGroup> = emptyList(),
    val totalImages: Int = 0,
    val failedCount: Int = 0,
    val selected: Set<Long> = emptySet(),
    val dismissedCount: Int = 0,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val resolver = app.contentResolver
    private val repo = MediaRepository(resolver)
    private val prefs = app.getSharedPreferences("sf2", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val trashChannel = Channel<IntentSenderRequest>(Channel.BUFFERED)
    val trashRequests = trashChannel.receiveAsFlow()

    private var items: Map<Long, ImageItem> = emptyMap()
    private var candidates: List<Candidate> = emptyList()
    private var scanJob: Job? = null
    private var pendingTrash: List<ImageItem> = emptyList()

    private val bestFirst = compareByDescending<ImageItem> { it.pixels }
        .thenByDescending { it.size }
        .thenBy { it.dateAdded }

    fun group(key: String): DupGroup? = _state.value.groups.firstOrNull { it.key == key }

    // ---------- сканирование (только чтение) ----------

    fun startScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update { it.copy(screen = Screen.Scanning, progressDone = 0, progressTotal = 0, selected = emptySet()) }
            try {
                val list = withContext(Dispatchers.IO) { repo.loadImages() }
                if (list.isEmpty()) {
                    messages.tryEmit("В галерее не найдено фото (или нет доступа)")
                    _state.update { it.copy(screen = Screen.Home) }
                    return@launch
                }
                _state.update { it.copy(progressTotal = list.size) }
                val out = Scanner(resolver).scan(list) { done, total ->
                    _state.update { it.copy(progressDone = done, progressTotal = total) }
                }
                items = out.items
                candidates = out.candidates.sortedWith(
                    compareByDescending<Candidate> { items[it.id]?.pixels ?: 0L }.thenByDescending { items[it.id]?.size ?: 0L },
                )
                _state.update { it.copy(totalImages = list.size, failedCount = out.failed.size, screen = Screen.Results) }
                regroup()
            } catch (e: CancellationException) {
                _state.update { it.copy(screen = Screen.Home) }
                throw e
            } catch (e: Exception) {
                messages.tryEmit("Не удалось проверить галерею: ${e.message ?: e::class.java.simpleName}")
                _state.update { it.copy(screen = Screen.Home) }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    fun setSensitivity(s: Sensitivity) {
        _state.update { it.copy(sensitivity = s) }
        if (candidates.isNotEmpty()) regroup()
    }

    private fun dismissed(): Set<String> = prefs.getStringSet("dismissed", emptySet()) ?: emptySet()

    private fun regroup() {
        val dismissed = dismissed()
        val all = DuplicateFinder.group(candidates, _state.value.sensitivity).map { rg ->
            DupGroup(
                key = rg.ids.sorted().joinToString("-"),
                members = rg.ids.mapNotNull { items[it] }.sortedWith(bestFirst),
                kind = rg.kind,
            )
        }.filter { it.members.size > 1 }
        val visible = all.filter { it.key !in dismissed }.sortedByDescending { it.reclaimable }
        _state.update { st ->
            st.copy(
                groups = visible,
                dismissedCount = all.size - visible.size,
                selected = st.selected.filter { it in items }.toSet(),
            )
        }
    }

    // ---------- навигация ----------

    fun openGroup(key: String) = _state.update { it.copy(screen = Screen.Group(key)) }
    fun openViewer(key: String, index: Int) = _state.update { it.copy(screen = Screen.Viewer(key, index)) }
    fun showViewerIndex(key: String, index: Int) = openViewer(key, index)

    fun back() {
        when (val s = _state.value.screen) {
            is Screen.Viewer -> _state.update { it.copy(screen = Screen.Group(s.key)) }
            is Screen.Group -> _state.update { it.copy(screen = Screen.Results) }
            Screen.Results -> _state.update { it.copy(screen = Screen.Home) }
            Screen.Scanning -> cancelScan()
            Screen.Home -> Unit
        }
    }

    // ---------- выбор файлов (ничего не отмечено само) ----------

    fun toggleSelect(groupKey: String, id: Long) {
        val g = group(groupKey) ?: return
        val sel = _state.value.selected
        if (id in sel) {
            _state.update { it.copy(selected = it.selected - id) }
        } else {
            val keptAfter = g.members.count { it.id !in sel && it.id != id }
            if (keptAfter < 1) {
                messages.tryEmit("Нельзя отметить все: в группе должна остаться хотя бы одна копия")
                return
            }
            _state.update { it.copy(selected = it.selected + id) }
        }
    }

    /** Подсказка: отметить всё, кроме лучшего по качеству. Только по кнопке, пользователь может изменить. */
    fun suggestSelection(groupKey: String) {
        val g = group(groupKey) ?: return
        val ids = g.members.drop(1).map { it.id }.toSet()
        _state.update { st -> st.copy(selected = (st.selected - g.members.map { it.id }.toSet()) + ids) }
    }

    fun clearSelection(groupKey: String) {
        val g = group(groupKey) ?: return
        val ids = g.members.map { it.id }.toSet()
        _state.update { it.copy(selected = it.selected - ids) }
    }

    fun dismissGroup(key: String) {
        val set = dismissed().toMutableSet().apply { add(key) }
        prefs.edit().putStringSet("dismissed", set).apply()
        regroup()
        _state.update { it.copy(screen = Screen.Results) }
        messages.tryEmit("Группа скрыта. Вернуть скрытые можно на экране результатов")
    }

    fun resetDismissed() {
        prefs.edit().remove("dismissed").apply()
        regroup()
    }

    // ---------- перемещение в корзину ----------

    fun requestTrash(groupKey: String) {
        val g = group(groupKey) ?: return
        val chosen = g.members.filter { it.id in _state.value.selected }
        if (chosen.isEmpty()) return
        if (chosen.size >= g.members.size) {
            messages.tryEmit("В группе должна остаться хотя бы одна копия")
            return
        }
        try {
            pendingTrash = chosen
            trashChannel.trySend(repo.createTrashRequest(chosen))
        } catch (e: Exception) {
            pendingTrash = emptyList()
            messages.tryEmit("Не удалось запросить перемещение в корзину: ${e.message ?: "ошибка"}")
        }
    }

    fun onTrashResult(ok: Boolean) {
        val moved = pendingTrash
        pendingTrash = emptyList()
        if (moved.isEmpty()) return
        if (!ok) {
            messages.tryEmit("Отменено. Файлы не тронуты")
            return
        }
        viewModelScope.launch {
            // Не верим на слово: проверяем, что файлы действительно ушли из галереи.
            val still = withContext(Dispatchers.IO) { repo.stillPresent(moved.map { it.id }) }
            val gone = moved.filter { it.id !in still }
            val goneIds = gone.map { it.id }.toSet()
            items = items - goneIds
            candidates = candidates.filter { it.id !in goneIds }
            _state.update { it.copy(selected = it.selected - goneIds, totalImages = it.totalImages - gone.size) }
            regroup()
            val cur = _state.value.screen
            if (cur is Screen.Group && group(cur.key) == null || cur is Screen.Viewer) {
                _state.update { it.copy(screen = Screen.Results) }
            }
            messages.tryEmit(
                if (still.isEmpty()) "В корзину перемещено: ${files(gone.size)}. Вернуть можно из корзины в галерее"
                else "В корзину перемещено ${gone.size}, не удалось: ${still.size}",
            )
        }
    }
}
