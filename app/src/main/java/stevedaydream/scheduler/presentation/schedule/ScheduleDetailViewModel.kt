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

@HiltViewModel
class ScheduleDetailViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val orgId: String = savedStateHandle.get<String>("orgId")!!
    private val groupId: String = savedStateHandle.get<String>("groupId")!!
    private val scheduleId: String = savedStateHandle.get<String>("scheduleId")!!

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _schedule = MutableStateFlow<Schedule?>(null)
    val schedule: StateFlow<Schedule?> = _schedule.asStateFlow()

    private val _assignments = MutableStateFlow<List<Assignment>>(emptyList())
    val assignments: StateFlow<List<Assignment>> = _assignments.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _shiftTypes = MutableStateFlow<List<ShiftType>>(emptyList())
    val shiftTypes: StateFlow<List<ShiftType>> = _shiftTypes.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            // 監聽班表
            repository.observeSchedule(scheduleId).collect {
                _schedule.value = it
            }
        }

        viewModelScope.launch {
            // 監聽班表分配
            repository.observeAssignments(scheduleId).collect {
                _assignments.value = it
            }
        }

        viewModelScope.launch {
            // 獲取班別類型
            repository.observeShiftTypes(orgId, groupId).firstOrNull()?.let {
                _shiftTypes.value = it
            }
        }

        viewModelScope.launch {
            // 獲取群組成員
            val group = repository.observeGroup(groupId).firstOrNull()
            if (group != null) {
                val allUsers = repository.observeUsers(orgId).firstOrNull() ?: emptyList()
                _users.value = allUsers.filter { it.id in group.memberIds }
            }
            _isLoading.value = false
        }
    }
}