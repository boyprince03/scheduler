package stevedaydream.scheduler.presentation.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
enum class SortOption(val displayName: String) {
    NAME("姓名筆畫"),
    GROUP("所屬群組")
}

data class MemberWithGroupInfo(
    val user: User,
    val groupName: String
)

data class MemberListUiState(
    val isLoading: Boolean = true,
    val membersInfo: List<MemberWithGroupInfo> = emptyList(),
    val currentUser: User? = null,
    val error: String? = null,
    val updateResult: Result<Unit>? = null,
    val sortOption: SortOption = SortOption.NAME
)
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

@HiltViewModel
class MemberListViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemberListUiState())
    val uiState: StateFlow<MemberListUiState> = _uiState.asStateFlow()

    private var currentOrgId: String? = null
    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    private val _sortOption = MutableStateFlow(SortOption.NAME)
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

    fun loadData(orgId: String) {
        if (orgId == currentOrgId) return
        currentOrgId = orgId
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            auth.currentUser?.uid?.let { userId ->
                repository.observeUser(userId).collect { user ->
                    _uiState.update { it.copy(currentUser = user) }
                }
            }
        }

        // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
        viewModelScope.launch {
            val usersFlow = repository.observeUsers(orgId)
            val groupsFlow = repository.observeGroups(orgId)

            combine(usersFlow, groupsFlow, _sortOption) { users, groups, sortOption ->
                val membersInfo = users.map { user ->
                    val groupName = groups.find { it.memberIds.contains(user.id) }?.groupName ?: "未分配群組"
                    MemberWithGroupInfo(user, groupName)
                }

                val sortedMembers = when (sortOption) {
                    SortOption.NAME -> {
                        val collator = Collator.getInstance(Locale.CHINESE)
                        membersInfo.sortedWith(compareBy(collator) { it.user.name })
                    }
                    SortOption.GROUP -> {
                        val collator = Collator.getInstance(Locale.CHINESE)
                        membersInfo.sortedWith(
                            compareBy<MemberWithGroupInfo> { it.groupName }
                                .thenBy(collator) { it.user.name }
                        )
                    }
                }
                // 回傳一個 Pair，以便同時更新列表和 UI 狀態中的排序選項
                Pair(sortedMembers, sortOption)
            }.collect { (sortedList, currentSortOption) ->
                _uiState.update {
                    it.copy(
                        membersInfo = sortedList,
                        isLoading = false,
                        sortOption = currentSortOption
                    )
                }
            }
        }
    }

    fun onSortChange(sortOption: SortOption) {
        _sortOption.value = sortOption
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

    fun updateUserStatus(orgId: String, userId: String, newStatus: String) {
        viewModelScope.launch {
            val result = repository.updateEmploymentStatus(orgId, userId, newStatus)
            _uiState.update { it.copy(updateResult = result) }
        }
    }

    fun clearUpdateResult() {
        _uiState.update { it.copy(updateResult = null) }
    }
}