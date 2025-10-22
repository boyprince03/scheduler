// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import stevedaydream.scheduler.domain.scheduling.RuleEngine
import javax.inject.Singleton

// 明確引入 SchedulingRule Interface
import stevedaydream.scheduler.domain.scheduling.rules.SchedulingRule
// 引入所有規則的實作
import stevedaydream.scheduler.domain.scheduling.rules.MaxConsecutiveWorkDaysRule
import stevedaydream.scheduler.domain.scheduling.rules.NightShiftFollowupRule
import stevedaydream.scheduler.domain.repository.scheduling.rules.MinRestBetweenShiftsRule


@Module
@InstallIn(SingletonComponent::class)
object RuleProviders {

    // --- 新增：明確提供規則實作 ---
    @Provides
    @Singleton
    fun provideMaxConsecutiveWorkDaysRule(): MaxConsecutiveWorkDaysRule {
        return MaxConsecutiveWorkDaysRule()
    }

    @Provides
    @Singleton
    fun provideMinRestBetweenShiftsRule(): MinRestBetweenShiftsRule {
        return MinRestBetweenShiftsRule()
    }

    @Provides
    @Singleton
    fun provideNightShiftFollowupRule(): NightShiftFollowupRule {
        return NightShiftFollowupRule()
    }
    // --- 新增結束 ---


    @Provides
    @Singleton
    fun provideRuleEngine(
        // 使用 @JvmSuppressWildcards 避免 Dagger 型別問題
        rules: Set<@JvmSuppressWildcards SchedulingRule>
    ): RuleEngine {
        // RuleEngine 的建構子接收 List，所以轉換一下
        return RuleEngine(rules.toList())
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲