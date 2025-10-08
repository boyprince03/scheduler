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
        val userRef = firestore.collection("organizations/${orgRef.id}/users").document(user.id)

        val orgWithId = org.copy(id = orgRef.id)
        val userWithOrgId = user.copy(orgId = orgRef.id)

        firestore.runBatch { batch ->
            // 1. 建立組織
            batch.set(orgRef, orgWithId.toFirestoreMap())
            // 2. 建立第一個使用者 (管理員)
            batch.set(userRef, userWithOrgId.toFirestoreMap())


            // 3. 建立預設的排班規則
            val rulesCollection = firestore.collection("organizations/${orgRef.id}/schedulingRules")
            getDefaultSchedulingRules().forEach { rule ->
                val ruleRef = rulesCollection.document()
                // 我們不需要 id，因為 Firestore 會自動產生
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
}