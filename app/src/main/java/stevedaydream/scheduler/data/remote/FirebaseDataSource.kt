package stevedaydream.scheduler.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import stevedaydream.scheduler.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    // ==================== 組織 ====================
    suspend fun createOrganization(org: Organization): Result<String> = runCatching {
        val docRef = firestore.collection("organizations").document()
        val orgWithId = org.copy(id = docRef.id)
        docRef.set(orgWithId.toFirestoreMap()).await()
        docRef.id
    }

    fun observeOrganization(orgId: String): Flow<Organization?> {
        return firestore.collection("organizations")
            .document(orgId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(Organization::class.java)?.copy(id = snapshot.id)
                } else null
            }
    }

    // ==================== 使用者 ====================
    suspend fun createUser(orgId: String, user: User): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/users").document()
        val userWithId = user.copy(id = docRef.id, orgId = orgId)
        docRef.set(userWithId.toFirestoreMap()).await()
        docRef.id
    }

    fun observeUsers(orgId: String): Flow<List<User>> {
        return firestore.collection("organizations/$orgId/users")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(User::class.java)?.copy(id = it.id, orgId = orgId)
                }
            }
    }

    // ==================== 群組 ====================
    suspend fun createGroup(orgId: String, group: Group): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/groups").document()
        val groupWithId = group.copy(id = docRef.id, orgId = orgId)
        docRef.set(groupWithId.toFirestoreMap()).await()
        docRef.id
    }

    suspend fun updateGroup(orgId: String, groupId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/groups")
            .document(groupId)
            .update(updates)
            .await()
    }

    fun observeGroups(orgId: String): Flow<List<Group>> {
        return firestore.collection("organizations/$orgId/groups")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(Group::class.java)?.copy(id = it.id, orgId = orgId)
                }
            }
    }

    // ==================== 排班者認領 ====================
    suspend fun claimScheduler(orgId: String, groupId: String, userId: String, userName: String, leaseDuration: Long = 2 * 60 * 60 * 1000): Result<Boolean> = runCatching {
        val groupRef = firestore.collection("organizations/$orgId/groups").document(groupId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(groupRef)
            val currentSchedulerId = snapshot.getString("schedulerId")
            val expiresAt = snapshot.getTimestamp("schedulerLeaseExpiresAt")?.toDate()?.time

            // 檢查是否已有排班者且租約未過期
            if (currentSchedulerId != null && expiresAt != null && System.currentTimeMillis() < expiresAt) {
                return@runTransaction false // 認領失敗
            }

            // 認領成功,更新租約
            transaction.update(groupRef, mapOf(
                "schedulerId" to userId,
                "schedulerName" to userName,
                "schedulerLeaseExpiresAt" to com.google.firebase.Timestamp(
                    (System.currentTimeMillis() + leaseDuration) / 1000, 0
                )
            ))
            true
        }.await()
    }

    suspend fun renewSchedulerLease(orgId: String, groupId: String, userId: String, leaseDuration: Long = 2 * 60 * 60 * 1000): Result<Boolean> = runCatching {
        val groupRef = firestore.collection("organizations/$orgId/groups").document(groupId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(groupRef)
            val currentSchedulerId = snapshot.getString("schedulerId")

            if (currentSchedulerId != userId) {
                return@runTransaction false // 不是當前排班者
            }

            transaction.update(groupRef, mapOf(
                "schedulerLeaseExpiresAt" to com.google.firebase.Timestamp(
                    (System.currentTimeMillis() + leaseDuration) / 1000, 0
                )
            ))
            true
        }.await()
    }

    suspend fun releaseScheduler(orgId: String, groupId: String): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/groups")
            .document(groupId)
            .update(mapOf(
                "schedulerId" to null,
                "schedulerName" to null,
                "schedulerLeaseExpiresAt" to null
            ))
            .await()
    }

    // ==================== 班別類型 ====================
    fun observeShiftTypes(orgId: String): Flow<List<ShiftType>> {
        return firestore.collection("organizations/$orgId/shiftTypes")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(ShiftType::class.java)?.copy(id = it.id, orgId = orgId)
                }
            }
    }

    // ==================== 請求 ====================
    suspend fun createRequest(orgId: String, request: Request): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/requests").document()
        val requestWithId = request.copy(id = docRef.id, orgId = orgId)
        docRef.set(requestWithId.toFirestoreMap()).await()
        docRef.id
    }

    fun observeRequests(orgId: String): Flow<List<Request>> {
        return firestore.collection("organizations/$orgId/requests")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(Request::class.java)?.copy(id = it.id, orgId = orgId)
                }
            }
    }

    // ==================== 排班規則 ====================
    fun observeSchedulingRules(orgId: String): Flow<List<SchedulingRule>> {
        return firestore.collection("organizations/$orgId/schedulingRules")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(SchedulingRule::class.java)?.copy(id = it.id, orgId = orgId)
                }
            }
    }

    // ==================== 班表 ====================
    suspend fun createSchedule(orgId: String, schedule: Schedule): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/schedules").document()
        val scheduleWithId = schedule.copy(id = docRef.id, orgId = orgId)
        docRef.set(scheduleWithId.toFirestoreMap()).await()
        docRef.id
    }

    fun observeSchedules(orgId: String, groupId: String): Flow<List<Schedule>> {
        return firestore.collection("organizations/$orgId/schedules")
            .whereEqualTo("groupId", groupId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(Schedule::class.java)?.copy(id = it.id, orgId = orgId)
                }
            }
    }

    // ==================== 班表分配 ====================
    suspend fun createAssignment(orgId: String, scheduleId: String, assignment: Assignment): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/schedules/$scheduleId/assignments").document()
        val assignmentWithId = assignment.copy(id = docRef.id, scheduleId = scheduleId)
        docRef.set(assignmentWithId.toFirestoreMap()).await()
        docRef.id
    }

    fun observeAssignments(orgId: String, scheduleId: String): Flow<List<Assignment>> {
        return firestore.collection("organizations/$orgId/schedules/$scheduleId/assignments")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(Assignment::class.java)?.copy(id = it.id, scheduleId = scheduleId)
                }
            }
    }
}