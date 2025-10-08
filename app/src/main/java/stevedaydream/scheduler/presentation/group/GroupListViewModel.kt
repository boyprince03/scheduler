package stevedaydream.scheduler.presentation.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.Group
import stevedaydream.scheduler.domain.repository.SchedulerRepository
import javax.inject.Inject

@HiltViewModel
class GroupListViewModel @Inject constructor(
    private val repository: SchedulerRepository
) : ViewModel() {

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    fun loadGroups(orgId: String) {
        viewModelScope.launch {
            repository.observeGroups(orgId).collect { groupList ->
                _groups.value = groupList
            }
        }
    }

    fun createGroup(orgId: String, groupName: String) {
        viewModelScope.launch {
            val newGroup = Group(
                orgId = orgId,
                groupName = groupName,
                memberIds = emptyList()
            )
            repository.createGroup(orgId, newGroup)
        }
    }
}