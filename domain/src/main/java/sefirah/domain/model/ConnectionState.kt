package sefirah.domain.model

sealed class ConnectionState {
    data object Connected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Disconnected(val forcedDisconnect: Boolean = false) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}