// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
// scheduler/presentation/members/MemberListViewModel.kt
package stevedaydream.scheduler.presentation.members

// ... (imports 保持不變) ...
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.Group
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

// +++ Define Quadruple data class +++
data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

// ... (Enums and MemberListUiState data class remain the same) ...
enum class SortOption(val displayName: String) {
    NAME("姓名筆畫"),
    GROUP("所屬群組"),
    CUSTOM("自訂排序")
}
data class MemberWithGroupInfo(
    val user: User,
    val groupName: String
)
data class MemberListUiState(
    val isLoading: Boolean = true,
    val membersInfo: List<MemberWithGroupInfo> = emptyList(), // 原始(依名稱排序)
    val orderedMembersInfo: List<MemberWithGroupInfo> = emptyList(), // 當前顯示(可能自訂排序)
    val currentUser: User? = null,
    val currentGroup: Group? = null,
    val error: String? = null,
    val updateResult: Result<Unit>? = null,
    val sortOption: SortOption = SortOption.NAME,
    val hasOrderChanged: Boolean = false,
    val saveOrderResult: Result<Unit>? = null
)


@HiltViewModel
class MemberListViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val currentOrgId: String = savedStateHandle.get<String>("orgId")!!
    private val currentGroupId: String = savedStateHandle.get<String>("groupId")!!

    private val _uiState = MutableStateFlow(MemberListUiState())
    val uiState: StateFlow<MemberListUiState> = _uiState.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.NAME)

    init {
        // Fetch current user immediately if possible (optional improvement)
        viewModelScope.launch {
            auth.currentUser?.uid?.let { userId ->
                repository.observeUser(userId).collect { user ->
                    _uiState.update { currentState -> currentState.copy(currentUser = user) }
                }
            }
        }
        loadData() // Load the main list data
    }

    internal fun loadData() {
        _uiState.update { it.copy(isLoading = true, hasOrderChanged = false, saveOrderResult = null) }

        viewModelScope.launch {
            val usersFlow = repository.observeUsers(currentOrgId)
            val groupFlow = repository.observeGroup(currentGroupId).filterNotNull()

            combine(usersFlow, groupFlow, _sortOption) { users, group, sortOption ->

                val baseMembersInGroup = users
                    .filter { user -> group.memberIds.contains(user.id) }
                    .map { user -> MemberWithGroupInfo(user, group.groupName) }

                val collator = Collator.getInstance(Locale.CHINESE)
                val nameSortedMembers = baseMembersInGroup.sortedWith(compareBy(collator) { it.user.name })

                val orderedMembersForDisplay = when (sortOption) {
                    SortOption.NAME -> nameSortedMembers
                    SortOption.GROUP -> {
                        nameSortedMembers.sortedWith( // Start from nameSorted for stability if group names are the same
                            compareBy<MemberWithGroupInfo> { it.groupName }
                                .thenBy(collator) { it.user.name }
                        )
                    }
                    SortOption.CUSTOM -> {
                        val customOrderIds = group.userOrder
                        val memberMap = nameSortedMembers.associateBy { it.user.id }

                        if (customOrderIds != null) {
                            val orderedPart = customOrderIds.mapNotNull { memberMap[it] }
                            // Filter *new* members from nameSorted list, ensuring they keep their relative name order
                            val newMembers = nameSortedMembers.filter { it.user.id !in customOrderIds.toSet() }
                            orderedPart + newMembers
                        } else {
                            nameSortedMembers // Fallback to name sort if no custom order exists
                        }
                    }
                }
                // +++ Use the defined Quadruple class +++
                Quadruple(nameSortedMembers, orderedMembersForDisplay, group, sortOption)

            }.collect { (nameSortedList, orderedListForDisplay, group, currentSortOption) -> // Destructure Quadruple

                var orderChanged = _uiState.value.hasOrderChanged // Get current state before update
                if (currentSortOption == SortOption.CUSTOM) {
                    val currentDisplayedIds = orderedListForDisplay.map { it.user.id }
                    val groupMemberIdsSet = group.memberIds.toSet() // Use Set for efficient lookup
                    val customOrderIdsSet = group.userOrder?.toSet() // Use Set for efficient lookup

                    // Check if the displayed list size matches group members OR if custom order exists and doesn't match displayed list
                    if (currentDisplayedIds.size != groupMemberIdsSet.size || (customOrderIdsSet != null && currentDisplayedIds.toSet() != customOrderIdsSet)) {
                        // Only set to true if it wasn't already true (e.g., from dragging)
                        if (!orderChanged) {
                            orderChanged = true
                        }
                    }
                    // If switched to CUSTOM, custom order exists, lists match, and no drag occurred yet, reset changed flag
                    else if (customOrderIdsSet != null && currentDisplayedIds.toSet() == customOrderIdsSet && !orderChanged) {
                        orderChanged = false // Explicitly reset if switching to CUSTOM matches the saved order
                    }
                } else {
                    orderChanged = false // Reset changed flag when not in CUSTOM mode
                }


                // +++ Explicitly name the parameter in the update lambda +++
                _uiState.update { currentState ->
                    currentState.copy(
                        membersInfo = nameSortedList,
                        orderedMembersInfo = orderedListForDisplay,
                        currentGroup = group,
                        isLoading = false,
                        sortOption = currentSortOption,
                        hasOrderChanged = orderChanged
                    )
                }
            }
        }
    }

    fun onSortChange(sortOption: SortOption) {
        _sortOption.value = sortOption
        // No need to manually update uiState here, combine will handle it
    }

    // ... (moveMemberOrder, saveUserOrder, etc., remain unchanged) ...
    fun moveMemberOrder(fromIndex: Int, toIndex: Int) {
        if (_uiState.value.sortOption != SortOption.CUSTOM) return

        val currentList = _uiState.value.orderedMembersInfo.toMutableList()
        if (fromIndex < 0 || fromIndex >= currentList.size || toIndex < 0 || toIndex >= currentList.size) return

        val movedItem = currentList.removeAt(fromIndex)
        currentList.add(toIndex, movedItem)

        // Determine if the order has actually changed compared to the *saved* order
        val newOrderIds = currentList.map { it.user.id }
        val originalSavedOrder = _uiState.value.currentGroup?.userOrder

        // Changed if: there was no saved order OR the new order differs from the saved one.
        val changed = originalSavedOrder == null || newOrderIds != originalSavedOrder

        _uiState.update {
            it.copy(
                orderedMembersInfo = currentList,
                hasOrderChanged = changed // Update based on comparison with saved state
            )
        }
    }
    fun saveUserOrder() {
        val group = _uiState.value.currentGroup ?: return
        val newOrder = _uiState.value.orderedMembersInfo.map { it.user.id }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) } // Show loading indicator
            val result = repository.updateGroup(currentOrgId, group.id, mapOf("userOrder" to newOrder))
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasOrderChanged = false, // Reset changed flag on successful save
                        saveOrderResult = Result.success(Unit)
                        // Note: The list in uiState is already the new order due to moveMemberOrder
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        saveOrderResult = Result.failure(error)
                        // Keep hasOrderChanged as true since save failed
                    )
                }
            }
        }
    }
    fun clearSaveOrderResult() {
        _uiState.update { it.copy(saveOrderResult = null) }
    }
    fun updateUserStatus(userId: String, newStatus: String) {
        viewModelScope.launch {
            val result = repository.updateEmploymentStatus(currentOrgId, userId, newStatus)
            _uiState.update { it.copy(updateResult = result) }
        }
    }
    fun clearUpdateResult() {
        _uiState.update { it.copy(updateResult = null) }
    }
}