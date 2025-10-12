package stevedaydream.scheduler.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth // <-- 1. 匯入 FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _generationState = MutableSharedFlow<Result<Unit>>()
    val generationState = _generationState.asSharedFlow()

    // 4. 修改函式簽名以接收 testMemberEmail
    fun createTestData(orgName: String, testMemberEmail: String) {
        viewModelScope.launch {
            val ownerId = auth.currentUser?.uid
            if (ownerId == null) {
                _generationState.emit(Result.failure(Exception("使用者未登入")))
                return@launch
            }

            if (orgName.isBlank()) {
                _generationState.emit(Result.failure(IllegalArgumentException("組織名稱不可為空")))
                return@launch
            }

            // 5. 將 testMemberEmail 傳遞到 Repository
            val result = repository.createTestData(orgName, ownerId, testMemberEmail.trim())
            _generationState.emit(result)
        }
    }
}