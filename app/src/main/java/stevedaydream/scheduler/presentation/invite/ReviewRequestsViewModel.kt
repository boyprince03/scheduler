package stevedaydream.scheduler.presentation.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.Group
import stevedaydream.scheduler.data.model.OrganizationJoinRequest
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import javax.inject.Inject

// ==================== ReviewRequestsViewModel ====================

@HiltViewModel
class ReviewRequestsViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _requests = MutableStateFlow<List<OrganizationJoinRequest>>(emptyList())
    val requests: StateFlow<List<OrganizationJoinRequest>> = _requests.asStateFlow()

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    private val _reviewResult = MutableSharedFlow<Result<Unit>>()
    val reviewResult = _reviewResult.asSharedFlow()

    fun loadRequests(orgId: String) {
        viewModelScope.launch {
            repository.observeOrganizationJoinRequests(orgId).collect { requestList ->
                _requests.value = requestList
            }
        }
    }

    fun loadGroups(orgId: String) {
        viewModelScope.launch {
            repository.observeGroups(orgId).collect { groupList ->
                _groups.value = groupList
            }
        }
    }

    fun processRequest(
        orgId: String,
        requestId: String,
        approve: Boolean,
        targetGroupId: String?
    ) {
        viewModelScope.launch {
            val processedBy = auth.currentUser?.uid ?: return@launch

            val result = repository.processJoinRequest(
                orgId = orgId,
                requestId = requestId,
                approve = approve,
                processedBy = processedBy,
                targetGroupId = targetGroupId
            )

            _reviewResult.emit(result)
        }
    }
}