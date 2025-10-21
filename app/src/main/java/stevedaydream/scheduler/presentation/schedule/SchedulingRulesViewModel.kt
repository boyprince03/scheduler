// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.presentation.schedule

import androidx.lifecycle.ViewModel // 確保正確 import
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel // 確保正確 import
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import javax.inject.Inject

// 更新 UI State，加入輪替規則相關狀態
data class SchedulingRulesUiState(
    val currentUser: User? = null,
    val organizationRules: List<SchedulingRule> = emptyList(),
    val groupCustomRules: List<SchedulingRule> = emptyList(),
    val ruleTemplates: List<SchedulingRule> = emptyList(),
    val isLoading: Boolean = true,
    val availableShiftTypes: List<ShiftType> = emptyList(),
    val rotationSettings: RotationSettingsContainer? = null,
    val saveRotationResult: Result<Unit>? = null
)

@HiltViewModel // 確保有 HiltViewModel 註解
class SchedulingRulesViewModel @Inject constructor( // 確保有 Inject constructor
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() { // 確保繼承 ViewModel

    private val _uiState = MutableStateFlow(SchedulingRulesUiState())
    val uiState: StateFlow<SchedulingRulesUiState> = _uiState.asStateFlow() // 確保是 public val

    private var currentOrgId = ""
    private var currentGroupId = ""

    fun loadData(orgId: String, groupId: String) { // 確保方法是 public (預設)
        if (orgId == currentOrgId && groupId == currentGroupId && !_uiState.value.isLoading) return
        currentOrgId = orgId
        currentGroupId = groupId
        _uiState.update { it.copy(isLoading = true, saveRotationResult = null) }

        viewModelScope.launch {
            auth.currentUser?.uid?.let { userId ->
                repository.observeUser(userId).collect { user ->
                    _uiState.update { it.copy(currentUser = user) }
                    // ✅ 修正：Superuser 判斷應基於 user?.role
                    if (user?.role == "superuser") {
                        repository.observeRuleTemplates().collect { templates ->
                            _uiState.update { it.copy(ruleTemplates = templates) }
                        }
                    } else {
                        // 如果不是 Superuser，清空範本
                        _uiState.update { it.copy(ruleTemplates = emptyList()) }
                    }
                }
            }
        }


        viewModelScope.launch {
            repository.observeSchedulingRules(orgId, groupId).collect { rules ->
                _uiState.update {
                    it.copy(
                        organizationRules = rules.filter { r -> r.groupId == null },
                        groupCustomRules = rules.filter { r -> r.groupId == groupId }
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.observeShiftTypes(orgId, groupId).collect { shiftTypes ->
                _uiState.update { it.copy(availableShiftTypes = shiftTypes.filter { s -> s.shortCode != "OFF" }) }
            }
        }

        viewModelScope.launch {
            repository.observeRotationSettings(orgId, groupId).collect { settings ->
                _uiState.update { it.copy(rotationSettings = settings ?: RotationSettingsContainer()) }
            }
        }

        viewModelScope.launch {
            // ✅ 簡化 combine 條件，isLoading 主要由第一個 launch 控制
            combine(
                _uiState.map { it.currentUser }, // 等待 currentUser 至少有一次值 (可以是 null)
                _uiState.map { it.availableShiftTypes },
                _uiState.map { it.rotationSettings } // 等待 rotationSettings 至少有一次值 (可以是 null)
            ) { _, _, _ ->
                _uiState.update { it.copy(isLoading = false) }
            }.take(1).collect() // 確保只執行一次 isLoading = false 的更新
        }

    }

    // --- Action Handlers for Organization/Group Rules (保持不變) ---
    fun toggleRule(rule: SchedulingRule, isEnabled: Boolean) {
        val currentUser = _uiState.value.currentUser ?: return
        val updates = mapOf("isEnabled" to isEnabled)

        val canToggle = when {
            isSuperuser() -> true
            rule.groupId == null && currentUser.role == "org_admin" -> true
            rule.groupId != null && rule.createdBy == currentUser.id -> true
            else -> false
        }

        if (canToggle) {
            viewModelScope.launch {
                repository.updateRuleForOrg(currentOrgId, rule.id, updates)
            }
        }
    }

    fun addCustomRule(rule: SchedulingRule) {
        if (!canAddCustomRules()) return
        viewModelScope.launch {
            repository.addCustomRuleForGroup(currentOrgId, currentGroupId, rule)
        }
    }

    fun updateCustomRule(rule: SchedulingRule) {
        if (rule.createdBy != auth.currentUser?.uid && !isSuperuser()) return
        viewModelScope.launch {
            repository.updateRuleForOrg(currentOrgId, rule.id, rule.toFirestoreMap())
        }
    }

    fun deleteRule(rule: SchedulingRule) {
        if (rule.createdBy != auth.currentUser?.uid && !isSuperuser()) return
        if (rule.groupId == null) return // Cannot delete org rules
        viewModelScope.launch {
            repository.deleteRuleForOrg(currentOrgId, rule.id)
        }
    }

    // --- Action Handlers for Superuser Rule Templates (保持不變) ---
    fun addRuleTemplate(rule: SchedulingRule) {
        if (!isSuperuser()) return
        viewModelScope.launch {
            val template = rule.copy(isTemplate = true, orgId = "", groupId = null, createdBy = null)
            repository.addRuleTemplate(template)
        }
    }

    fun updateRuleTemplate(rule: SchedulingRule) {
        if (!isSuperuser()) return
        viewModelScope.launch {
            repository.updateRuleTemplate(rule.id, rule.toFirestoreMap())
        }
    }

    fun deleteRuleTemplate(ruleId: String) {
        if (!isSuperuser()) return
        viewModelScope.launch {
            repository.deleteRuleTemplate(ruleId)
        }
    }

    // --- Action Handlers for Rotation Settings ---

    fun updateRotationSetting(shiftTypeId: String, setting: RotationSetting) { // 確保方法是 public
        val currentSettings = _uiState.value.rotationSettings ?: RotationSettingsContainer()
        val updatedRules = currentSettings.rules.toMutableMap()
        updatedRules[shiftTypeId] = setting
        _uiState.update { it.copy(rotationSettings = currentSettings.copy(rules = updatedRules)) }
    }

    fun removeRotationSetting(shiftTypeId: String) { // 確保方法是 public
        val currentSettings = _uiState.value.rotationSettings ?: RotationSettingsContainer()
        val updatedRules = currentSettings.rules.toMutableMap()
        updatedRules.remove(shiftTypeId)
        _uiState.update { it.copy(rotationSettings = currentSettings.copy(rules = updatedRules)) }
    }

    fun saveRotationSettings() { // 確保方法是 public
        if (!canModifyRotationSettings()) return
        val settingsToSave = _uiState.value.rotationSettings ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.saveRotationSettings(currentOrgId, currentGroupId, settingsToSave)
            _uiState.update { it.copy(isLoading = false, saveRotationResult = result) }
        }
    }

    fun clearSaveRotationResult() { // 確保方法是 public
        _uiState.update { it.copy(saveRotationResult = null) }
    }


    // --- Helper functions for permissions ---
    private fun isSuperuser(): Boolean = _uiState.value.currentUser?.role == "superuser"

    fun canAddCustomRules(): Boolean = // 確保方法是 public
        (_uiState.value.currentUser?.role == "org_admin") || isSuperuser()

    fun canModifyRotationSettings(): Boolean = // 確保方法是 public
        (_uiState.value.currentUser?.role == "org_admin") || isSuperuser()
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲