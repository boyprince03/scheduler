package stevedaydream.scheduler.domain.scheduling

// ✅ 1. 確保你 import 的是 data class，而不是 interface
import stevedaydream.scheduler.data.model.SchedulingRule

/**
 * 提供一組預設的「醫院-專科護理師」排班規則模板
 *
 * @return 一個包含三條硬性規則的列表
 */
fun getHospitalNursePractitionerRules(): List<SchedulingRule> {
    // ✅ 2. 這裡建立的是 stevedaydream.scheduler.data.model.SchedulingRule 的實例
    return listOf(
        SchedulingRule(
            id = "template-hnp-consecutive-work-6", // 建議 id 加上 template 前綴以茲區別
            ruleName = "連續上班不超過N天",
            description = "每人當月不可連續上班超過6天。",
            ruleType = "hard", // 硬性規則
            penaltyScore = -1000,
            isEnabled = true,
            isTemplate = true, // 標示為範本
            parameters = mapOf("maxDays" to "6")
        ),
        SchedulingRule(
            id = "template-hnp-night-shift-followup",
            ruleName = "夜班後續班別限制",
            description = "值班(夜)後面只能是值班(夜)或OFF。",
            ruleType = "hard",
            penaltyScore = -1000,
            isEnabled = true,
            isTemplate = true
        ),
        SchedulingRule(
            id = "template-hnp-min-rest-12h",
            ruleName = "輪班間隔需大於N小時",
            description = "每個班別中間至少要間隔12小時。",
            ruleType = "hard",
            penaltyScore = -1000,
            isEnabled = true,
            isTemplate = true,
            parameters = mapOf("minHours" to "12")
        )
    )
}

// 未來可以新增更多不同情境的規則模板...
// fun getFactoryOperatorRules(): List<SchedulingRule> { ... }