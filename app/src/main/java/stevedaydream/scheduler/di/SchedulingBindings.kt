// scheduler/di/SchedulingBindings.kt (No changes needed from the previous correct version)
package stevedaydream.scheduler.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import stevedaydream.scheduler.domain.scheduling.BacktrackingSolverFactory
import stevedaydream.scheduler.domain.scheduling.BacktrackingSolverFactoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SchedulingBindings {

    @Binds
    @Singleton
    abstract fun bindBacktrackingSolverFactory(
        impl: BacktrackingSolverFactoryImpl // Hilt will use the @Provides from AppModule
    ): BacktrackingSolverFactory
}