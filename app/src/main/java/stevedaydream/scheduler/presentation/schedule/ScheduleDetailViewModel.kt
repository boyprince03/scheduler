package stevedaydream.scheduler.presentation.schedule

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import javax.inject.Inject

// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
data class ScheduleDetailUiState(
    val isLoading: Boolean = true,
    val schedule: Schedule? = null,
    val assignments: List<Assignment> = emptyList(),
    val users: List<User> = emptyList(),
    val shiftTypes: List<ShiftType> = emptyList()
)
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

@HiltViewModel
class ScheduleDetailViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val orgId: String = savedStateHandle.get<String>("orgId")!!
    private val groupId: String = savedStateHandle.get<String>("groupId")!!
    private val scheduleId: String = savedStateHandle.get<String>("scheduleId")!!

    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    private val _uiState = MutableStateFlow(ScheduleDetailUiState())
    val uiState: StateFlow<ScheduleDetailUiState> = _uiState.asStateFlow()
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val scheduleFlow = repository.observeSchedule(scheduleId)
            // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改此處函式呼叫 ▼▼▼▼▼▼▼▼▼▼▼▼
            val assignmentsFlow = repository.observeAssignments(orgId, scheduleId)
            // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
            val shiftTypesFlow = repository.observeShiftTypes(orgId, groupId)
            val usersFlow = repository.observeGroup(groupId)
                .filterNotNull()
                .flatMapLatest { group ->
                    repository.observeUsers(orgId).map { allUsers ->
                        allUsers.filter { it.id in group.memberIds }
                    }
                }

            combine(
                scheduleFlow,
                assignmentsFlow,
                shiftTypesFlow,
                usersFlow
            ) { schedule, assignments, shiftTypes, users ->
                ScheduleDetailUiState(
                    isLoading = false,
                    schedule = schedule,
                    assignments = assignments,
                    shiftTypes = shiftTypes,
                    users = users
                )
            }.catch { e ->
                // ... (省略錯誤處理) ...
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }
}