package stevedaydream.scheduler.presentation.schedule

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
            // 監聽班表
            repository.observeSchedule(scheduleId).collect { schedule ->
                _uiState.update { it.copy(schedule = schedule) }
            }
        }

        viewModelScope.launch {
            // 監聽班表分配
            repository.observeAssignments(scheduleId).collect { assignments ->
                _uiState.update { it.copy(assignments = assignments) }
            }
        }

        // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
        viewModelScope.launch {
            // 獲取班別類型 (持續監聽)
            repository.observeShiftTypes(orgId, groupId).collect { shiftTypes ->
                _uiState.update { it.copy(shiftTypes = shiftTypes) }
            }
        }
        // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

        viewModelScope.launch {
            // 結合使用者和群組資料
            repository.observeGroup(groupId)
                .filterNotNull()
                .flatMapLatest { group ->
                    repository.observeUsers(orgId).map { allUsers ->
                        allUsers.filter { it.id in group.memberIds }
                    }
                }
                .collect { usersInGroup ->
                    _uiState.update { it.copy(users = usersInGroup, isLoading = false) }
                }
        }
    }
}