// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
// 明確引入 SchedulingRule Interface
import stevedaydream.scheduler.domain.scheduling.rules.SchedulingRule
// 引入所有規則的實作
import stevedaydream.scheduler.domain.scheduling.rules.MaxConsecutiveWorkDaysRule
import stevedaydream.scheduler.domain.scheduling.rules.NightShiftFollowupRule
import stevedaydream.scheduler.domain.repository.scheduling.rules.MinRestBetweenShiftsRule
// 移除 @Singleton import
// import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RuleBindings {

    @Binds @IntoSet
    // @Singleton // <-- 移除 Singleton
    abstract fun bindMaxConsecutiveWorkDaysRule(
        impl: MaxConsecutiveWorkDaysRule
    ): SchedulingRule // 綁定到 Interface

    @Binds @IntoSet
    // @Singleton // <-- 移除 Singleton
    abstract fun bindMinRestBetweenShiftsRule(
        impl: MinRestBetweenShiftsRule
    ): SchedulingRule // 綁定到 Interface

    @Binds @IntoSet
    // @Singleton // <-- 移除 Singleton
    abstract fun bindNightShiftFollowupRule(
        impl: NightShiftFollowupRule
    ): SchedulingRule // 綁定到 Interface

    // --- 如果有其他規則實作，繼續在這裡加入 ---
    // @Binds @IntoSet
    // abstract fun bindAnotherRule(impl: AnotherRuleImpl): SchedulingRule

}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲