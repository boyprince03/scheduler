// scheduler/presentation/invite/ReviewRequestsViewModel.kt

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
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

    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    fun loadDataForUser(initialOrgId: String) {
        val currentUid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            repository.observeUser(currentUid).filterNotNull().flatMapLatest { user ->
                if (user.role == "superuser") {
                    // 超級管理員：監聽所有他擁有的組織的申請
                    repository.observeOrganizationsByOwner(user.id).flatMapLatest { ownedOrgs ->
                        if (ownedOrgs.isEmpty()) {
                            flowOf(Pair(emptyList(), emptyList())) // 回傳空的申請和群組
                        } else {
                            // 建立所有組織的申請 Flow
                            val requestFlows = ownedOrgs.map { org ->
                                repository.observeOrganizationJoinRequests(org.id)
                            }
                            // 建立所有組織的群組 Flow
                            val groupFlows = ownedOrgs.map { org ->
                                repository.observeGroups(org.id)
                            }

                            // 合併所有 Flow
                            combine(combine(requestFlows) { it.flatMap { list -> list } },
                                combine(groupFlows) { it.flatMap { list -> list } }) { requests, groups ->
                                Pair(requests, groups)
                            }
                        }
                    }
                } else {
                    // 普通管理員：只監聽單一組織的申請和群組
                    combine(
                        repository.observeOrganizationJoinRequests(initialOrgId),
                        repository.observeGroups(initialOrgId)
                    ) { requests, groups ->
                        Pair(requests, groups)
                    }
                }
            }.collect { (requestList, groupList) ->
                _requests.value = requestList
                _groups.value = groupList
            }
        }
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

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