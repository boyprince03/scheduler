// scheduler/data/remote/FirebaseDataSource.kt

package stevedaydream.scheduler.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import stevedaydream.scheduler.data.model.*
import javax.inject.Inject
import javax.inject.Singleton
import stevedaydream.scheduler.util.TestDataGenerator

@Singleton
class FirebaseDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    /**
     * 產生一組預設的排班規則
     */
    private fun getDefaultSchedulingRules(): List<SchedulingRule> {
        return listOf(
            SchedulingRule(
                ruleName = "連續上班不超過N天",
                description = "避免員工因連續工作過多天而過勞。",
                ruleType = "soft",
                penaltyScore = -50,
                isEnabled = true,
                parameters = mapOf("maxDays" to "6")
            ),
            SchedulingRule(
                ruleName = "輪班間隔需大於N小時",
                description = "確保員工在兩次輪班之間有足夠的休息時間。",
                ruleType = "hard",
                penaltyScore = -1000,
                isEnabled = true,
                parameters = mapOf("minHours" to "11")
            )
        )
    }

    suspend fun leaveOrganization(orgId: String, userId: String): Result<Unit> = runCatching {
        val userRef = firestore.collection("users").document(userId)
        val subCollectionUserRef = firestore.collection("organizations/$orgId/users").document(userId)

        // 1. 在批次寫入前，先讀取所有需要的資料
        val userSnapshot = userRef.get().await()
        val user = userSnapshot.toObject(User::class.java)
        val groupsInOrg = firestore.collection("organizations/$orgId/groups")
            .whereArrayContains("memberIds", userId)
            .get()
            .await()

        // 2. 執行批次寫入
        firestore.runBatch { batch ->
            // 從頂層 User 物件中移除 orgId
            batch.update(userRef, "orgIds", FieldValue.arrayRemove(orgId))

            // 如果 currentOrgId 是要離開的組織，則清空
            if (user?.currentOrgId == orgId) {
                batch.update(userRef, "currentOrgId", "")
            }

            // 從組織底下的 users 子集合中刪除使用者
            batch.delete(subCollectionUserRef)

            // 從該組織的所有群組中移除使用者
            groupsInOrg.documents.forEach { doc ->
                batch.update(doc.reference, "memberIds", FieldValue.arrayRemove(userId))
            }
        }.await()
    }

    suspend fun updateEmploymentStatus(orgId: String, userId: String, status: String): Result<Unit> = runCatching {
        val userRef = firestore.collection("users").document(userId)
        val subCollectionUserRef = firestore.collection("organizations/$orgId/users").document(userId)
        val statusUpdate = mapOf("employmentStatus.$orgId" to status)

        firestore.runBatch { batch ->
            batch.update(userRef, statusUpdate)
            batch.update(subCollectionUserRef, statusUpdate)
        }.await()
    }


    // ==================== 組織 ====================
    suspend fun createOrganizationAndFirstUser(org: Organization, user: User): Result<String> = runCatching {
        val orgRef = firestore.collection("organizations").document()
        val topLevelUserRef = firestore.collection("users").document(user.id)

        val orgWithId = org.copy(id = orgRef.id)
        val userWithOrgId = user.copy(
            orgIds = user.orgIds + orgRef.id,
            currentOrgId = orgRef.id
        )

        firestore.runBatch { batch ->
            batch.set(orgRef, orgWithId.toFirestoreMap())
            batch.set(topLevelUserRef, userWithOrgId.toFirestoreMap(), com.google.firebase.firestore.SetOptions.merge())

            val rulesCollection = firestore.collection("organizations/${orgRef.id}/schedulingRules")
            getDefaultSchedulingRules().forEach { rule ->
                val ruleRef = rulesCollection.document()
                batch.set(ruleRef, rule.toFirestoreMap())
            }
        }.await()
        orgRef.id
    }

    fun observeOrganization(orgId: String): Flow<Organization?> {
        if (orgId.isBlank()) {
            return flowOf(null)
        }
        return firestore.collection("organizations")
            .document(orgId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(Organization::class.java)?.copy(id = snapshot.id)
                } else null
            }
    }

    fun observeOrganizationsByOwner(ownerId: String): Flow<List<Organization>> {
        return firestore.collection("organizations")
            .whereEqualTo("ownerId", ownerId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(Organization::class.java)?.copy(id = it.id)
                }
            }
    }
    suspend fun getOrganizationsByOwner(ownerId: String): List<Organization> {
        val snapshot = firestore.collection("organizations")
            .whereEqualTo("ownerId", ownerId)
            .get()
            .await()
        return snapshot.documents.mapNotNull {
            it.toObject(Organization::class.java)?.copy(id = it.id)
        }
    }

    suspend fun generateUniqueOrgCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        var code: String
        var isUnique = false

        do {
            code = (1..8).map { chars.random() }.joinToString("")
            val existingOrg = firestore.collection("organizations")
                .whereEqualTo("orgCode", code)
                .get()
                .await()
            isUnique = existingOrg.isEmpty
        } while (!isUnique)

        return code
    }

    // ==================== 使用者 ====================
    suspend fun createUser(orgId: String, user: User): Result<String> = runCatching {
        val subCollectionUserRef = firestore.collection("organizations/$orgId/users").document(user.id)
        val topLevelUserRef = firestore.collection("users").document(user.id)

        val updatedUser = user.copy(
            orgIds = (user.orgIds + orgId).distinct(),
            currentOrgId = orgId
        )

        firestore.runBatch { batch ->
            batch.set(subCollectionUserRef, updatedUser.toFirestoreMap())
            batch.set(topLevelUserRef, updatedUser.toFirestoreMap(), com.google.firebase.firestore.SetOptions.merge())
        }.await()
        subCollectionUserRef.id
    }

    suspend fun createOrganizationInvite(
        orgId: String,
        invite: OrganizationInvite
    ): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/invites").document()
        val inviteWithId = invite.copy(id = docRef.id)
        docRef.set(inviteWithId.toFirestoreMap()).await()
        docRef.id
    }

    fun observeUsers(orgId: String): Flow<List<User>> {
        return firestore.collection("organizations/$orgId/users")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(User::class.java)?.copy(id = it.id)
                }
            }
    }

    fun observeOrganizationInvites(orgId: String): Flow<List<OrganizationInvite>> {
        return firestore.collection("organizations/$orgId/invites")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(OrganizationInvite::class.java)?.copy(id = it.id)
                }
            }
    }

    suspend fun getOrganizationByInviteCode(inviteCode: String): Result<Organization?> = runCatching {
        val inviteSnapshot = firestore.collectionGroup("invites")
            .whereEqualTo("inviteCode", inviteCode)
            .whereEqualTo("isActive", true)
            .get()
            .await()

        if (inviteSnapshot.isEmpty) {
            return@runCatching null
        }

        val invite = inviteSnapshot.documents.first()
            .toObject(OrganizationInvite::class.java) ?: return@runCatching null

        if (!invite.isValid()) {
            return@runCatching null
        }

        val orgSnapshot = firestore.collection("organizations")
            .document(invite.orgId)
            .get()
            .await()

        orgSnapshot.toObject(Organization::class.java)?.copy(id = orgSnapshot.id)
    }

    suspend fun validateAndUseInviteCode(inviteCode: String): Result<OrganizationInvite> = runCatching {
        val inviteQuery = firestore.collectionGroup("invites")
            .whereEqualTo("inviteCode", inviteCode)
            .whereEqualTo("isActive", true)
            .limit(1)
            .get()
            .await()

        if (inviteQuery.isEmpty) {
            throw IllegalArgumentException("邀請碼不存在或已失效")
        }
        val inviteDocRef = inviteQuery.documents.first().reference

        firestore.runTransaction { transaction ->
            val inviteDoc = transaction.get(inviteDocRef)
            val invite = inviteDoc.toObject(OrganizationInvite::class.java)
                ?: throw IllegalArgumentException("無法解析邀請碼")

            if (!invite.isValid()) {
                throw IllegalArgumentException("邀請碼已過期或已達使用上限")
            }

            transaction.update(inviteDoc.reference, "usedCount", invite.usedCount + 1)
            invite
        }.await()
    }
    // ==================== 組織加入申請 ====================

    suspend fun createOrganizationJoinRequest(
        request: OrganizationJoinRequest
    ): Result<String> = runCatching {
        val docRef = firestore.collection("organizationJoinRequests").document()
        val requestWithId = request.copy(id = docRef.id)
        docRef.set(requestWithId.toFirestoreMap()).await()
        docRef.id
    }

    fun observeOrganizationJoinRequests(orgId: String): Flow<List<OrganizationJoinRequest>> {
        return firestore.collection("organizationJoinRequests")
            .whereEqualTo("orgId", orgId)
            .orderBy("requestedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(OrganizationJoinRequest::class.java)?.copy(id = it.id)
                }
            }
    }

    fun observeUserJoinRequests(userId: String): Flow<List<OrganizationJoinRequest>> {
        return firestore.collection("organizationJoinRequests")
            .whereEqualTo("userId", userId)
            .orderBy("requestedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(OrganizationJoinRequest::class.java)?.copy(id = it.id)
                }
            }
    }

    suspend fun deactivateInvite(orgId: String, inviteId: String): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/invites")
            .document(inviteId)
            .update("isActive", false)
            .await()
    }

    suspend fun processJoinRequest(
        orgId: String,
        requestId: String,
        approve: Boolean,
        processedBy: String,
        targetGroupId: String?
    ): Result<Unit> = runCatching {
        val requestRef = firestore.collection("organizationJoinRequests").document(requestId)

        firestore.runTransaction { transaction ->
            val requestSnapshot = transaction.get(requestRef)
            val request = requestSnapshot.toObject(OrganizationJoinRequest::class.java)
                ?: throw IllegalArgumentException("找不到申請記錄: $requestId")

            val userRef = firestore.collection("users").document(request.userId)
            val userSnapshot = transaction.get(userRef)
            val user = userSnapshot.toObject(User::class.java)

            val groupRef = if (targetGroupId != null) {
                firestore.collection("organizations/$orgId/groups").document(targetGroupId)
            } else null
            val groupSnapshot = groupRef?.let { transaction.get(it) }

            if (approve && groupRef != null && (groupSnapshot == null || !groupSnapshot.exists())) {
                throw IllegalArgumentException("指定的群組不存在: $targetGroupId")
            }

            val statusUpdate = mapOf(
                "status" to if (approve) "approved" else "rejected",
                "processedBy" to processedBy,
                "processedAt" to com.google.firebase.Timestamp.now()
            )
            transaction.update(requestRef, statusUpdate)

            if (approve) {
                transaction.update(userRef, "orgIds", com.google.firebase.firestore.FieldValue.arrayUnion(orgId))
                if (user?.currentOrgId.isNullOrBlank()) {
                    transaction.update(userRef, "currentOrgId", orgId)
                }

                groupRef?.let {
                    transaction.update(it, "memberIds", com.google.firebase.firestore.FieldValue.arrayUnion(request.userId))
                }
            }
        }.await()
    }

    suspend fun getOrganizationByCode(orgCode: String): Result<Organization?> = runCatching {
        val snapshot = firestore.collection("organizations")
            .whereEqualTo("orgCode", orgCode)
            .get()
            .await()

        if (snapshot.isEmpty) {
            return@runCatching null
        }

        val doc = snapshot.documents.first()
        doc.toObject(Organization::class.java)?.copy(id = doc.id)
    }

    suspend fun checkUserExists(userId: String): Boolean {
        val userDoc = firestore.collection("users").document(userId).get().await()
        return userDoc.exists()
    }

    suspend fun updateUser(userId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        val topLevelUserRef = firestore.collection("users").document(userId)

        val userSnapshot = topLevelUserRef.get().await()
        val user = userSnapshot.toObject(User::class.java)
        val orgIds = user?.orgIds ?: emptyList()

        firestore.runBatch { batch ->
            batch.set(topLevelUserRef, updates, com.google.firebase.firestore.SetOptions.merge())

            orgIds.forEach { orgId ->
                if (orgId.isNotEmpty()) {
                    val subCollectionUserRef = firestore.collection("organizations/$orgId/users").document(userId)
                    batch.set(subCollectionUserRef, updates, com.google.firebase.firestore.SetOptions.merge())
                }
            }
        }.await()
    }

    fun observeUserFromTopLevel(userId: String): Flow<User?> {
        return firestore.collection("users")
            .document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(User::class.java)?.copy(id = snapshot.id)
                } else {
                    null
                }
            }
    }

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

    suspend fun createGroupJoinRequest(orgId: String, request: GroupJoinRequest): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/groupJoinRequests").document()
        val requestWithId = request.copy(id = docRef.id)
        docRef.set(requestWithId.toFirestoreMap()).await()
        docRef.id
    }
    suspend fun cancelGroupJoinRequest(orgId: String, requestId: String): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/groupJoinRequests")
            .document(requestId)
            .update("status", "canceled")
            .await()
    }

    fun observeGroupJoinRequestsForUser(userId: String): Flow<List<GroupJoinRequest>> {
        return firestore.collectionGroup("groupJoinRequests")
            .whereEqualTo("userId", userId)
            .orderBy("requestedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(GroupJoinRequest::class.java)?.copy(id = it.id)
                }
            }
    }

    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    fun observeGroupJoinRequestsForOrg(orgId: String): Flow<List<GroupJoinRequest>> {
        return firestore.collection("organizations/$orgId/groupJoinRequests")
            .orderBy("requestedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(GroupJoinRequest::class.java)?.copy(id = it.id)
                }
            }
    }

    suspend fun updateGroupJoinRequestStatus(orgId: String, requestId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/groupJoinRequests")
            .document(requestId)
            .update(updates)
            .await()
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

    suspend fun updateUserGroup(
        orgId: String,
        userId: String,
        newGroupId: String,
        oldGroupId: String?
    ): Result<Unit> = runCatching {
        val groupsCollection = firestore.collection("organizations/$orgId/groups")

        firestore.runBatch { batch ->
            if (oldGroupId != null && oldGroupId != newGroupId) {
                val oldGroupRef = groupsCollection.document(oldGroupId)
                batch.update(oldGroupRef, "memberIds", com.google.firebase.firestore.FieldValue.arrayRemove(userId))
            }

            val newGroupRef = groupsCollection.document(newGroupId)
            batch.update(newGroupRef, "memberIds", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
        }.await()
    }
    suspend fun claimScheduler(orgId: String, groupId: String, userId: String, userName: String, leaseDuration: Long = 2 * 60 * 60 * 1000): Result<Boolean> = runCatching {
        val groupRef = firestore.collection("organizations/$orgId/groups").document(groupId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(groupRef)
            val currentSchedulerId = snapshot.getString("schedulerId")
            val expiresAt = snapshot.getTimestamp("schedulerLeaseExpiresAt")?.toDate()?.time

            if (currentSchedulerId != null && expiresAt != null && System.currentTimeMillis() < expiresAt) {
                return@runTransaction false
            }

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
                return@runTransaction false
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

    fun observeShiftTypeTemplates(): Flow<List<ShiftType>> {
        return firestore.collection("shiftTypeTemplates")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(ShiftType::class.java)?.copy(id = it.id)
                }
            }
    }

    fun observeShiftTypes(orgId: String, groupId: String): Flow<List<ShiftType>> {
        return firestore.collection("organizations/$orgId/shiftTypes")
            .whereIn("groupId", listOf(null, groupId))
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(ShiftType::class.java)?.copy(id = it.id, orgId = orgId)
                }
            }
    }

    suspend fun addCustomShiftTypeForGroup(orgId: String, groupId: String, shiftType: ShiftType): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/shiftTypes").document()
        val newShiftType = shiftType.copy(
            id = docRef.id,
            orgId = orgId,
            groupId = groupId,
            isTemplate = false,
            createdBy = auth.currentUser?.uid
        )
        docRef.set(newShiftType.toFirestoreMap()).await()
        docRef.id
    }

    suspend fun updateShiftType(orgId: String, shiftTypeId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/shiftTypes")
            .document(shiftTypeId)
            .update(updates)
            .await()
    }

    suspend fun deleteShiftType(orgId: String, shiftTypeId: String): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/shiftTypes")
            .document(shiftTypeId)
            .delete()
            .await()
    }

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

    fun observeRuleTemplates(): Flow<List<SchedulingRule>> {
        return firestore.collection("ruleTemplates")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(SchedulingRule::class.java)?.copy(id = it.id)
                }
            }
    }

    suspend fun addRuleTemplate(rule: SchedulingRule): Result<String> = runCatching {
        val docRef = firestore.collection("ruleTemplates").document()
        val template = rule.copy(id = docRef.id, isTemplate = true, orgId = "", groupId = null)
        docRef.set(template.toFirestoreMap()).await()
        docRef.id
    }

    suspend fun updateRuleTemplate(ruleId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        firestore.collection("ruleTemplates").document(ruleId).update(updates).await()
    }

    suspend fun deleteRuleTemplate(ruleId: String): Result<Unit> = runCatching {
        firestore.collection("ruleTemplates").document(ruleId).delete().await()
    }

    fun observeSchedulingRules(orgId: String, groupId: String): Flow<List<SchedulingRule>> {
        return firestore.collection("organizations/$orgId/schedulingRules")
            .whereIn("groupId", listOf(null, groupId))
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(SchedulingRule::class.java)?.copy(id = it.id)
                }
            }
    }

    suspend fun enableTemplateForRule(orgId: String, ruleTemplate: SchedulingRule): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/schedulingRules").document()
        val newRule = ruleTemplate.copy(
            id = docRef.id,
            orgId = orgId,
            isTemplate = false,
            templateId = ruleTemplate.id,
            isEnabled = true,
            groupId = null,
            createdBy = auth.currentUser?.uid
        )
        docRef.set(newRule.toFirestoreMap()).await()
        docRef.id
    }

    suspend fun addCustomRuleForGroup(orgId: String, groupId: String, rule: SchedulingRule): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/schedulingRules").document()
        val newRule = rule.copy(
            id = docRef.id,
            orgId = orgId,
            groupId = groupId,
            isTemplate = false,
            templateId = null,
            createdBy = auth.currentUser?.uid
        )
        docRef.set(newRule.toFirestoreMap()).await()
        docRef.id
    }

    suspend fun addRuleForOrg(orgId: String, rule: SchedulingRule): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/schedulingRules").document()
        val ruleWithId = rule.copy(id = docRef.id, orgId = orgId)
        docRef.set(ruleWithId.toFirestoreMap()).await()
        docRef.id
    }

    suspend fun updateRuleForOrg(orgId: String, ruleId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/schedulingRules")
            .document(ruleId)
            .update(updates)
            .await()
    }

    suspend fun deleteRuleForOrg(orgId: String, ruleId: String): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/schedulingRules")
            .document(ruleId)
            .delete()
            .await()
    }

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

    fun observeManpowerPlan(orgId: String, groupId: String, month: String): Flow<ManpowerPlan?> {
        val planId = "${orgId}_${groupId}_${month}"
        return firestore.collection("organizations/$orgId/manpowerPlans")
            .document(planId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(ManpowerPlan::class.java)
            }
    }

    suspend fun saveManpowerPlan(orgId: String, plan: ManpowerPlan): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/manpowerPlans")
            .document(plan.id)
            .set(plan.toFirestoreMap())
            .await()
    }

    fun observeAdminStatus(userId: String): Flow<Boolean> {
        return firestore.collection("admins").document(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.exists() && snapshot.getString("role") == "superuser"
            }
    }

    suspend fun createTestData(dataSet: TestDataGenerator.TestDataSet): Result<Unit> = runCatching {
        val orgRef = firestore.collection("organizations").document(dataSet.organization.id)

        firestore.runBatch { batch ->
            batch.set(orgRef, dataSet.organization.toFirestoreMap())

            dataSet.users.forEach { user ->
                val userRef = orgRef.collection("users").document(user.id)
                batch.set(userRef, user.toFirestoreMap())
            }

            dataSet.groups.forEach { group ->
                val groupRef = orgRef.collection("groups").document(group.id)
                batch.set(groupRef, group.toFirestoreMap())
            }

            dataSet.shiftTypes.forEach { shiftType ->
                val shiftTypeRef = orgRef.collection("shiftTypes").document(shiftType.id)
                batch.set(shiftTypeRef, shiftType.toFirestoreMap())
            }

            dataSet.rules.forEach { rule ->
                val ruleRef = orgRef.collection("schedulingRules").document(rule.id)
                batch.set(ruleRef, rule.toFirestoreMap())
            }

            dataSet.schedules.forEach { schedule ->
                val scheduleRef = orgRef.collection("schedules").document(schedule.id)
                batch.set(scheduleRef, schedule.toFirestoreMap())

                val assignmentsForSchedule = dataSet.assignments.filter { it.scheduleId == schedule.id }
                assignmentsForSchedule.forEach { assignment ->
                    val assignmentRef = scheduleRef.collection("assignments").document(assignment.id)
                    batch.set(assignmentRef, assignment.toFirestoreMap())
                }
            }
        }.await()
    }
}