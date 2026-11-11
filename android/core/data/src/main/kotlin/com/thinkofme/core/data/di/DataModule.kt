package com.thinkofme.core.data.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.thinkofme.core.data.AuthRepository
import com.thinkofme.core.data.CircleRepository
import com.thinkofme.core.data.ClaimRepository
import com.thinkofme.core.data.OccasionRepository
import com.thinkofme.core.data.ProfileRepository
import com.thinkofme.core.data.Today
import com.thinkofme.core.data.WishRepository
import com.thinkofme.core.data.demo.DemoAuthRepository
import com.thinkofme.core.data.demo.DemoCircleRepository
import com.thinkofme.core.data.demo.DemoClaimRepository
import com.thinkofme.core.data.demo.DemoOccasionRepository
import com.thinkofme.core.data.demo.DemoProfileRepository
import com.thinkofme.core.data.demo.DemoWishRepository
import com.thinkofme.core.data.firebase.FirebaseAuthRepository
import com.thinkofme.core.data.firebase.FirebaseCircleRepository
import com.thinkofme.core.data.firebase.FirebaseClaimRepository
import com.thinkofme.core.data.firebase.FirebaseOccasionRepository
import com.thinkofme.core.data.firebase.FirebaseProfileRepository
import com.thinkofme.core.data.firebase.FirebaseWishRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UsesFirebase

/**
 * Picks the Firebase implementations when the app was built with a Firebase
 * config (google-services.json), and the in-memory demo world otherwise.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    @UsesFirebase
    fun usesFirebase(@ApplicationContext context: Context): Boolean =
        FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null

    @Provides
    @Singleton
    fun today(): Today = Today { LocalDate.now() }

    @Provides
    @Singleton
    fun auth(@UsesFirebase firebase: Boolean, real: Provider<FirebaseAuthRepository>, demo: Provider<DemoAuthRepository>): AuthRepository =
        if (firebase) real.get() else demo.get()

    @Provides
    @Singleton
    fun profiles(@UsesFirebase firebase: Boolean, real: Provider<FirebaseProfileRepository>, demo: Provider<DemoProfileRepository>): ProfileRepository =
        if (firebase) real.get() else demo.get()

    @Provides
    @Singleton
    fun circles(@UsesFirebase firebase: Boolean, real: Provider<FirebaseCircleRepository>, demo: Provider<DemoCircleRepository>): CircleRepository =
        if (firebase) real.get() else demo.get()

    @Provides
    @Singleton
    fun wishes(@UsesFirebase firebase: Boolean, real: Provider<FirebaseWishRepository>, demo: Provider<DemoWishRepository>): WishRepository =
        if (firebase) real.get() else demo.get()

    @Provides
    @Singleton
    fun claims(@UsesFirebase firebase: Boolean, real: Provider<FirebaseClaimRepository>, demo: Provider<DemoClaimRepository>): ClaimRepository =
        if (firebase) real.get() else demo.get()

    @Provides
    @Singleton
    fun occasions(@UsesFirebase firebase: Boolean, real: Provider<FirebaseOccasionRepository>, demo: Provider<DemoOccasionRepository>): OccasionRepository =
        if (firebase) real.get() else demo.get()
}
