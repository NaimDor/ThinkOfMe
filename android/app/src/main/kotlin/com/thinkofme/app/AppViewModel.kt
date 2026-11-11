package com.thinkofme.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.thinkofme.core.data.AuthRepository
import com.thinkofme.core.data.ProfileRepository
import com.thinkofme.core.data.di.UsesFirebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

sealed interface AppGate {
    data object Loading : AppGate
    data object SignedOut : AppGate
    data object NeedsProfile : AppGate
    data object Ready : AppGate
}

@HiltViewModel
class AppViewModel @Inject constructor(
    auth: AuthRepository,
    private val profiles: ProfileRepository,
    @UsesFirebase private val usesFirebase: Boolean,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val gate: StateFlow<AppGate> = auth.session
        .flatMapLatest { session ->
            if (session == null) flowOf(AppGate.SignedOut)
            else profiles.me().map { me -> if (me == null) AppGate.NeedsProfile else AppGate.Ready }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppGate.Loading)

    /** Tell the backend our time zone, language and push token so reminders arrive at 10:00 local time, in our language. */
    fun registerDevice() {
        viewModelScope.launch {
            val token = if (usesFirebase) runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull() else null
            runCatching { profiles.saveDeviceSettings(TimeZone.getDefault().id, Locale.getDefault().toLanguageTag(), token) }
        }
    }
}
