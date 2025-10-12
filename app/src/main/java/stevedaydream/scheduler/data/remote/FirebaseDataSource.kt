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
import kotlinx.coroutines.tasks.await // 確保有這個 import
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
                parameters = mapOf("maxDays" to "6") // 預設連續上班不超過 6 天
            ),
            SchedulingRule(
                ruleName = "輪班間隔需大於N小時",
                description = "確保員工在兩次輪班之間有足夠的休息時間。",
                ruleType = "hard",
                penaltyScore = -1000,
                isEnabled = true,
                parameters = mapOf("minHours" to "11") // 預設輪班間隔需大於 11 小時
            )
            // 未來可以在這裡新增更多預設規則
        )
    }



    // ==================== 組織 ====================
    suspend fun createOrganizationAndFirstUser(org: Organization, user: User): Result<String> = runCatching {
        val orgRef = firestore.collection("organizations").document()
        val topLevelUserRef = firestore.collection("users").document(user.id)
        val orgUserRef = firestore.collection("organizations/${orgRef.id}/users").document(user.id)

        val orgWithId = org.copy(id = orgRef.id)
        val userWithOrgId = user.copy(orgId = orgRef.id)

        firestore.runBatch { batch ->
            batch.set(orgRef, orgWithId.toFirestoreMap())

            // ✅ 使用 merge 保留既有資料
            batch.set(orgUserRef, userWithOrgId.toFirestoreMap(), com.google.firebase.firestore.SetOptions.merge())
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
    /**
     * 生成唯一的8位組織代碼
     */
    suspend fun generateUniqueOrgCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // 排除易混淆字元
        var code: String
        var isUnique = false

        do {
            code = (1..8).map { chars.random() }.joinToString("")
            // 檢查是否已存在
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
        val docRef = firestore.collection("organizations/$orgId/users").document(user.id)
        val topLevelUserRef = firestore.collection("users").document(user.id)
        val userWithId = user.copy(orgId = orgId)

        firestore.runBatch { batch ->
            batch.set(docRef, userWithId.toFirestoreMap())
            // ✅ 3. 同樣在頂層 users 集合中建立
            batch.set(topLevelUserRef, userWithId.toFirestoreMap())
        }.await()
        docRef.id
    }
    /**
     * 創建組織邀請碼
     */
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
                    it.toObject(User::class.java)?.copy(id = it.id, orgId = orgId)
                }
            }
    }
    /**
     * 監聽組織的邀請碼
     */
    fun observeOrganizationInvites(orgId: String): Flow<List<OrganizationInvite>> {
        return firestore.collection("organizations/$orgId/invites")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(OrganizationInvite::class.java)?.copy(id = it.id)
                }
            }
    }

    /**
     * 根據邀請碼查詢組織
     */
    suspend fun getOrganizationByInviteCode(inviteCode: String): Result<Organization?> = runCatching {
        // 使用 collectionGroup 查詢所有組織的邀請碼
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

        // 檢查邀請是否有效
        if (!invite.isValid()) {
            return@runCatching null
        }

        // 取得組織資訊
        val orgSnapshot = firestore.collection("organizations")
            .document(invite.orgId)
            .get()
            .await()

        orgSnapshot.toObject(Organization::class.java)?.copy(id = orgSnapshot.id)
    }

    /**
     * 驗證並使用邀請碼
     */
    suspend fun validateAndUseInviteCode(inviteCode: String): Result<OrganizationInvite> = runCatching {
        // 1. 在 Transaction 外先執行查詢，找到目標文件的引用
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

        // 2. 在 Transaction 內執行原子的「讀取-修改-寫入」操作
        firestore.runTransaction { transaction ->
            val inviteDoc = transaction.get(inviteDocRef)
            val invite = inviteDoc.toObject(OrganizationInvite::class.java)
                ?: throw IllegalArgumentException("無法解析邀請碼")

            if (!invite.isValid()) {
                throw IllegalArgumentException("邀請碼已過期或已達使用上限")
            }

            // 更新使用次數
            transaction.update(inviteDoc.reference, "usedCount", invite.usedCount + 1)

            // 將 invite 物件作為 transaction 的結果返回
            invite
        }.await()
    }
    // ==================== 組織加入申請 ====================

    /**
     * 創建組織加入申請
     */
    suspend fun createOrganizationJoinRequest(
        request: OrganizationJoinRequest
    ): Result<String> = runCatching {
        val docRef = firestore.collection("organizationJoinRequests").document()
        val requestWithId = request.copy(id = docRef.id)
        docRef.set(requestWithId.toFirestoreMap()).await()
        docRef.id
    }
    /**
     * 監聽組織的加入申請
     */
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

    /**
     * 監聽用戶的加入申請
     */
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

    /**
     * 停用邀請碼
     */
    suspend fun deactivateInvite(orgId: String, inviteId: String): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/invites")
            .document(inviteId)
            .update("isActive", false)
            .await()
    }
    /**
     * 審核加入申請
     */
    suspend fun processJoinRequest(
        orgId: String,
        requestId: String,
        approve: Boolean,
        processedBy: String,
        targetGroupId: String? = null
    ): Result<Unit> = runCatching {
        // 1. 在 Batch 操作前，先讀取所有需要的資料
        val requestRef = firestore.collection("organizationJoinRequests").document(requestId)
        val requestSnapshot = requestRef.get().await()
        val request = requestSnapshot.toObject(OrganizationJoinRequest::class.java)
            ?: throw IllegalArgumentException("找不到申請記錄")

        // 2. 執行 Batch 寫入操作
        firestore.runBatch { batch ->
            // 更新申請狀態
            batch.update(requestRef, mapOf(
                "status" to if (approve) "approved" else "rejected",
                "processedBy" to processedBy,
                "processedAt" to com.google.firebase.Timestamp.now()
            ))

            if (approve) {
                // 更新用戶的 orgId
                val userRef = firestore.collection("users").document(request.userId)
                batch.update(userRef, "orgId", orgId)

                // 如果有指定群組,將用戶加入群組 (這裡改為直接更新，因為 Batch 中無法讀取)
                if (targetGroupId != null) {
                    val groupRef = firestore.collection("organizations/$orgId/groups")
                        .document(targetGroupId)
                    // 注意：在 Batch 中，我們使用 FieldValue.arrayUnion 來原子性地添加元素，避免讀取舊列表
                    batch.update(groupRef, "memberIds", com.google.firebase.firestore.FieldValue.arrayUnion(request.userId))
                }
            }
        }.await()
    }
    /**
     * 根據組織代碼查詢組織
     */
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

    // ✅ 4. 優化 `checkUserExists`
    suspend fun checkUserExists(userId: String): Boolean {
        // 直接查詢頂層 users 集合，更快速且不需要特殊索引
        val userDoc = firestore.collection("users").document(userId).get().await()
        return userDoc.exists()
    }

    // ✅ 5. 優化 `updateUser`
    // ✅ 修正: 簡化邏輯,不使用 collectionGroup
    suspend fun updateUser(userId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        val topLevelUserRef = firestore.collection("users").document(userId)

        // 先取得頂層用戶資料,從中獲取 orgId
        val userSnapshot = topLevelUserRef.get().await()
        val orgId = userSnapshot.getString("orgId")

        firestore.runBatch { batch ->
            // 更新頂層 users 集合
            batch.set(topLevelUserRef, updates, com.google.firebase.firestore.SetOptions.merge())

            // 如果有 orgId,同時更新組織內的子集合
            if (!orgId.isNullOrEmpty()) {
                val orgUserRef = firestore.collection("organizations/$orgId/users").document(userId)
                batch.set(orgUserRef, updates, com.google.firebase.firestore.SetOptions.merge())
            }
        }.await()
    }
    /**
     * 從頂層 users 集合監聽單一用戶
     */
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
    // ==================== 組別加入申請 ====================
    suspend fun createGroupJoinRequest(orgId: String, request: GroupJoinRequest): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/groupJoinRequests").document()
        val requestWithId = request.copy(id = docRef.id)
        docRef.set(requestWithId.toFirestoreMap()).await()
        docRef.id
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

    // ==================== 班別類型 (Shift Types) ====================

    // 🔽🔽🔽 新增以下所有方法 🔽🔽🔽

    /**
     * 監聽班別範本 (未來加值功能)
     */
    fun observeShiftTypeTemplates(): Flow<List<ShiftType>> {
        return firestore.collection("shiftTypeTemplates")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(ShiftType::class.java)?.copy(id = it.id)
                }
            }
    }

    /**
     * 監聽一個組織的班別，包含組織層級 + 特定群組層級
     */
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

    /**
     * 為群組新增自訂班別
     */
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

    /**
     * 更新組織內的班別
     */
    suspend fun updateShiftType(orgId: String, shiftTypeId: String, updates: Map<String, Any>): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/shiftTypes")
            .document(shiftTypeId)
            .update(updates)
            .await()
    }

    /**
     * 刪除組織內的班別
     */
    suspend fun deleteShiftType(orgId: String, shiftTypeId: String): Result<Unit> = runCatching {
        firestore.collection("organizations/$orgId/shiftTypes")
            .document(shiftTypeId)
            .delete()
            .await()
    }
    // 🔼🔼🔼 到此為止 🔼🔼🔼


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
    // ==================== 排班規則 (Rule Templates for Superuser) ====================

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
        // 確保 isTemplate 標記為 true
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


    // ==================== 排班規則 (Organization & Group Rules) ====================

    /**
     * 監聽一個組織內的所有規則，包含組織層級 + 特定群組層級
     */
    fun observeSchedulingRules(orgId: String, groupId: String): Flow<List<SchedulingRule>> {
        return firestore.collection("organizations/$orgId/schedulingRules")
            // 查詢條件: (groupId == null) OR (groupId == currentGroupId)
            .whereIn("groupId", listOf(null, groupId))
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull {
                    it.toObject(SchedulingRule::class.java)?.copy(id = it.id)
                }
            }
    }
    /**
     * Org Admin 啟用一個範本規則到組織中 (複製)
     */
    suspend fun enableTemplateForRule(orgId: String, ruleTemplate: SchedulingRule): Result<String> = runCatching {
        val docRef = firestore.collection("organizations/$orgId/schedulingRules").document()
        val newRule = ruleTemplate.copy(
            id = docRef.id,
            orgId = orgId,
            isTemplate = false, // 這是範本的實例，不是範本本身
            templateId = ruleTemplate.id, // 記錄來源
            isEnabled = true,
            groupId = null, // 組織層級規則
            createdBy = auth.currentUser?.uid
        )
        docRef.set(newRule.toFirestoreMap()).await()
        docRef.id
    }

    /**
     * 排班者為群組新增自訂規則
     */
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

    // ✅ 新增以下三個方法
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
    // ==================== 人力規劃 ====================
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

    // ==================== 管理員 ====================
    fun observeAdminStatus(userId: String): Flow<Boolean> {
        return firestore.collection("admins").document(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.exists() && snapshot.getString("role") == "superuser"
            }
    }
    // ==================== 超級管理員 ====================
    suspend fun createTestData(dataSet: TestDataGenerator.TestDataSet): Result<Unit> = runCatching {
        val orgRef = firestore.collection("organizations").document(dataSet.organization.id)

        firestore.runBatch { batch ->
            // 1. 寫入組織
            batch.set(orgRef, dataSet.organization.toFirestoreMap())

            // 2. 寫入使用者
            dataSet.users.forEach { user ->
                val userRef = orgRef.collection("users").document(user.id)
                batch.set(userRef, user.toFirestoreMap())
            }

            // 3. 寫入群組
            dataSet.groups.forEach { group ->
                val groupRef = orgRef.collection("groups").document(group.id)
                batch.set(groupRef, group.toFirestoreMap())
            }

            // 4. 寫入班別類型
            dataSet.shiftTypes.forEach { shiftType ->
                val shiftTypeRef = orgRef.collection("shiftTypes").document(shiftType.id)
                batch.set(shiftTypeRef, shiftType.toFirestoreMap())
            }

            // 5. 寫入排班規則
            dataSet.rules.forEach { rule ->
                val ruleRef = orgRef.collection("schedulingRules").document(rule.id)
                batch.set(ruleRef, rule.toFirestoreMap())
            }

            // 6. 寫入班表和班表分配
            dataSet.schedules.forEach { schedule ->
                val scheduleRef = orgRef.collection("schedules").document(schedule.id)
                batch.set(scheduleRef, schedule.toFirestoreMap())

                // 找到屬於這個班表的分配
                val assignmentsForSchedule = dataSet.assignments.filter { it.scheduleId == schedule.id }
                assignmentsForSchedule.forEach { assignment ->
                    val assignmentRef = scheduleRef.collection("assignments").document(assignment.id)
                    batch.set(assignmentRef, assignment.toFirestoreMap())
                }
            }
        }.await()
    }
}