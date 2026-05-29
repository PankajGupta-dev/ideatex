package com.alertnet.app.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertnet.app.db.DatabaseProvider
import com.alertnet.app.db.PeerQueries
import com.alertnet.app.model.MeshPeer
import com.alertnet.app.repository.SettingsRepository
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * ViewModel for MeshMapScreen.
 *
 * Provides reactive peer location data and self-location for the offline map.
 * Uses FusedLocationProviderClient directly — no service binding required.
 * Manages the first-visit location consent dialog flow.
 */
class MeshMapViewModel(
    private val settingsRepository: SettingsRepository,
    private val appContext: Context
) : ViewModel() {

    /** Peers with known GPS coordinates — polled every 5s */
    val peersWithLocation: StateFlow<List<MeshPeer>> = flow {
        while (true) {
            try {
                val peers = PeerQueries.getPeersWithLocation(DatabaseProvider.db)
                emit(peers)
            } catch (_: Exception) {
                emit(emptyList())
            }
            delay(5000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selfLocation = MutableStateFlow<Location?>(null)
    /** User's own GPS position (local only, never transmitted by this flow) */
    val selfLocation: StateFlow<Location?> = _selfLocation.asStateFlow()

    private val _showConsentDialog = MutableStateFlow(false)
    /** True on first-ever visit to MeshMapScreen — drives the consent dialog */
    val showConsentDialog: StateFlow<Boolean> = _showConsentDialog.asStateFlow()

    private val cancellationTokenSource = CancellationTokenSource()

    init {
        checkConsentState()
        if (settingsRepository.isLocalMapLocationEnabled) {
            fetchSelfLocation()
        }
    }

    private fun checkConsentState() {
        viewModelScope.launch {
            if (!settingsRepository.hasShownLocationConsent) {
                _showConsentDialog.value = true
            }
        }
    }

    fun acceptLocationConsent() {
        viewModelScope.launch {
            settingsRepository.setMeshLocationBroadcast(true)
            settingsRepository.setHasShownLocationConsent(true)
            _showConsentDialog.value = false
            fetchSelfLocation()
        }
    }

    fun declineLocationConsent() {
        viewModelScope.launch {
            settingsRepository.setHasShownLocationConsent(true)  // don't show again
            _showConsentDialog.value = false
            // isMeshLocationBroadcastEnabled remains false
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchSelfLocation() {
        val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(TimeUnit.MINUTES.toMillis(2))
            .setDurationMillis(TimeUnit.SECONDS.toMillis(10))
            .build()

        fusedClient.getCurrentLocation(request, cancellationTokenSource.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    _selfLocation.value = location
                } else {
                    // Fallback to last known
                    fusedClient.lastLocation.addOnSuccessListener { last ->
                        _selfLocation.value = last
                    }
                }
            }
            .addOnFailureListener {
                fusedClient.lastLocation.addOnSuccessListener { last ->
                    _selfLocation.value = last
                }
            }
    }

    override fun onCleared() {
        cancellationTokenSource.cancel()
        super.onCleared()
    }
}
