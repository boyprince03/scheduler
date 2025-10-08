// scheduler/presentation/schedule/ShiftTypeSettingsViewModel.kt

package stevedaydream.scheduler.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.ShiftType
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import javax.inject.Inject

@HiltViewModel
class ShiftTypeSettingsViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShiftTypeSettingsUiState())
    val uiState: StateFlow<ShiftTypeSettingsUiState> = _uiState.asStateFlow()

    private var currentOrgId = ""
    private var currentGroupId = ""

    fun loadData(orgId: String, groupId: String) {
        if (orgId == currentOrgId && groupId == currentGroupId) return
        currentOrgId = orgId
        currentGroupId = groupId
        _uiState.update { it.copy(isLoading = true) }

        // 載入使用者資訊以判斷權限
        viewModelScope.launch {
            auth.currentUser?.uid?.let { userId ->
                repository.observeUser(userId).collect { user ->
                    _uiState.update { it.copy(currentUser = user) }
                }
            }
        }

        // 載入班別範本 (未來功能)
        viewModelScope.launch {
            repository.observeShiftTypeTemplates().collect { templates ->
                _uiState.update { it.copy(shiftTypeTemplates = templates) }
            }
        }

        // 載入組織與群組的自訂班別
        viewModelScope.launch {
            repository.observeShiftTypes(orgId, groupId).collect { shiftTypes ->
                _uiState.update {
                    it.copy(
                        // 分離組織層級和群組層級
                        organizationShiftTypes = shiftTypes.filter { s -> s.groupId == null },
                        groupCustomShiftTypes = shiftTypes.filter { s -> s.groupId == groupId },
                        isLoading = false
                    )
                }
            }
        }
    }

    fun addCustomShiftType(shiftType: ShiftType) {
        viewModelScope.launch {
            repository.addCustomShiftTypeForGroup(currentOrgId, currentGroupId, shiftType)
        }
    }

    fun updateShiftType(shiftType: ShiftType) {
        viewModelScope.launch {
            repository.updateShiftType(currentOrgId, shiftType.id, shiftType.toFirestoreMap())
        }
    }

    fun deleteShiftType(shiftType: ShiftType) {
        // 權限檢查：只能刪除自己建立的
        if (shiftType.createdBy != auth.currentUser?.uid) return
        viewModelScope.launch {
            repository.deleteShiftType(currentOrgId, shiftType.id)
        }
    }
}

data class ShiftTypeSettingsUiState(
    val currentUser: User? = null,
    val organizationShiftTypes: List<ShiftType> = emptyList(),
    val groupCustomShiftTypes: List<ShiftType> = emptyList(),
    val shiftTypeTemplates: List<ShiftType> = emptyList(),
    val isLoading: Boolean = true
)