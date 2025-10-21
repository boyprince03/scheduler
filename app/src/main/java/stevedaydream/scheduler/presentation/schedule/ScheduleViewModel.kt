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
import javax.inject.Inject

// ... (SchedulingStrategy Enum 保持不變) ...
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

    // --- Existing States (保持不變) ---
    private val _group = MutableStateFlow<Group?>(null)
    val group: StateFlow<Group?> = _group.asStateFlow()
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    private val _selectedStrategy = MutableStateFlow(SchedulingStrategy.GENERAL)
    val selectedStrategy: StateFlow<SchedulingStrategy> = _selectedStrategy.asStateFlow()
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

    // --- ✅ 3. 新增預覽相關狀態 ---
    private val _isPreviewGenerating = MutableStateFlow(false)
    val isPreviewGenerating: StateFlow<Boolean> = _isPreviewGenerating.asStateFlow()

    // 儲存預覽計算出的輪班結果 (不含 group state 更新)
    private val _previewRotationSchedule = MutableStateFlow<Map<String, Map<String, String>>?>(null)
    val previewRotationSchedule: StateFlow<Map<String, Map<String, String>>?> = _previewRotationSchedule.asStateFlow()

    // 儲存預覽生成的完整班表結果
    private val _previewGeneratedSchedule = MutableStateFlow<ScheduleGenerator.ScheduleGenerationResult?>(null)
    val previewGeneratedSchedule: StateFlow<ScheduleGenerator.ScheduleGenerationResult?> = _previewGeneratedSchedule.asStateFlow()

    // 用於顯示預覽錯誤訊息
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
                _schedules.value = scheduleList
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


    fun selectStrategy(strategy: SchedulingStrategy) {
        // ... (保持不變) ...
        _selectedStrategy.value = strategy
    }

    fun toggleReservation(month: String, currentStatus: String) {
        // ... (保持不變, 觸發 calculateAndSaveRotations) ...
        viewModelScope.launch {
            val newStatus = when (currentStatus) {
                "inactive" -> "active"
                "active" -> "closed"
                "closed" -> "active"
                else -> "inactive"
            }
            // 先更新狀態
            val updateResult = repository.updateReservationStatus(currentOrgId, currentGroupId, month, newStatus)

            // 如果是啟動預約 (inactive -> active) 且更新成功，則觸發輪替計算
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
                    Log.w("ScheduleVM", "計算輪替缺少必要資料 (group, users, shifts, or settings)")
                    _rotationCalculationResult.emit(Result.failure(Exception("缺少必要資料")))
                    return@launch
                }

                // 將 RotationSettingsContainer 轉換為 RotationScheduler 需要的 Map<String, RotationRuleConfig>
                val rotationRulesConfig = settings.mapValues { (shiftId, setting) ->
                    stevedaydream.scheduler.domain.scheduling.RotationRuleConfig(shiftTypeId = shiftId, daysOfWeek = setting.daysOfWeek)
                }

                val initialRotationState = groupData.rotationState ?: emptyMap()


                // 執行計算
                val result = rotationScheduler.calculateRotations(
                    month = month,
                    orderedUsers = ordered,
                    shiftTypes = shifts,
                    rotationRules = rotationRulesConfig,
                    initialRotationState = initialRotationState
                )

                // 儲存預排班結果
                val saveScheduleResult = repository.saveRotationSchedule(
                    currentOrgId,
                    currentGroupId,
                    month,
                    result.preScheduledRotations
                )

                if (saveScheduleResult.isFailure) {
                    throw saveScheduleResult.exceptionOrNull() ?: Exception("儲存輪替班表失敗")
                }

                // 更新 Group 的 rotationState (下個月的起始狀態)
                val updateGroupResult = repository.updateGroup(
                    currentOrgId,
                    currentGroupId,
                    mapOf("rotationState" to result.nextRotationState) // 直接更新 rotationState 欄位
                )

                if (updateGroupResult.isFailure) {
                    throw updateGroupResult.exceptionOrNull() ?: Exception("更新輪替狀態失敗")
                }

                _rotationCalculationResult.emit(Result.success(Unit)) // 發送成功結果

            } catch (e: Exception) {
                Log.e("ScheduleVM", "計算或儲存輪替失敗", e)
                _rotationCalculationResult.emit(Result.failure(e)) // 發送失敗結果
            } finally {
                _isCalculatingRotation.value = false
            }
        }
    }


    fun claimScheduler() {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch
            repository.claimScheduler(
                orgId = currentOrgId,
                groupId = currentGroupId,
                userId = currentUser.uid,
                userName = currentUser.displayName ?: currentUser.email ?: "未命名使用者"
            )
        }
    }

    fun addSchedulerToGroup() {
        viewModelScope.launch {
            auth.currentUser?.uid?.let { userId ->
                repository.addUserToGroupAndOrg(
                    orgId = currentOrgId,
                    groupId = currentGroupId,
                    userId = userId
                )
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
            repository.renewSchedulerLease(
                orgId = currentOrgId,
                groupId = currentGroupId,
                userId = currentUser.uid
            )
        }
    }

    // --- ✅ 4. 新增：生成預覽班表 (包含輪替計算，但不儲存) ---
    fun generatePreviewSchedule(month: String) {
        viewModelScope.launch {
            _isPreviewGenerating.value = true
            _previewGeneratedSchedule.value = null // 清除舊預覽
            _previewRotationSchedule.value = null // 清除舊預覽
            var calculatedRotations: Map<String, Map<String, String>>? = null // 暫存輪替結果

            try {
                // --- Step 1: 計算輪替 (但不儲存) ---
                if (_selectedStrategy.value == SchedulingStrategy.HOSPITAL) {
                    val groupData = _group.value
                    val ordered = _orderedUsers.value
                    val shifts = _shiftTypes.value
                    val settings = _rotationSettings.value?.rules ?: emptyMap()

                    if (groupData == null || ordered.isEmpty() || shifts.isEmpty() || settings.isEmpty()) {
                        throw Exception("醫院策略缺少輪替計算資料")
                    }

                    val rotationRulesConfig = settings.mapValues { (shiftId, setting) ->
                        RotationRuleConfig(shiftTypeId = shiftId, daysOfWeek = setting.daysOfWeek)
                    }
                    val initialRotationState = groupData.rotationState ?: emptyMap()

                    val rotationResult = rotationScheduler.calculateRotations(
                        month = month,
                        orderedUsers = ordered,
                        shiftTypes = shifts,
                        rotationRules = rotationRulesConfig,
                        initialRotationState = initialRotationState
                    )
                    calculatedRotations = rotationResult.preScheduledRotations
                    _previewRotationSchedule.value = calculatedRotations // 儲存輪替預覽
                } else {
                    calculatedRotations = emptyMap() // 非醫院策略，輪替為空
                    _previewRotationSchedule.value = calculatedRotations
                }

                // --- Step 2: 生成班表 (使用計算出的輪替，但不儲存) ---
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
                    strategy = _selectedStrategy.value.name.lowercase(), // "general" or "hospital"
                    orderedUsers = if (_selectedStrategy.value == SchedulingStrategy.HOSPITAL) _orderedUsers.value else null,
                    preScheduledRotations = calculatedRotations // 使用計算出的輪替
                )

                _previewGeneratedSchedule.value = generatorResult // 儲存完整預覽結果

            } catch (e: Exception) {
                Log.e("ScheduleVM", "預覽生成失敗 (${_selectedStrategy.value})", e)
                _previewError.emit("預覽生成失敗: ${e.message}") // 發送錯誤事件
            } finally {
                _isPreviewGenerating.value = false
            }
        }
    }

    // --- ✅ 5. 新增：儲存預覽的班表 ---
    fun savePreviewSchedule() {
        val previewResult = _previewGeneratedSchedule.value ?: return
        viewModelScope.launch {
            _isGenerating.value = true // 使用主生成狀態，避免重複按鈕
            _previewGeneratedSchedule.value = null // 清除預覽
            _previewRotationSchedule.value = null

            try {
                // 直接使用預覽結果中的 Schedule 和 Assignments 進行儲存
                repository.createScheduleAndAssignments(
                    orgId = currentOrgId,
                    schedule = previewResult.schedule,
                    assignments = previewResult.assignments
                ).getOrThrow() // 如果儲存失敗會拋出異常

                _generateSuccess.emit(Unit) // 發送成功事件
            } catch (e: Exception) {
                Log.e("ScheduleVM", "儲存預覽班表失敗", e)
                // 可以發送一個儲存失敗的事件或更新 UI State
                _previewError.emit("儲存班表失敗: ${e.message}")
            } finally {
                _isGenerating.value = false
            }
        }
    }

    // --- ✅ 6. 新增：清除預覽狀態 ---
    fun clearPreview() {
        _previewGeneratedSchedule.value = null
        _previewRotationSchedule.value = null
    }


    // --- 原有的 generateSmartSchedule (實際生成並儲存) ---
    fun generateSmartSchedule(month: String) {
        // ... (保持不變，但注意策略名稱傳遞) ...
        viewModelScope.launch {
            _isGenerating.value = true
            try {
                // 通用需要的資料
                val manpowerPlan = repository.getManpowerPlanOnce(currentOrgId, currentGroupId, month)
                val reservations = repository.observeReservations(currentOrgId, currentGroupId, month).first()
                val enabledRules = _rules.value.filter { it.isEnabled }
                var rotationScheduleData: Map<String, Map<String, String>>? = null

                // 如果是醫院策略，先讀取已儲存的 (或剛計算儲存的) 輪替班表
                if (_selectedStrategy.value == SchedulingStrategy.HOSPITAL) {
                    rotationScheduleData = try {
                        repository.observeRotationSchedule(currentOrgId, currentGroupId, month).first()
                    } catch (e: Exception) {
                        Log.w("ScheduleVM", "無法讀取 RotationSchedule: ${e.message}, 使用空資料。")
                        emptyMap<String, Map<String, String>>()
                    }
                }


                // 根據選擇的策略呼叫 ScheduleGenerator
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
                    strategy = _selectedStrategy.value.name.lowercase(), // "general" or "hospital"
                    orderedUsers = if (_selectedStrategy.value == SchedulingStrategy.HOSPITAL) _orderedUsers.value else null,
                    preScheduledRotations = rotationScheduleData // 傳入讀取的輪替資料
                )


                repository.createScheduleAndAssignments(
                    orgId = currentOrgId,
                    schedule = result.schedule,
                    assignments = result.assignments
                ).getOrThrow()

                _generateSuccess.emit(Unit)
            } catch (e: Exception) {
                Log.e("ScheduleVM", "智慧排班生成或儲存失敗 (${_selectedStrategy.value})", e)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun deleteSchedule(scheduleId: String) {
        // ... (保持不變) ...
        viewModelScope.launch {
            repository.deleteSchedule(currentOrgId, scheduleId)
                .onFailure {
                    println("❌ 刪除班表失敗: ${it.message}")
                }
        }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲