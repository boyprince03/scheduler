// scheduler/presentation/schedule/ManualScheduleViewModel.kt

package stevedaydream.scheduler.presentation.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import stevedaydream.scheduler.util.DateUtils
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ManualScheduleViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val currentOrgId: String = savedStateHandle.get<String>("orgId")!!
    private val currentGroupId: String = savedStateHandle.get<String>("groupId")!!
    val currentMonth: String = savedStateHandle.get<String>("month")!!

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _shiftTypes = MutableStateFlow<List<ShiftType>>(emptyList())
    val shiftTypes: StateFlow<List<ShiftType>> = _shiftTypes.asStateFlow()

    private val _assignments = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val assignments: StateFlow<Map<String, Map<String, String>>> = _assignments.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saveSuccess = MutableSharedFlow<Unit>()
    val saveSuccess = _saveSuccess.asSharedFlow()

    init {
        loadData()
    }

    private fun loadData() {
        _isLoading.value = true

        viewModelScope.launch {
            repository.observeGroup(currentGroupId).collect { group ->
                group?.let {
                    repository.observeUsers(currentOrgId).collect { allUsers ->
                        _users.value = allUsers.filter { it.id in group.memberIds }
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.observeShiftTypes(currentOrgId, currentGroupId).collect { types ->
                _shiftTypes.value = types
            }
        }

        viewModelScope.launch {
            combine(users, shiftTypes) { userList, shiftList ->
                Pair(userList, shiftList)
            }.filter { (userList, shiftList) ->
                userList.isNotEmpty() && shiftList.isNotEmpty()
            }.take(1)
                .collect { (userList, _) ->
                    val dates = DateUtils.getDatesInMonth(currentMonth)
                    val initialAssignments = userList.associate { user ->
                        user.id to dates.associate { date ->
                            date.split("-").last() to ""
                        }
                    }
                    _assignments.value = initialAssignments
                    _isLoading.value = false
                }
        }
    }

    fun updateAssignment(userId: String, day: String, shiftId: String) {
        val current = _assignments.value.toMutableMap()
        val userAssignments = current[userId]?.toMutableMap() ?: mutableMapOf()
        userAssignments[day] = shiftId
        current[userId] = userAssignments
        _assignments.value = current
    }

    fun saveSchedule() {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val schedule = Schedule(
                    id = UUID.randomUUID().toString(),
                    orgId = currentOrgId,
                    groupId = currentGroupId,
                    month = currentMonth,
                    status = "published",
                    generatedAt = Date(),
                    totalScore = 0,
                    violatedRules = emptyList()
                )

                val assignmentsList = _users.value.map { user ->
                    Assignment(
                        id = UUID.randomUUID().toString(),
                        scheduleId = schedule.id,
                        userId = user.id,
                        userName = user.name,
                        dailyShifts = _assignments.value[user.id] ?: emptyMap()
                    )
                }

                repository.createSchedule(currentOrgId, schedule)
                assignmentsList.forEach { assignment ->
                    repository.createAssignment(currentOrgId, schedule.id, assignment)
                }

                _saveSuccess.emit(Unit)
            } catch (e: Exception) {
                println("儲存失敗: ${e.message}")
            } finally {
                _isSaving.value = false
            }
        }
    }
}