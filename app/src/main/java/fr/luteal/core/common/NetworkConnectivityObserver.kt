package fr.luteal.core.common

import kotlinx.coroutines.flow.Flow

interface NetworkConnectivityObserver {
    val isConnected: Flow<Boolean>
}
