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
import javax.inject.Qualifier
import javax.inject.Singleton
import stevedaydream.scheduler.domain.scheduling.ScheduleGenerator

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return Firebase.firestore.apply {
            firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false) // 關閉 Firestore 本地快取,因為我們使用 Room
                .build()
        }
    }

    @Provides
    @Singleton
    fun provideSchedulerDatabase(
        @ApplicationContext context: Context
    ): SchedulerDatabase {
        // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改开始 ▼▼▼▼▼▼▼▼▼▼▼▼
        return Room.databaseBuilder(
            context,
            SchedulerDatabase::class.java,
            "scheduler_database"
        )
            .fallbackToDestructiveMigration() // ✅ 允許在遷移失敗時破壞性地重建資料庫
            .build()
        // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改结束 ▲▲▲▲▲▲▲▲▲▲▲▲
    }

    @Provides
    @Singleton
    fun provideFirebaseDataSource(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): FirebaseDataSource {
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
        auth: FirebaseAuth, // <-- 新增 FirebaseAuth
        @ApplicationScope externalScope: CoroutineScope
    ): SchedulerRepository {
        // <-- 將 auth 傳入建構子
        return SchedulerRepositoryImpl(remoteDataSource, database, auth, externalScope)
    }
    @Provides
    @Singleton
    fun provideScheduleGenerator(): ScheduleGenerator {
        return ScheduleGenerator()
    }
}