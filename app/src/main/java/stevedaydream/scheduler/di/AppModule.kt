package stevedaydream.scheduler.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import stevedaydream.scheduler.data.local.SchedulerDatabase
import stevedaydream.scheduler.data.remote.FirebaseDataSource
import stevedaydream.scheduler.data.repository.SchedulerRepositoryImpl
import stevedaydream.scheduler.domain.repository.SchedulerRepository
// 引入排班相關類別
import stevedaydream.scheduler.domain.scheduling.*
// 引入具體的規則實作 (Interface)
import stevedaydream.scheduler.domain.scheduling.rules.MaxConsecutiveWorkDaysRule
import stevedaydream.scheduler.domain.scheduling.rules.NightShiftFollowupRule
// Interface import needed here for explicit type in RuleEngine provider
import stevedaydream.scheduler.domain.scheduling.rules.SchedulingRule
// Data class alias remains useful for BacktrackingSolver factory
import stevedaydream.scheduler.data.model.SchedulingRule as SchedulingRuleData
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.data.model.ShiftType
import stevedaydream.scheduler.domain.repository.scheduling.rules.MinRestBetweenShiftsRule
import javax.inject.Qualifier
import javax.inject.Singleton


@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // --- Firebase, Database, DataSource, CoroutineScope, Repository providers ---
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return Firebase.firestore.apply {
            // 注意： setPersistenceEnabled 在較新版本已棄用，
            // 若要禁用離線快取，建議檢查最新 Firebase SDK 文件
            // 通常預設是啟用的，若無特殊需求可移除此設定
            /*
            firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false) // Deprecated
                .build()
            */
        }
    }

    @Provides
    @Singleton
    fun provideSchedulerDatabase(@ApplicationContext context: Context): SchedulerDatabase {
        return Room.databaseBuilder(context, SchedulerDatabase::class.java, "scheduler_database")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideFirebaseDataSource(firestore: FirebaseFirestore, auth: FirebaseAuth): FirebaseDataSource {
        return FirebaseDataSource(firestore, auth)
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob())
    }

    @Provides
    @Singleton
    fun provideSchedulerRepository(
        remoteDataSource: FirebaseDataSource,
        database: SchedulerDatabase,
        auth: FirebaseAuth,
        @ApplicationScope externalScope: CoroutineScope
    ): SchedulerRepository {
        return SchedulerRepositoryImpl(remoteDataSource, database, auth, externalScope)
    }


    // ▼▼▼▼▼▼▼▼▼▼▼▼ Modified Scheduling Providers ▼▼▼▼▼▼▼▼▼▼▼▼

    // REMOVE the provider for the list of interfaces
    /*
    @Provides
    @Singleton
    fun provideAvailableSchedulingRules(): List<SchedulingRule> { // Using interface import
        return listOf(
            MaxConsecutiveWorkDaysRule(),
            MinRestBetweenShiftsRule(),
            NightShiftFollowupRule()
        )
    }
    */

    /**
     * Provide RuleEngine instance directly, instantiating rules inside.
     */
    @Provides
    @Singleton
    fun provideRuleEngine(): RuleEngine { // No longer takes List<Interface> as parameter
        // Explicitly type the list variable with the Interface path
        val availableRules: List<stevedaydream.scheduler.domain.scheduling.rules.SchedulingRule> = listOf(
            MaxConsecutiveWorkDaysRule(),
            MinRestBetweenShiftsRule(),
            NightShiftFollowupRule()
            // Add other rule implementations here...
        )
        return RuleEngine(availableRules)
    }

    /**
     * Provide ScheduleInitializer instance (Depends on RuleEngine)
     */
    @Provides
    @Singleton
    fun provideScheduleInitializer(ruleEngine: RuleEngine): ScheduleInitializer {
        return ScheduleInitializer(ruleEngine)
    }

    /**
     * Provide GreedyScheduleFiller instance (Depends on RuleEngine)
     */
    @Provides
    @Singleton
    fun provideGreedyScheduleFiller(ruleEngine: RuleEngine): GreedyScheduleFiller {
        return GreedyScheduleFiller(ruleEngine)
    }

    /**
     * Provide BacktrackingSolver factory function (Depends on RuleEngine)
     */
    @Provides
    fun provideBacktrackingSolverFactory(ruleEngine: RuleEngine): (List<User>, Int, List<String>, Map<String, Map<String, String>>, Map<String, Map<String, Int>>, List<ShiftType>, List<SchedulingRuleData>, RuleEngine) -> BacktrackingSolver {
        // This provider now correctly depends only on RuleEngine
        return { users, numDays, dates, initialSchedule, initialQuotas, shiftTypes, dbRules, engine ->
            // Pass the 'engine' parameter received during invocation (which should be the same as ruleEngine injected here)
            BacktrackingSolver(users, numDays, dates, initialSchedule, initialQuotas, shiftTypes, dbRules, engine)
        }
    }

    /**
     * Provide ScheduleOptimizer instance (Depends on RuleEngine)
     */
    @Provides
    @Singleton
    fun provideScheduleOptimizer(ruleEngine: RuleEngine): ScheduleOptimizer {
        return ScheduleOptimizer(ruleEngine)
    }

    /**
     * Provide ScheduleGenerator instance (Depends on Initializer, Filler, Factory, Optimizer, RuleEngine)
     */
    @Provides
    @Singleton
    fun provideScheduleGenerator(
        initializer: ScheduleInitializer,
        greedyFiller: GreedyScheduleFiller,
        // Factory function type signature must match what provideBacktrackingSolverFactory returns
        backtrackingSolverFactory: (List<User>, Int, List<String>, Map<String, Map<String, String>>, Map<String, Map<String, Int>>, List<ShiftType>, List<SchedulingRuleData>, RuleEngine) -> BacktrackingSolver,
        optimizer: ScheduleOptimizer,
        ruleEngine: RuleEngine
    ): ScheduleGenerator {
        return ScheduleGenerator(initializer, greedyFiller, backtrackingSolverFactory, optimizer, ruleEngine)
    }

    /**
     * Provide RotationScheduler instance (No dependencies needed here)
     */
    @Provides
    @Singleton
    fun provideRotationScheduler(): RotationScheduler {
        return RotationScheduler()
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲ Modified Scheduling Providers ▲▲▲▲▲▲▲▲▲▲▲▲
}