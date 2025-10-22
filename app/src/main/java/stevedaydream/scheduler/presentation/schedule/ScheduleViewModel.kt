// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.presentation.schedule

import android.util.Log // 引入 Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import stevedaydream.scheduler.domain.scheduling.RotationRuleConfig // 引入 RotationRuleConfig
import stevedaydream.scheduler.domain.scheduling.RotationScheduler
import stevedaydream.scheduler.domain.scheduling.ScheduleGenerator // ✅ 1. 引入 ScheduleGenerator
// ✅ 引入 SchedulingStrategyType Enum
import stevedaydream.scheduler.domain.scheduling.SchedulingStrategyType
import javax.inject.Inject

// ✅ 將 SchedulingStrategy Enum 移到這裡或共用檔案，讓 ViewModel 也能訪問
//    或者直接在 ScheduleGenerator.kt 中定義並匯入 SchedulingStrategyType
enum class SchedulingStrategy(val displayName: String) {
    GENERAL("通用設定"),
    HOSPITAL("醫院設定")
}


@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth,
    private val scheduleGenerator: ScheduleGenerator, // ✅ 2. 確認 scheduleGenerator 已注入
    private val rotationScheduler: RotationScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val currentOrgId: String = savedStateHandle.get<String>("orgId")!!
    val currentGroupId: String = savedStateHandle.get<String>("groupId")!!

    // --- Existing States ---
    private val _group = MutableStateFlow<Group?>(null)
    val group: StateFlow<Group?> = _group.asStateFlow()
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    // ✅ _selectedStrategy 應使用 SchedulingStrategyType
    private val _selectedStrategy = MutableStateFlow(SchedulingStrategyType.HOSPITAL_GREEDY) // 預設值改為 Enum
    val selectedStrategy: StateFlow<SchedulingStrategyType> = _selectedStrategy.asStateFlow()

    // ... (其他狀態保持不變) ...
    val isScheduler: StateFlow<Boolean> = _group.map { group ->
        group?.schedulerId == auth.currentUser?.uid
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val canSchedule: StateFlow<Boolean> = _group.map { group ->
        group?.isSchedulerActive() == false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()
    private val _shiftTypes = MutableStateFlow<List<ShiftType>>(emptyList())
    val shiftTypes: StateFlow<List<ShiftType>> = _shiftTypes.asStateFlow()
    private val _requests = MutableStateFlow<List<Request>>(emptyList())
    val requests: StateFlow<List<Request>> = _requests.asStateFlow()
    private val _rules = MutableStateFlow<List<SchedulingRule>>(emptyList())
    val rules: StateFlow<List<SchedulingRule>> = _rules.asStateFlow()
    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()
    private val _generateSuccess = MutableSharedFlow<Unit>()
    val generateSuccess = _generateSuccess.asSharedFlow()
    private val _orderedUsers = MutableStateFlow<List<User>>(emptyList())
    val orderedUsers: StateFlow<List<User>> = _orderedUsers.asStateFlow()
    private val _rotationSettings = MutableStateFlow<RotationSettingsContainer?>(null)
    private val _isCalculatingRotation = MutableStateFlow(false)
    val isCalculatingRotation: StateFlow<Boolean> = _isCalculatingRotation.asStateFlow()
    private val _rotationCalculationResult = MutableSharedFlow<Result<Unit>>()
    val rotationCalculationResult: SharedFlow<Result<Unit>> = _rotationCalculationResult.asSharedFlow()
    private val _isPreviewGenerating = MutableStateFlow(false)
    val isPreviewGenerating: StateFlow<Boolean> = _isPreviewGenerating.asStateFlow()
    private val _previewRotationSchedule = MutableStateFlow<Map<String, Map<String, String>>?>(null)
    val previewRotationSchedule: StateFlow<Map<String, Map<String, String>>?> = _previewRotationSchedule.asStateFlow()
    private val _previewGeneratedSchedule = MutableStateFlow<ScheduleGenerator.ScheduleGenerationResult?>(null)
    val previewGeneratedSchedule: StateFlow<ScheduleGenerator.ScheduleGenerationResult?> = _previewGeneratedSchedule.asStateFlow()
    private val _previewError = MutableSharedFlow<String>()
    val previewError = _previewError.asSharedFlow()

    init {
        loadGroupData()
        loadRotationSettings()
    }

    private fun loadGroupData() {
        // ... (保持不變) ...
        auth.currentUser?.uid?.let { userId ->
            viewModelScope.launch {
                repository.observeUser(userId).collect { user ->
                    _currentUser.value = user
                }
            }
        }
        viewModelScope.launch {
            repository.observeGroup(currentGroupId).collect { groupData ->
                _group.value = groupData
                if (groupData?.schedulerId == auth.currentUser?.uid && groupData?.isSchedulerActive() == true) {
                    renewLease()
                }
            }
        }
        viewModelScope.launch {
            repository.observeSchedules(currentOrgId, currentGroupId).collect { scheduleList ->
                _schedules.value = scheduleList.sortedByDescending { it.month } // 確保排序
            }
        }
        viewModelScope.launch {
            combine(
                repository.observeGroup(currentGroupId).filterNotNull(),
                repository.observeUsers(currentOrgId)
            ) { group, allUsers ->
                val groupMembers = allUsers.filter { user -> user.id in group.memberIds }
                val userMap = groupMembers.associateBy { it.id }
                val ordered = group.userOrder?.mapNotNull { userId -> userMap[userId] } ?: groupMembers
                Pair(groupMembers, ordered)
            }.collect { (groupMembers, orderedGroupMembers) ->
                _users.value = groupMembers
                _orderedUsers.value = orderedGroupMembers
            }
        }
        viewModelScope.launch {
            repository.observeShiftTypes(currentOrgId, currentGroupId).collect { types ->
                _shiftTypes.value = types
            }
        }
        viewModelScope.launch {
            repository.observeRequests(currentOrgId).collect { reqs ->
                _requests.value = reqs
            }
        }
        viewModelScope.launch {
            repository.observeSchedulingRules(currentOrgId, currentGroupId).collect { ruleList ->
                _rules.value = ruleList
            }
        }
    }

    private fun loadRotationSettings() {
        // ... (保持不變) ...
        viewModelScope.launch {
            repository.observeRotationSettings(currentOrgId, currentGroupId).collect { settings ->
                _rotationSettings.value = settings
            }
        }
    }

    // ✅ selectStrategy 應使用 SchedulingStrategyType
    fun selectStrategy(strategy: SchedulingStrategyType) {
        _selectedStrategy.value = strategy
    }


    fun toggleReservation(month: String, currentStatus: String) {
        // ... (保持不變) ...
        viewModelScope.launch {
            val newStatus = when (currentStatus) {
                "inactive" -> "active"
                "active" -> "closed"
                "closed" -> "active"
                else -> "inactive"
            }
            val updateResult = repository.updateReservationStatus(currentOrgId, currentGroupId, month, newStatus)
            if (currentStatus == "inactive" && newStatus == "active" && updateResult.isSuccess) {
                calculateAndSaveRotations(month)
            }
        }
    }

    fun calculateAndSaveRotations(month: String) {
        // ... (保持不變) ...
        viewModelScope.launch {
            _isCalculatingRotation.value = true
            try {
                val groupData = _group.value
                val ordered = _orderedUsers.value
                val shifts = _shiftTypes.value
                val settings = _rotationSettings.value?.rules ?: emptyMap()
                if (groupData == null || ordered.isEmpty() || shifts.isEmpty() || settings.isEmpty()) {
                    _rotationCalculationResult.emit(Result.failure(Exception("缺少計算輪替的必要資料")))
                    return@launch
                }
                val rotationRulesConfig = settings.mapValues { (shiftId, setting) ->
                    RotationRuleConfig(shiftTypeId = shiftId, daysOfWeek = setting.daysOfWeek)
                }
                val initialRotationState = groupData.rotationState ?: emptyMap()
                val result = rotationScheduler.calculateRotations(month, ordered, shifts, rotationRulesConfig, initialRotationState)
                val saveScheduleResult = repository.saveRotationSchedule(currentOrgId, currentGroupId, month, result.preScheduledRotations)
                if (saveScheduleResult.isFailure) throw saveScheduleResult.exceptionOrNull()!!
                val updateGroupResult = repository.updateGroup(currentOrgId, currentGroupId, mapOf("rotationState" to result.nextRotationState))
                if (updateGroupResult.isFailure) throw updateGroupResult.exceptionOrNull()!!
                _rotationCalculationResult.emit(Result.success(Unit))
            } catch (e: Exception) {
                Log.e("ScheduleVM", "計算或儲存輪替失敗", e)
                _rotationCalculationResult.emit(Result.failure(e))
            } finally {
                _isCalculatingRotation.value = false
            }
        }
    }

    // ... (claimScheduler, addSchedulerToGroup, releaseScheduler, renewLease 保持不變) ...
    fun claimScheduler() {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch
            repository.claimScheduler(currentOrgId, currentGroupId, currentUser.uid, currentUser.displayName ?: currentUser.email ?: "未命名")
        }
    }
    fun addSchedulerToGroup() {
        viewModelScope.launch {
            auth.currentUser?.uid?.let { userId ->
                repository.addUserToGroupAndOrg(currentOrgId, currentGroupId, userId)
            }
        }
    }
    fun releaseScheduler() {
        viewModelScope.launch {
            repository.releaseScheduler(currentOrgId, currentGroupId)
        }
    }
    private fun renewLease() {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch
            repository.renewSchedulerLease(currentOrgId, currentGroupId, currentUser.uid)
        }
    }

    // --- ✅ 修改 generatePreviewSchedule ---
    fun generatePreviewSchedule(month: String) {
        viewModelScope.launch {
            _isPreviewGenerating.value = true
            _previewGeneratedSchedule.value = null
            _previewRotationSchedule.value = null
            var calculatedRotations: Map<String, Map<String, String>>? = null

            try {
                // Step 1: Calculate rotations (if hospital strategy)
                if (_selectedStrategy.value == SchedulingStrategyType.HOSPITAL_GREEDY || _selectedStrategy.value == SchedulingStrategyType.HOSPITAL_BACKTRACKING) { // Check both hospital types
                    // ... (rotation calculation logic remains the same) ...
                    val groupData = _group.value
                    val ordered = _orderedUsers.value
                    val shifts = _shiftTypes.value
                    val settings = _rotationSettings.value?.rules ?: emptyMap()
                    if (groupData == null || ordered.isEmpty() || shifts.isEmpty() || settings.isEmpty()) {
                        throw Exception("醫院策略缺少輪替計算資料")
                    }
                    val rotationRulesConfig = settings.mapValues { (shiftId, setting) -> RotationRuleConfig(shiftTypeId = shiftId, daysOfWeek = setting.daysOfWeek) }
                    val initialRotationState = groupData.rotationState ?: emptyMap()
                    val rotationResult = rotationScheduler.calculateRotations(month, ordered, shifts, rotationRulesConfig, initialRotationState)
                    calculatedRotations = rotationResult.preScheduledRotations
                    _previewRotationSchedule.value = calculatedRotations
                } else {
                    calculatedRotations = emptyMap()
                    _previewRotationSchedule.value = calculatedRotations
                }

                // Step 2: Generate schedule using the calculated rotations
                val manpowerPlan = repository.getManpowerPlanOnce(currentOrgId, currentGroupId, month)
                val reservations = repository.observeReservations(currentOrgId, currentGroupId, month).first()
                val enabledRules = _rules.value.filter { it.isEnabled }

                val generatorResult = scheduleGenerator.generateSchedule(
                    orgId = currentOrgId,
                    groupId = currentGroupId,
                    month = month,
                    users = _users.value,
                    shiftTypes = _shiftTypes.value,
                    requests = _requests.value,
                    reservations = reservations,
                    rules = enabledRules,
                    manpowerPlan = manpowerPlan,
                    // ✅ 直接傳遞 Enum
                    strategy = _selectedStrategy.value,
                    orderedUsers = if (_selectedStrategy.value == SchedulingStrategyType.HOSPITAL_GREEDY || _selectedStrategy.value == SchedulingStrategyType.HOSPITAL_BACKTRACKING) _orderedUsers.value else null,
                    preScheduledRotations = calculatedRotations
                )
                _previewGeneratedSchedule.value = generatorResult

            } catch (e: Exception) {
                Log.e("ScheduleVM", "預覽生成失敗 (${_selectedStrategy.value})", e)
                _previewError.emit("預覽生成失敗: ${e.message}")
            } finally {
                _isPreviewGenerating.value = false
            }
        }
    }

    // ... (savePreviewSchedule 保持不變) ...
    fun savePreviewSchedule() {
        val previewResult = _previewGeneratedSchedule.value ?: return
        viewModelScope.launch {
            _isGenerating.value = true
            _previewGeneratedSchedule.value = null
            _previewRotationSchedule.value = null
            try {
                repository.createScheduleAndAssignments(currentOrgId, previewResult.schedule, previewResult.assignments).getOrThrow()
                _generateSuccess.emit(Unit)
            } catch (e: Exception) {
                Log.e("ScheduleVM", "儲存預覽班表失敗", e)
                _previewError.emit("儲存班表失敗: ${e.message}")
            } finally {
                _isGenerating.value = false
            }
        }
    }

    // ... (clearPreview 保持不變) ...
    fun clearPreview() {
        _previewGeneratedSchedule.value = null
        _previewRotationSchedule.value = null
    }

    // --- ✅ 修改 generateSmartSchedule ---
    fun generateSmartSchedule(month: String) {
        viewModelScope.launch {
            _isGenerating.value = true
            try {
                // Common data needed
                val manpowerPlan = repository.getManpowerPlanOnce(currentOrgId, currentGroupId, month)
                val reservations = repository.observeReservations(currentOrgId, currentGroupId, month).first()
                val enabledRules = _rules.value.filter { it.isEnabled }
                var rotationScheduleData: Map<String, Map<String, String>>? = null

                // Read rotation schedule if hospital strategy
                if (_selectedStrategy.value == SchedulingStrategyType.HOSPITAL_GREEDY || _selectedStrategy.value == SchedulingStrategyType.HOSPITAL_BACKTRACKING) { // Check both hospital types
                    rotationScheduleData = try {
                        repository.observeRotationSchedule(currentOrgId, currentGroupId, month).first()
                    } catch (e: Exception) {
                        Log.w("ScheduleVM", "無法讀取 RotationSchedule: ${e.message}, 使用空資料。")
                        emptyMap()
                    }
                }

                // Call generator with selected strategy
                val result = scheduleGenerator.generateSchedule(
                    orgId = currentOrgId,
                    groupId = currentGroupId,
                    month = month,
                    users = _users.value,
                    shiftTypes = _shiftTypes.value,
                    requests = _requests.value,
                    reservations = reservations,
                    rules = enabledRules,
                    manpowerPlan = manpowerPlan,
                    // ✅ 直接傳遞 Enum
                    strategy = _selectedStrategy.value,
                    orderedUsers = if (_selectedStrategy.value == SchedulingStrategyType.HOSPITAL_GREEDY || _selectedStrategy.value == SchedulingStrategyType.HOSPITAL_BACKTRACKING) _orderedUsers.value else null,
                    preScheduledRotations = rotationScheduleData
                )

                // Save the result
                repository.createScheduleAndAssignments(currentOrgId, result.schedule, result.assignments).getOrThrow()
                _generateSuccess.emit(Unit)

            } catch (e: Exception) {
                Log.e("ScheduleVM", "智慧排班生成或儲存失敗 (${_selectedStrategy.value})", e)
                // Emit error or update UI state
            } finally {
                _isGenerating.value = false
            }
        }
    }

    // ... (deleteSchedule 保持不變) ...
    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            repository.deleteSchedule(currentOrgId, scheduleId).onFailure {
                println("❌ 刪除班表失敗: ${it.message}")
            }
        }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
