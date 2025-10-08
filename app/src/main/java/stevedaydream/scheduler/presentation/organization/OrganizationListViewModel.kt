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
            auth.currentUser?.uid?.let { ownerId ->
                repository.observeOrganizationsByOwner(ownerId).collect { orgList ->
                    _organizations.value = orgList
                }
            }
        }
    }

    fun createOrganization(orgName: String) {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: return@launch

            // 準備要建立的組織物件
            val newOrg = Organization(
                orgName = orgName,
                ownerId = currentUser.uid,
                createdAt = System.currentTimeMillis(),
                plan = "free"
            )

            // 呼叫 repository 並處理回傳的 Result
            repository.createOrganization(newOrg)
                .onSuccess { newOrgId ->
                    // 成功時可以執行的操作，例如記錄日誌
                    // 在這裡我們不需要做特別的事，因為 Flow 會自動更新列表
                    println("Successfully created organization with ID: $newOrgId")
                }
                .onFailure { error ->
                    // 失敗時執行的操作，例如印出錯誤訊息
                    // 之後您可以在這裡加入顯示錯誤訊息給使用者的 UI 邏輯
                    println("Failed to create organization: ${error.localizedMessage}")
                }
        }
    }
}