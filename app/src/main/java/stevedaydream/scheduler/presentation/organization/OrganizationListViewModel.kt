package stevedaydream.scheduler.presentation.organization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.Organization
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import javax.inject.Inject

@HiltViewModel
class OrganizationListViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _organizations = MutableStateFlow<List<Organization>>(emptyList())
    val organizations: StateFlow<List<Organization>> = _organizations.asStateFlow()

    init {
        loadOrganizations()
    }

    private fun loadOrganizations() {
        viewModelScope.launch {
            // 注意:這裡需要根據當前使用者查詢所屬組織
            // 實際實作需要在 Firestore 中建立索引或使用不同的查詢策略

            // 暫時的示範:假設使用者 ID 就是組織 ID
            auth.currentUser?.uid?.let { userId ->
                repository.observeOrganization(userId).collect { org ->
                    _organizations.value = listOfNotNull(org)
                }
            }
        }
    }

    fun createOrganization(orgName: String) {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch

            val newOrg = Organization(
                orgName = orgName,
                ownerId = currentUser.uid,
                createdAt = System.currentTimeMillis(),
                plan = "free"
            )

            repository.createOrganization(newOrg)
        }
    }
}