package stevedaydream.scheduler.presentation.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.OrganizationInvite
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import java.util.Calendar
import javax.inject.Inject
@HiltViewModel
class InviteManagementViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _invites = MutableStateFlow<List<OrganizationInvite>>(emptyList())
    val invites: StateFlow<List<OrganizationInvite>> = _invites.asStateFlow()

    fun loadInvites(orgId: String) {
        viewModelScope.launch {
            repository.observeOrganizationInvites(orgId).collect { inviteList ->
                _invites.value = inviteList.sortedByDescending { it.createdAt }
            }
        }
    }

    fun createInvite(
        orgId: String,
        inviteType: String,
        expiryDays: Int?,
        usageLimit: Int?,
        targetGroupId: String?
    ) {
        viewModelScope.launch {
            val inviteCode = generateInviteCode()
            val expiresAt = expiryDays?.let {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, it)
                }.time
            }

            val invite = OrganizationInvite(
                orgId = orgId,
                orgName = "", // 會在 Repository 補上
                inviteCode = inviteCode,
                inviteType = inviteType,
                createdBy = auth.currentUser?.uid ?: "",
                expiresAt = expiresAt,
                usageLimit = usageLimit,
                targetGroupId = targetGroupId
            )

            repository.createOrganizationInvite(orgId, invite)
        }
    }

    fun deactivateInvite(orgId: String, inviteId: String) {
        viewModelScope.launch {
            repository.deactivateInvite(orgId, inviteId)
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars.random() }.joinToString("")
    }
}