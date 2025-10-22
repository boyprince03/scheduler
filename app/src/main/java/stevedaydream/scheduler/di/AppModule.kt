// scheduler/di/AppModule.kt
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
// ✅ Use Kotlin's class reference
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import stevedaydream.scheduler.data.local.SchedulerDatabase
import stevedaydream.scheduler.data.remote.FirebaseDataSource
import stevedaydream.scheduler.data.repository.SchedulerRepositoryImpl
import stevedaydream.scheduler.domain.repository.SchedulerRepository
// Import the implementation class
import stevedaydream.scheduler.domain.scheduling.BacktrackingSolverFactoryImpl
import javax.inject.Qualifier
import javax.inject.Singleton


@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
// ▼▼▼▼▼▼▼▼▼▼▼▼ 修正 ▼▼▼▼▼▼▼▼▼▼▼▼
@InstallIn(SingletonComponent::class) // ✅ Changed .class to ::class
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修正 ▲▲▲▲▲▲▲▲▲▲▲▲
object AppModule {

    // --- Firebase, Database, DataSource, CoroutineScope, Repository providers ---
    // (These remain unchanged)
    @Provides @Singleton fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth
    @Provides @Singleton fun provideFirebaseFirestore(): FirebaseFirestore {
        return Firebase.firestore /* ... */
    }
    @Provides @Singleton fun provideSchedulerDatabase(@ApplicationContext context: Context): SchedulerDatabase {
        return Room.databaseBuilder(context, SchedulerDatabase::class.java, "scheduler_database")
            .fallbackToDestructiveMigration()
            .build()
    }
    @Provides @Singleton fun provideFirebaseDataSource(firestore: FirebaseFirestore, auth: FirebaseAuth): FirebaseDataSource {
        return FirebaseDataSource(firestore, auth)
    }
    @Provides @Singleton @ApplicationScope fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob())
    }
    @Provides @Singleton fun provideSchedulerRepository(
        remoteDataSource: FirebaseDataSource,
        database: SchedulerDatabase,
        auth: FirebaseAuth,
        @ApplicationScope externalScope: CoroutineScope
    ): SchedulerRepository {
        return SchedulerRepositoryImpl(remoteDataSource, database, auth, externalScope)
    }

    // --- Explicitly provide BacktrackingSolverFactoryImpl ---
    @Provides
    @Singleton
    fun provideBacktrackingSolverFactoryImpl(): BacktrackingSolverFactoryImpl {
        // Assuming BacktrackingSolverFactoryImpl has @Inject constructor or no-arg constructor
        return BacktrackingSolverFactoryImpl()
    }


    // --- Scheduling related providers are handled in other modules ---
}