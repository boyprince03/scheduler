// scheduler/presentation/schedule/SchedulingRulesViewModel.kt

package stevedaydream.scheduler.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.SchedulingRule
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import javax.inject.Inject

@HiltViewModel
class SchedulingRulesViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(SchedulingRulesUiState())
    val uiState: StateFlow<SchedulingRulesUiState> = _uiState.asStateFlow()

    private var currentOrgId = ""
    private var currentGroupId = ""

    fun loadData(orgId: String, groupId: String) {
        if (orgId == currentOrgId && groupId == currentGroupId) return
        currentOrgId = orgId
        currentGroupId = groupId
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            auth.currentUser?.uid?.let { userId ->
                repository.observeUser(userId).collect { user ->
                    _uiState.update { it.copy(currentUser = user) }
                    if (user?.role == "superuser") {
                        repository.observeRuleTemplates().collect { templates ->
                            _uiState.update { it.copy(ruleTemplates = templates) }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.observeSchedulingRules(orgId, groupId).collect { rules ->
                _uiState.update {
                    it.copy(
                        organizationRules = rules.filter { r -> r.groupId == null },
                        groupCustomRules = rules.filter { r -> r.groupId == groupId },
                        isLoading = false
                    )
                }
            }
        }
    }

    // --- Action Handlers for Organization/Group Rules ---

    fun toggleRule(rule: SchedulingRule, isEnabled: Boolean) {
        val currentUser = _uiState.value.currentUser ?: return
        val updates = mapOf("isEnabled" to isEnabled)

        // 權限檢查
        val canToggle = when {
            isSuperuser() -> true
            rule.groupId == null && currentUser.role == "org_admin" -> true // Org admin can toggle org rules
            rule.groupId != null && rule.createdBy == currentUser.id -> true // Creator can toggle own rule
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

    // --- Action Handlers for Superuser Rule Templates ---

    fun addRuleTemplate(rule: SchedulingRule) {
        if (!isSuperuser()) return
        viewModelScope.launch {
            // Ensure it's marked as a template and has no org/group info
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

    // --- Helper functions for permissions ---
    private fun isSuperuser(): Boolean = _uiState.value.currentUser?.role == "superuser"

    fun canAddCustomRules(): Boolean =
        (_uiState.value.currentUser?.role == "org_admin" /* && isScheduler */) || isSuperuser()
}

data class SchedulingRulesUiState(
    val currentUser: User? = null,
    val organizationRules: List<SchedulingRule> = emptyList(),
    val groupCustomRules: List<SchedulingRule> = emptyList(),
    val ruleTemplates: List<SchedulingRule> = emptyList(),
    val isLoading: Boolean = true
)