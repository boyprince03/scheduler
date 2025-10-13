// 修改開始
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
    // 保持 SavedStateHandle 的注入
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // --- 這些是非可選參數，保持不變 ---
    private val currentOrgId: String = savedStateHandle.get<String>("orgId")!!
    private val currentGroupId: String = savedStateHandle.get<String>("groupId")!!
    val currentMonth: String = savedStateHandle.get<String>("month")!!

    // --- 這是我們要修改的地方 ---
    private val scheduleIdToEdit: String?
    val isEditMode: Boolean

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

    private var originalSchedule: Schedule? = null
    private var originalAssignments: List<Assignment> = emptyList()

    // --- 將可選參數的初始化邏輯移到 init 區塊中 ---
    init {
        scheduleIdToEdit = savedStateHandle.get<String>("scheduleId")
        isEditMode = scheduleIdToEdit != null
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
                .collect {
                    if (isEditMode && scheduleIdToEdit != null) {
                        originalSchedule = repository.observeSchedule(scheduleIdToEdit).firstOrNull()
                        // 這裡需要 orgId，我們從 currentOrgId 取得
                        originalAssignments = repository.observeAssignments(currentOrgId, scheduleIdToEdit).firstOrNull() ?: emptyList()

                        val initialAssignments = originalAssignments.associate { assignment ->
                            assignment.userId to assignment.dailyShifts
                        }
                        _assignments.value = initialAssignments
                    } else {
                        val dates = DateUtils.getDatesInMonth(currentMonth)
                        val initialAssignments = _users.value.associate { user ->
                            user.id to dates.associate { date ->
                                date.split("-").last() to ""
                            }
                        }
                        _assignments.value = initialAssignments
                    }
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
                if (isEditMode && originalSchedule != null) {
                    // ==================== 更新模式 (邏輯不變) ====================
                    val updatedSchedule = originalSchedule!!.copy(
                        status = "published",
                        generatedAt = Date()
                    )
                    val updatedAssignments = _users.value.map { user ->
                        val existingAssignment = originalAssignments.find { it.userId == user.id }
                        val newShifts = _assignments.value[user.id] ?: emptyMap()
                        if (existingAssignment != null) {
                            existingAssignment.copy(dailyShifts = newShifts)
                        } else {
                            Assignment(
                                id = UUID.randomUUID().toString(),
                                scheduleId = updatedSchedule.id,
                                userId = user.id,
                                userName = user.name,
                                dailyShifts = newShifts
                            )
                        }
                    }
                    repository.updateScheduleAndAssignments(currentOrgId, updatedSchedule, updatedAssignments)

                } else {
                    // ==================== 新增模式 (使用新的批次寫入邏輯) ====================
                    // 1. 在客戶端預先產生所有 ID
                    val newScheduleId = UUID.randomUUID().toString()

                    val schedule = Schedule(
                        id = newScheduleId, // 使用預產生的 ID
                        orgId = currentOrgId,
                        groupId = currentGroupId,
                        month = currentMonth,
                        status = "published",
                        generatedAt = Date(),
                        totalScore = 0,
                        violatedRules = emptyList(),
                        generationMethod = "manual"
                    )

                    val assignmentsList = _users.value.map { user ->
                        Assignment(
                            id = UUID.randomUUID().toString(),
                            scheduleId = newScheduleId, // 使用相同的 ID
                            userId = user.id,
                            userName = user.name,
                            dailyShifts = _assignments.value[user.id] ?: emptyMap()
                        )
                    }

                    // 2. 呼叫新的 Repository 函式，一次性寫入所有資料
                    repository.createScheduleAndAssignments(currentOrgId, schedule, assignmentsList)
                }

                _saveSuccess.emit(Unit)
            } catch (e: Exception) {
                println("儲存失敗: ${e.message}")
                // 可以在此處更新 UI state 來顯示錯誤訊息
            } finally {
                _isSaving.value = false
            }
        }
    }
}
// 修改結束