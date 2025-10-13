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
import stevedaydream.scheduler.data.model.GroupJoinRequest
import stevedaydream.scheduler.data.model.OrganizationJoinRequest
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import javax.inject.Inject


sealed class ReviewableRequest {
    data class OrgJoin(val request: OrganizationJoinRequest) : ReviewableRequest()
    data class GroupJoin(val request: GroupJoinRequest) : ReviewableRequest()
}


// ==================== ReviewRequestsViewModel ====================

@HiltViewModel
class ReviewRequestsViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _requests = MutableStateFlow<List<ReviewableRequest>>(emptyList())
    val requests: StateFlow<List<ReviewableRequest>> = _requests.asStateFlow()

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    private val _reviewResult = MutableSharedFlow<Result<Unit>>()
    val reviewResult = _reviewResult.asSharedFlow()

    fun loadDataForUser(initialOrgId: String) {
        val currentUid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            repository.observeUser(currentUid).filterNotNull().flatMapLatest { user ->
                if (user.role == "superuser") {
                    // 超級管理員：監聽所有他擁有的組織的申請
                    repository.observeOrganizationsByOwner(user.id).flatMapLatest { ownedOrgs ->
                        if (ownedOrgs.isEmpty()) {
                            // 回傳空的申請和群組
                            flowOf(Triple(emptyList<OrganizationJoinRequest>(), emptyList<GroupJoinRequest>(), emptyList<Group>()))
                        } else {
                            // 建立所有組織的申請 Flow
                            val orgRequestFlows = ownedOrgs.map { org ->
                                repository.observeOrganizationJoinRequests(org.id)
                            }
                            val groupRequestFlows = ownedOrgs.map { org ->
                                repository.observeGroupJoinRequestsForOrg(org.id)
                            }
                            val groupFlows = ownedOrgs.map { org ->
                                repository.observeGroups(org.id)
                            }

                            // 合併所有 Flow
                            combine(
                                combine(orgRequestFlows) { it.flatMap { list -> list } },
                                combine(groupRequestFlows) { it.flatMap { list -> list } },
                                combine(groupFlows) { it.flatMap { list -> list } }
                            ) { orgRequests, groupRequests, groups ->
                                Triple(orgRequests, groupRequests, groups)
                            }
                        }
                    }
                } else {
                    // 普通管理員：只監聽單一組織的申請和群組
                    combine(
                        repository.observeOrganizationJoinRequests(initialOrgId),
                        repository.observeGroupJoinRequestsForOrg(initialOrgId),
                        repository.observeGroups(initialOrgId)
                    ) { orgRequests, groupRequests, groups ->
                        Triple(orgRequests, groupRequests, groups)
                    }
                }
            }.collect { (orgRequestList, groupRequestList, groupList) ->
                // 合併兩種申請並排序
                val combinedList = (orgRequestList.map { ReviewableRequest.OrgJoin(it) } +
                        groupRequestList.map { ReviewableRequest.GroupJoin(it) })

                _requests.value = combinedList
                _groups.value = groupList
            }
        }
    }


    fun processOrgRequest(
        request: OrganizationJoinRequest,
        approve: Boolean,
        targetGroupId: String?
    ) {
        viewModelScope.launch {
            val processedBy = auth.currentUser?.uid ?: return@launch

            val result = repository.processJoinRequest(
                orgId = request.orgId,
                requestId = request.id,
                approve = approve,
                processedBy = processedBy,
                targetGroupId = targetGroupId
            )
            _reviewResult.emit(result)
        }
    }


    fun processGroupRequest(
        request: GroupJoinRequest,
        approve: Boolean
    ) {
        viewModelScope.launch {
            val processedBy = auth.currentUser?.uid ?: return@launch
            val status = if (approve) "approved" else "rejected"

            // 如果核准，先將使用者加入群組
            if (approve) {
                val updateGroupResult = repository.updateUserGroup(
                    orgId = request.orgId,
                    userId = request.userId,
                    newGroupId = request.targetGroupId,
                    oldGroupId = null // 假設使用者一次只在一個群組
                )
                if (updateGroupResult.isFailure) {
                    _reviewResult.emit(Result.failure(updateGroupResult.exceptionOrNull()!!))
                    return@launch
                }
            }

            // 更新申請狀態
            val statusUpdate = mapOf(
                "status" to status,
                "processedBy" to processedBy,
                "processedAt" to com.google.firebase.Timestamp.now()
            )
            val result = repository.updateGroupJoinRequestStatus(request.orgId, request.id, statusUpdate)
            _reviewResult.emit(result)
        }
    }

}